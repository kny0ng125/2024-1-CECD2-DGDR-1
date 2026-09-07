package dgdr.server.telephony.scenario;

import dgdr.server.telephony.core.CallControl;
import dgdr.server.telephony.core.CallDescriptor;
import dgdr.server.telephony.core.CallOrchestrator;
import dgdr.server.telephony.core.LegHandle;
import dgdr.server.telephony.core.LegRole;
import dgdr.server.telephony.core.MediaSink;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 대본을 재생하는 통화 소스. 전화망 없이 전 구간을 재현한다.
 *
 * <h2>다른 통화 소스와 동등한 시민이 된 이유</h2>
 * <p>예전 구현({@code TranscriptFakeDriver})은 {@code CallTranscriptCache} 를
 * 직접 호출했다. 파이프라인을 통째로 우회했다는 뜻이고, 그래서
 * <b>가짜에서는 되는데 실제 통화에서는 안 되는</b> 부류의 버그를 잡아낼 수
 * 없었다. 실제로 그런 버그가 있었다 — 실제 통화 경로는 {@code call_started}
 * 를 push 하지 않아 프런트가 callId 를 받지 못했는데, 데모는 이 재생기로만
 * 돌렸기 때문에 아무도 눈치채지 못했다.
 *
 * <p>지금은 {@link CallOrchestrator#attachLeg} 로 leg 두 개를 붙이고
 * {@link LegHandle#writeTranscript} 로 발화를 밀어 넣는다. callId 발급,
 * 화자 태깅, SSE push, 통화 종료, DB flush 전부 실제 통화와 <b>같은 코드</b>를 탄다.
 *
 * <p>{@link CallControl} 까지 구현하므로 <b>벨 울림 → 수락/거절 흐름도
 * 전화 없이 테스트</b>된다. 수락 UI 를 검증하려고 Asterisk 를 띄울 필요가 없다.
 *
 * <p>운영 노출 차단은 이중이다: {@code dev} 프로파일 + {@code dev.fake-call.enabled}.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "dev.fake-call.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ScenarioCallSource {

    private final CallOrchestrator orchestrator;

    /** 재생을 백그라운드로 돌려 start() 가 즉시 리턴하게 한다. */
    private final ExecutorService playbackPool =
            Executors.newFixedThreadPool(4, daemon("scenario-playback"));

    /** callId → 진행 중 세션. stop/active 용. */
    private final ConcurrentMap<Long, Session> sessions = new ConcurrentHashMap<>();

    /** 재생 상태 (REST 응답용). */
    public record PlaybackStatus(Long callId, String scenarioId, String state,
                                 LocalDateTime startedAt) {}

    // ── 제어 ────────────────────────────────────────────────

    /**
     * 시나리오 통화를 시작한다.
     *
     * @param ring {@code true} 면 벨 울림(OFFERED) 상태로 시작해 요원의 수락을
     *             기다린다. {@code false} 면 곧바로 통화 중으로 시작해 바로 재생한다.
     */
    public PlaybackStatus start(String agentUserId, String scenarioId, double speed, boolean ring) {
        double sp = speed <= 0 ? 1.0 : speed;

        if (sessions.values().stream().anyMatch(s -> agentUserId.equals(s.agentUserId))) {
            throw new IllegalStateException("Active scenario call already exists for " + agentUserId);
        }

        Scenarios.Scenario sc = Scenarios.byId(scenarioId);

        // 통화 소스가 부여하는 통화 키. Vonage 의 conversation-uuid,
        // AudioSocket 의 UUID 와 같은 자리에 놓인다.
        String callKey = "scenario-" + UUID.randomUUID();
        Session session = new Session(agentUserId, sc, sp, ring);

        CallDescriptor callDesc = new CallDescriptor(
                callKey, agentUserId, sc.agentPhone(), sc.callerPhone());

        // leg 두 개를 붙인다. 실제 통화와 동일하게 요원/신고자 각각 하나씩.
        // 오디오를 받을 수 없으므로 sink 는 NULL.
        LegHandle agentLeg = orchestrator.attachLeg(
                callDesc.textOnlyLeg(LegRole.AGENT), MediaSink.NULL, session.control);
        LegHandle callerLeg = orchestrator.attachLeg(
                callDesc.textOnlyLeg(LegRole.CALLER), MediaSink.NULL, session.control);

        session.bind(agentLeg, callerLeg);
        Long callId = agentLeg.callId();
        sessions.put(callId, session);

        // 재생 스레드는 지금 뜨지만, 수락 래치가 풀릴 때까지 아무것도 방출하지 않는다.
        // 수락 시점에 스레드를 새로 만들면 첫 발화가 상태 전이보다 앞서 도착할 수
        // 있어(그 발화는 코어가 버린다) 경합이 생긴다.
        session.future = playbackPool.submit(() -> play(callId, session));

        // 벨을 울리지 않는 모드면 코어가 이미 ANSWERED 로 열었으므로 즉시 재생.
        if (!ring) session.answered.countDown();

        log.info("[scenario] started callId={} agent={} scenario={} speed={} ring={}",
                callId, agentUserId, sc.id(), sp, ring);
        return status(callId, session);
    }

    public void stop(Long callId) {
        Session s = sessions.get(callId);
        if (s != null) s.control.hangup(CallControl.HangupCause.NORMAL);
    }

    public List<PlaybackStatus> active() {
        return sessions.entrySet().stream()
                .map(e -> status(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private PlaybackStatus status(Long callId, Session s) {
        String state = (s.answered.getCount() == 0) ? "ANSWERED" : "OFFERED";
        return new PlaybackStatus(callId, s.scenario.id(), state, s.startedAt);
    }

    // ── 세션 ────────────────────────────────────────────────

    /** 시나리오 통화 하나의 상태 + 그 통화의 {@link CallControl} 구현. */
    private final class Session {

        private final String agentUserId;
        private final Scenarios.Scenario scenario;
        private final double speed;
        private final LocalDateTime startedAt = LocalDateTime.now();

        /** 수락 전까지 재생 스레드를 붙잡아 두는 래치. */
        private final CountDownLatch answered = new CountDownLatch(1);

        private final boolean ring;
        private volatile LegHandle agentLeg;
        private volatile LegHandle callerLeg;
        private volatile Future<?> future;

        private final CallControl control = new CallControl() {
            @Override
            public void answer() {
                answered.countDown();   // 멱등: 이미 0이면 아무 일도 없다
            }

            @Override
            public void hangup(HangupCause cause) {
                log.info("[scenario] hangup cause={}", cause);
                answered.countDown();   // 대기 중이면 깨워서 finally 로 보낸다
                Future<?> f = future;
                if (f != null) f.cancel(true);
            }

            @Override
            public boolean supportsAnswer() {
                return ring;
            }
        };

        private Session(String agentUserId, Scenarios.Scenario scenario, double speed, boolean ring) {
            this.agentUserId = agentUserId;
            this.scenario = scenario;
            this.speed = speed;
            this.ring = ring;
        }

        private void bind(LegHandle agentLeg, LegHandle callerLeg) {
            this.agentLeg = agentLeg;
            this.callerLeg = callerLeg;
        }
    }

    // ── 재생 ────────────────────────────────────────────────

    /**
     * 모든 발화를 <b>절대 시각</b>에 예약한다. 순차로 sleep 하며 흘리면
     * 겹치는 발화를 재현할 수 없다 — 동시 발화는 이 시스템이 반드시 다뤄야 하는
     * 실제 상황이므로 재생기가 그걸 만들어낼 수 있어야 한다.
     */
    private void play(Long callId, Session session) {
        // 풀 크기를 발화 수에 맞춘다. 예전에는 2로 고정돼 있었는데, emit() 이
        // partial 사이에 sleep 하며 스레드를 점유하기 때문에 겹치는 발화가
        // 3개 이상이면 뒤엣것이 밀려 예약 시각을 못 지켰다. 그러면 동시 발화
        // 시나리오가 조용히 순차 재생으로 바뀌어, 정작 검증하려던 상황이
        // 재현되지 않는다.
        int poolSize = Math.max(2, Math.min(session.scenario.lines().size(), 8));
        ScheduledExecutorService emitter =
                Executors.newScheduledThreadPool(poolSize, daemon("scenario-emit"));
        boolean answered = false;
        try {
            // 수락(또는 거절)을 기다린다. 벨만 울리다 끊기는 경우도 정상 경로다.
            session.answered.await();
            answered = true;

            for (Scenarios.Line line : session.scenario.lines()) {
                LegHandle leg = "agent".equals(line.speaker()) ? session.agentLeg : session.callerLeg;
                emitter.schedule(() -> emit(leg, line, session.speed),
                        scaled(line.startMs(), session.speed), TimeUnit.MILLISECONDS);
            }
            // 예약만 하면 이 메서드가 즉시 끝나버린다. 마지막 발화까지 대기.
            Thread.sleep(scaled(session.scenario.totalDurationMs(), session.speed) + 500);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();   // hangup 으로 취소된 정상 경로
        } catch (RuntimeException e) {
            log.error("[scenario] playback error callId={}", callId, e);
        } finally {
            emitter.shutdownNow();
            sessions.remove(callId);
            // leg 를 닫으면 코어가 통화 종료(finalize + call_ended)까지 처리한다.
            // 재생기가 종료 절차를 직접 알 필요가 없다는 점이 핵심이다.
            safeClose(session.callerLeg);
            safeClose(session.agentLeg);
            log.info("[scenario] finished callId={} (answered={})", callId, answered);
        }
    }

    /**
     * 한 발화를 partial 여러 개 + final 하나로 쪼개 방출한다.
     * 실제 STT 가 부분 결과를 흘리다가 확정하는 동작을 흉내 낸다.
     */
    private void emit(LegHandle leg, Scenarios.Line line, double speed) {
        try {
            String[] words = line.text().split("\\s+");
            int splits = Math.min(words.length, 3);          // partial 최대 2 + final 1
            long step = scaled(line.durationMs() / Math.max(1, splits), speed);

            for (int i = 1; i < splits; i++) {
                int upto = Math.max(1, (words.length * i) / splits);
                String partial = Arrays.stream(words).limit(upto).collect(Collectors.joining(" "));
                leg.writeTranscript(partial, false);
                Thread.sleep(step);
            }
            leg.writeTranscript(line.text(), true);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("[scenario] emit failed: {}", e.toString());
        }
    }

    private static void safeClose(LegHandle leg) {
        if (leg == null) return;
        try { leg.close(); }
        catch (RuntimeException e) { log.warn("[scenario] leg close failed: {}", e.toString()); }
    }

    private static long scaled(long ms, double speed) {
        return Math.max(0, (long) (ms / speed));
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(s -> {
            s.answered.countDown();
            Future<?> f = s.future;
            if (f != null) f.cancel(true);
        });
        playbackPool.shutdownNow();
    }
}
