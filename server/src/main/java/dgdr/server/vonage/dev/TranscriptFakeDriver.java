package dgdr.server.vonage.dev;

import dgdr.server.vonage.call.CallSessionService;      // ← 실제 위치에 맞게
import dgdr.server.vonage.transcript.CallTranscriptCache;    // ← 실제 위치에 맞게
import dgdr.server.vonage.dev.FakeCallDtos.FakeCallStatus;
import dgdr.server.vonage.dev.FakeCallScenarios.Line;
import dgdr.server.vonage.dev.FakeCallScenarios.Scenario;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component("transcript")   // ← mode 키
@Profile("dev")
@ConditionalOnProperty(name = "dev.fake-call.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TranscriptFakeDriver implements FakeCallDriver {

    private final CallSessionService callSessionService;
    private final CallTranscriptCache transcriptCache;

    /** 시나리오 재생을 백그라운드로 돌려서 start()가 바로 리턴하게 함 */
    private final ExecutorService executor =
            Executors.newFixedThreadPool(4, daemon("fake-call-runner"));

    /** 진행 중 통화 추적 (stop/active 용) */
    private final ConcurrentMap<Long, RunningSim> running = new ConcurrentHashMap<>();
    private record RunningSim(Long callId, String userId, String scenarioId,
                              LocalDateTime startedAt, Future<?> future) {}

    @Override
    public FakeCallStatus start(String userId, String scenarioId, double speed) {
        if (speed <= 0) speed = 1.0;
        if (running.values().stream().anyMatch(s -> userId.equals(s.userId())))
            throw new IllegalStateException("Active fake call already exists for user " + userId);

        Scenario sc = FakeCallScenarios.byId(scenarioId);

        // Call 생성 + agent 채널로 call_started push → callId
        Long callId = callSessionService.beginCall(userId, sc.callerPhone());

        LocalDateTime startedAt = LocalDateTime.now();
        double sp = speed;
        // 재생은 백그라운드에서 (start는 즉시 리턴)
        Future<?> future = executor.submit(() -> runScenario(callId, sc, sp));
        running.put(callId, new RunningSim(callId, userId, sc.id(), startedAt, future));
        log.info("[FakeCall/transcript] started callId={} user={} scenario={}", callId, userId, sc.id());
        return new FakeCallStatus(callId, sc.id(), startedAt);
    }

    @Override
    public void stop(Long callId) {
        RunningSim sim = running.get(callId);
        if (sim != null) sim.future().cancel(true);   // runScenario 스레드 인터럽트
    }

    @Override
    public List<FakeCallStatus> active() {
        return running.values().stream()
                .map(s -> new FakeCallStatus(s.callId(), s.scenarioId(), s.startedAt()))
                .collect(Collectors.toList());
    }

    /** 모든 발화를 "절대 시각"에 예약 → 겹치는 발화는 동시에 방출됨 */
    private void runScenario(Long callId, Scenario sc, double speed) {
        // 발화 예약/동시실행용 풀 (스레드 2개 → 겹친 발화 병렬)
        ScheduledExecutorService sched =
                Executors.newScheduledThreadPool(2, daemon("fake-emit"));
        long maxEndMs = 0;
        try {
            for (Line line : sc.lines()) {
                // line.startMs 후에 emitLine 실행하도록 예약
                sched.schedule(() -> emitLine(callId, sc, line, speed),
                        scaled(line.startMs(), speed), TimeUnit.MILLISECONDS);
                maxEndMs = Math.max(maxEndMs, line.startMs() + line.durationMs());
            }
            // 예약만 하면 함수가 바로 끝남 → 모든 발화 끝날 때까지 이 스레드는 대기
            Thread.sleep(scaled(maxEndMs, speed) + 500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();       // stop()으로 취소됨
        } catch (Exception e) {
            log.error("[FakeCall/transcript] error callId={}: {}", callId, e.getMessage(), e);
        } finally {
            sched.shutdownNow();                       // 남은 예약 취소
            try { callSessionService.endCall(callId); } // finalize + call_ended
            catch (Exception e) { log.error("endCall error callId={}", callId, e); }
            running.remove(callId);
            log.info("[FakeCall/transcript] finished callId={}", callId);
        }
    }

    /** 한 발화를 partial 2개 + final로 방출 (durationMs 구간에 걸쳐) */
    private void emitLine(Long callId, Scenario sc, Line line, double speed) {
        try {
            String phone = "agent".equals(line.speaker()) ? sc.agentPhone() : sc.callerPhone();
            String[] words = line.text().split("\\s+");
            int total = words.length;
            int splits = Math.min(total, 3);                       // 최대 partial 2 + final 1
            long step = scaled(line.durationMs() / Math.max(1, splits), speed);
            for (int i = 1; i < splits; i++) {
                int upto = Math.max(1, (total * i) / splits);
                String partial = Arrays.stream(words).limit(upto).collect(Collectors.joining(" "));
                transcriptCache.append(callId, line.speaker(), phone, partial, false);  // partial
                Thread.sleep(step);
            }
            transcriptCache.append(callId, line.speaker(), phone, line.text(), true);   // final
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static long scaled(long ms, double speed) {
        return Math.max(0, (long) (ms / speed));
    }

    private static ThreadFactory daemon(String name) {
        return r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; };
    }

    @PreDestroy
    public void shutdown() {
        running.values().forEach(s -> s.future().cancel(true));
        executor.shutdownNow();
    }
}