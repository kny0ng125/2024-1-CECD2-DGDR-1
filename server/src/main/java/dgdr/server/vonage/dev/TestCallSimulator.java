package dgdr.server.vonage.dev;

import dgdr.server.vonage.Call;
import dgdr.server.vonage.CallRepository;
import dgdr.server.vonage.CallService;
import dgdr.server.vonage.clova.CallTranscriptCache;
import dgdr.server.vonage.dev.TestCallDtos.FakeCallStatus;
import dgdr.server.vonage.dev.TestCallScenarios.Line;
import dgdr.server.vonage.dev.TestCallScenarios.Scenario;
import dgdr.server.vonage.user.domain.User;
import dgdr.server.vonage.user.infra.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "dev.fake-call.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TestCallSimulator {

    private final CallRepository callRepository;
    private final UserRepository userRepository;
    private final CallService callService;
    private final CallTranscriptCache transcriptCache;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "fake-call-sim");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<Long, RunningSim> running = new ConcurrentHashMap<>();

    private record RunningSim(Long callId,
                              String userId,
                              String scenarioId,
                              LocalDateTime startedAt,
                              Future<?> future) {}

    public FakeCallStatus start(String userId, String scenarioId, double speed) {
        if (speed <= 0) speed = 1.0;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        boolean hasActive = running.values().stream()
                .anyMatch(s -> userId.equals(s.userId()));
        if (hasActive) {
            throw new IllegalStateException("Active fake call already exists for user " + userId);
        }

        Scenario scenario = TestCallScenarios.byId(scenarioId);

        Call call = Call.builder()
                .user(user)
                .startTime(LocalDateTime.now())
                .build();
        callRepository.save(call);
        Long callId = call.getId();

        LocalDateTime startedAt = LocalDateTime.now();
        double finalSpeed = speed;
        Future<?> future = executor.submit(() -> runScenario(callId, scenario, finalSpeed));

        running.put(callId, new RunningSim(callId, userId, scenario.id(), startedAt, future));
        log.info("[FakeCall] started callId={} user={} scenario={} speed={}",
                callId, userId, scenario.id(), speed);

        return new FakeCallStatus(callId, scenario.id(), startedAt);
    }

    public void stop(Long callId) {
        RunningSim sim = running.get(callId);
        if (sim == null) return;
        sim.future().cancel(true);
        log.info("[FakeCall] stop requested callId={}", callId);
    }

    public List<FakeCallStatus> active() {
        return running.values().stream()
                .map(s -> new FakeCallStatus(s.callId(), s.scenarioId(), s.startedAt()))
                .collect(Collectors.toList());
    }

    private void runScenario(Long callId, Scenario sc, double speed) {
        try {
            for (Line line : sc.lines()) {
                Thread.sleep(scaled(line.preDelayMs(), speed));
                emitLine(callId, sc, line, speed);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.info("[FakeCall] scenario interrupted callId={}", callId);
        } catch (Exception e) {
            log.error("[FakeCall] scenario error callId={}: {}", callId, e.getMessage(), e);
        } finally {
            try {
                callService.finalizeCall(callId);
            } catch (Exception e) {
                log.error("[FakeCall] finalizeCall error callId={}: {}", callId, e.getMessage(), e);
            }
            running.remove(callId);
            log.info("[FakeCall] finished callId={}", callId);
        }
    }

    private void emitLine(Long callId, Scenario sc, Line line, double speed) throws InterruptedException {
        String phone = "agent".equals(line.speaker()) ? sc.agentPhone() : sc.callerPhone();

        String[] words = line.text().split("\\s+");
        int total = words.length;
        int splits = Math.min(total, 3); // 최대 2개의 중간 partial + 1개의 final

        for (int i = 1; i < splits; i++) {
            int upto = Math.max(1, (total * i) / splits);
            String partial = Arrays.stream(words).limit(upto).collect(Collectors.joining(" "));
            transcriptCache.append(callId, line.speaker(), phone, partial, false);
            Thread.sleep(scaled(sc.partialDelayMs(), speed));
        }

        transcriptCache.append(callId, line.speaker(), phone, line.text(), true);
    }

    private static long scaled(long ms, double speed) {
        long v = (long) (ms / speed);
        return Math.max(0, v);
    }

    @PreDestroy
    public void shutdown() {
        running.values().forEach(s -> s.future().cancel(true));
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
