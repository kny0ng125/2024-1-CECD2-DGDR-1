package dgdr.server.telephony.core;

import dgdr.server.call.CallSessionService;
import dgdr.server.transcript.CallTranscriptCache;
import dgdr.server.transcript.TranscriptEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 통화 소스에 무관한 코어 동작 검증.
 *
 * <p>CLOVA 자격증명도, Asterisk 도, 네트워크도, Spring 컨텍스트도 없이 돈다.
 * {@code TranscriptionEngine} 과 {@code CallControl} 을 인터페이스로 둔 값이
 * 여기서 회수된다.
 */
class DefaultCallOrchestratorTest {

    private static final String AGENT_ID = "agent-01";
    private static final String AGENT_PHONE = "+821000001111";
    private static final String CALLER_PHONE = "+821000002222";

    private CallSessionService callSessionService;
    private CallTranscriptCache transcriptCache;
    private RecordingTranscriptionEngine engine;
    private DefaultCallOrchestrator orchestrator;

    private final AtomicLong callIdSeq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        callSessionService = mock(CallSessionService.class);
        when(callSessionService.beginCall(anyString(), any(), any()))
                .thenAnswer(inv -> callIdSeq.incrementAndGet());

        transcriptCache = new CallTranscriptCache();
        engine = new RecordingTranscriptionEngine();
        orchestrator = new DefaultCallOrchestrator(callSessionService, transcriptCache, engine);
    }

    // ── 헬퍼 ────────────────────────────────────────────────

    private CallDescriptor callDesc(String key) {
        return new CallDescriptor(key, AGENT_ID, AGENT_PHONE, CALLER_PHONE);
    }

    private LegDescriptor leg(String key, LegRole role) {
        return leg(key, role, AudioFormat.PCM16_16K_MONO);
    }

    private LegDescriptor leg(String key, LegRole role, AudioFormat format) {
        return callDesc(key).leg(role, format);
    }

    /** 제어 없는 통화 = 즉시 통화 중으로 시작. */
    private LegHandle attachLive(String key, LegRole role, MediaSink sink) {
        return orchestrator.attachLeg(leg(key, role), sink, CallControl.UNSUPPORTED);
    }

    private static byte[] pcm(int bytes) {
        byte[] b = new byte[bytes];
        for (int i = 0; i < bytes; i++) b[i] = (byte) (i % 127);
        return b;
    }

    // ================================================================
    @Nested
    @DisplayName("통화 식별 / callId 발급")
    class CallIdentity {

        @Test
        @DisplayName("같은 providerCallKey 의 leg 들은 한 통화로 묶이고 callId 는 한 번만 발급된다")
        void legsShareOneCall() {
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            assertThat(agent.callId()).isEqualTo(caller.callId());
            verify(callSessionService, times(1)).beginCall(eq(AGENT_ID), any(), any());
        }

        @Test
        @DisplayName("providerCallKey 가 다르면 별개의 통화가 열린다")
        void differentKeysOpenDifferentCalls() {
            LegHandle a = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());
            LegHandle b = attachLive("conv-2", LegRole.CALLER, new CapturingMediaSink());

            assertThat(a.callId()).isNotEqualTo(b.callId());
            verify(callSessionService, times(2)).beginCall(eq(AGENT_ID), any(), any());
        }

        @Test
        @DisplayName("담당 요원 없이는 통화를 열 수 없다")
        void agentIsRequired() {
            assertThatThrownBy(() -> new CallDescriptor("conv-x", null, AGENT_PHONE, CALLER_PHONE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("같은 통화의 두 leg 가 동시에 붙어도 Call 은 하나만 생성된다")
        void concurrentAttachCreatesSingleCall() throws Exception {
            int legCount = 8;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(legCount);
            ExecutorService pool = Executors.newFixedThreadPool(legCount);

            for (int i = 0; i < legCount; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        attachLive("conv-race", LegRole.CALLER, new CapturingMediaSink());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            verify(callSessionService, times(1)).beginCall(eq(AGENT_ID), any(), any());
        }
    }

    // ================================================================
    @Nested
    @DisplayName("오디오 브리지")
    class Bridging {

        @Test
        @DisplayName("한 leg 의 오디오는 같은 통화의 다른 leg 로만 가고 자기 자신에게는 오지 않는다")
        void relaysToPeersOnly() {
            CapturingMediaSink agentSink = new CapturingMediaSink();
            CapturingMediaSink callerSink = new CapturingMediaSink();

            LegHandle agent = attachLive("conv-1", LegRole.AGENT, agentSink);
            attachLive("conv-1", LegRole.CALLER, callerSink);

            agent.writeAudio(pcm(320));

            assertThat(callerSink.frameCount()).isEqualTo(1);
            assertThat(agentSink.frameCount()).isZero();
        }

        /**
         * 이 테스트가 이 리팩터링의 핵심 회귀 방지선이다.
         *
         * <p>예전 구현은 "열려 있는 모든 WebSocket 세션"을 순회하며 릴레이했다.
         * 통화가 둘 이상 동시에 진행되면 서로 다른 신고의 음성이 섞였고,
         * 119 상황실에서 이는 치명적인 결함이다.
         */
        @Test
        @DisplayName("동시에 진행 중인 다른 통화로는 오디오가 절대 새지 않는다")
        void neverLeaksAcrossCalls() {
            CapturingMediaSink call1Peer = new CapturingMediaSink();
            CapturingMediaSink call2Peer = new CapturingMediaSink();

            LegHandle call1Agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            attachLive("conv-1", LegRole.CALLER, call1Peer);

            attachLive("conv-2", LegRole.AGENT, new CapturingMediaSink());
            attachLive("conv-2", LegRole.CALLER, call2Peer);

            call1Agent.writeAudio(pcm(320));

            assertThat(call1Peer.frameCount()).isEqualTo(1);
            assertThat(call2Peer.frameCount()).as("다른 통화로 새면 안 된다").isZero();
        }

        @Test
        @DisplayName("닫힌 sink 는 브리지 대상에서 제외된다")
        void skipsClosedSinks() {
            CapturingMediaSink callerSink = new CapturingMediaSink();
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            attachLive("conv-1", LegRole.CALLER, callerSink);

            callerSink.closeSink();
            agent.writeAudio(pcm(320));

            assertThat(callerSink.frameCount()).isZero();
        }

        @Test
        @DisplayName("포맷이 다른 leg 로 릴레이할 때 샘플레이트를 변환한다")
        void resamplesBetweenLegs() {
            CapturingMediaSink wideband = new CapturingMediaSink();

            LegHandle narrow = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER, AudioFormat.PCM16_8K_MONO),
                    new CapturingMediaSink(), CallControl.UNSUPPORTED);
            orchestrator.attachLeg(
                    leg("conv-1", LegRole.AGENT, AudioFormat.PCM16_16K_MONO),
                    wideband, CallControl.UNSUPPORTED);

            narrow.writeAudio(pcm(320));   // 8kHz 160 샘플

            // 8k → 16k 이므로 대략 두 배가 되어야 한다.
            assertThat(wideband.totalBytes())
                    .isCloseTo(640, org.assertj.core.data.Offset.offset(4));
        }
    }

    // ================================================================
    @Nested
    @DisplayName("전사 / 화자 태깅")
    class Transcription {

        @Test
        @DisplayName("leg 마다 STT 스트림이 따로 열린다 (화자 분리의 전제)")
        void oneSttStreamPerLeg() {
            attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            assertThat(engine.streams()).hasSize(2);
        }

        @Test
        @DisplayName("STT 결과에 leg 역할이 화자로 태깅되어 캐시에 쌓인다")
        void tagsSpeakerFromLegRole() {
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            engine.stream(0).emit("네, 119 상황실입니다", true);   // agent leg
            engine.stream(1).emit("불이 났어요", true);            // caller leg

            List<TranscriptEntry> entries = transcriptCache.snapshot(agent.callId());
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).speaker()).isEqualTo("agent");
            assertThat(entries.get(0).speakerPhone()).isEqualTo(AGENT_PHONE);
            assertThat(entries.get(1).speaker()).isEqualTo("caller");
            assertThat(entries.get(1).speakerPhone()).isEqualTo(CALLER_PHONE);
        }

        /**
         * 시나리오 재생기가 특권을 잃었다는 증거.
         * 오디오 경로(STT)와 텍스트 경로(writeTranscript)가 같은 결과를 만든다.
         */
        @Test
        @DisplayName("writeTranscript 는 STT 결과와 완전히 같은 경로를 탄다")
        void textPathMatchesAudioPath() {
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            engine.stream(0).emit("오디오 경로", true);
            caller.writeTranscript("텍스트 경로", true);

            List<TranscriptEntry> entries = transcriptCache.snapshot(agent.callId());
            assertThat(entries).hasSize(2);
            assertThat(entries).allSatisfy(e -> assertThat(e.isFinal()).isTrue());
            assertThat(entries.get(0).speaker()).isEqualTo("agent");
            assertThat(entries.get(1).speaker()).isEqualTo("caller");
        }

        @Test
        @DisplayName("partial 과 final 이 구분되어 쌓인다")
        void distinguishesPartialAndFinal() {
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            caller.writeTranscript("불이", false);
            caller.writeTranscript("불이 났어요", true);

            assertThat(transcriptCache.snapshot(caller.callId())).hasSize(2);
            assertThat(transcriptCache.finalEntries(caller.callId())).hasSize(1);
        }

        @Test
        @DisplayName("빈 전사는 무시된다")
        void ignoresBlankText() {
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());
            caller.writeTranscript("   ", true);
            caller.writeTranscript(null, true);

            assertThat(transcriptCache.snapshot(caller.callId())).isEmpty();
        }
    }

    // ================================================================
    @Nested
    @DisplayName("호 제어 — 수락 / 종료")
    class Control {

        @Test
        @DisplayName("제어 가능한 소스는 벨 울림(OFFERED) 상태로 시작한다")
        void controllableSourceStartsOffered() {
            LegHandle caller = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), new RecordingCallControl());

            assertThat(orchestrator.stateOf(caller.callId())).isEqualTo(CallState.OFFERED);
        }

        @Test
        @DisplayName("제어할 수 없는 소스는 곧바로 통화 중으로 시작한다")
        void uncontrollableSourceStartsAnswered() {
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            assertThat(orchestrator.stateOf(caller.callId())).isEqualTo(CallState.ANSWERED);
        }

        /**
         * OFFERED 구간에서 STT 를 열면 링백 톤과 무음을 인식하게 되고,
         * 통화가 성립하지도 않은 구간에 대해 인식 과금이 발생한다.
         */
        @Test
        @DisplayName("수락 전에는 STT 스트림이 열리지 않는다")
        void noSttBeforeAnswer() {
            orchestrator.attachLeg(leg("conv-1", LegRole.CALLER),
                    new CapturingMediaSink(), new RecordingCallControl());

            assertThat(engine.streams()).isEmpty();
        }

        @Test
        @DisplayName("수락하면 게이트웨이에 응답을 지시하고 STT 를 연다")
        void answerOpensStt() {
            RecordingCallControl control = new RecordingCallControl();
            LegHandle caller = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), control);

            boolean changed = orchestrator.answerCall(caller.callId());

            assertThat(changed).isTrue();
            assertThat(control.answerCount()).isEqualTo(1);
            assertThat(engine.streams()).hasSize(1);
            assertThat(orchestrator.stateOf(caller.callId())).isEqualTo(CallState.ANSWERED);
            verify(callSessionService).answerCall(caller.callId());
        }

        @Test
        @DisplayName("수락 전 발화는 버려진다 (링백 구간)")
        void dropsTranscriptBeforeAnswer() {
            LegHandle caller = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), new RecordingCallControl());

            caller.writeTranscript("수락 전 발화", true);
            assertThat(transcriptCache.snapshot(caller.callId())).isEmpty();

            orchestrator.answerCall(caller.callId());
            caller.writeTranscript("수락 후 발화", true);
            assertThat(transcriptCache.snapshot(caller.callId())).hasSize(1);
        }

        @Test
        @DisplayName("수락을 연타해도 게이트웨이는 한 번만 호출된다")
        void answerIsIdempotent() {
            RecordingCallControl control = new RecordingCallControl();
            LegHandle caller = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), control);

            assertThat(orchestrator.answerCall(caller.callId())).isTrue();
            assertThat(orchestrator.answerCall(caller.callId())).isFalse();
            assertThat(orchestrator.answerCall(caller.callId())).isFalse();

            assertThat(control.answerCount()).isEqualTo(1);
            assertThat(engine.streams()).hasSize(1);
        }

        @Test
        @DisplayName("동시에 수락해도 게이트웨이는 한 번만 호출된다")
        void concurrentAnswerCallsGatewayOnce() throws Exception {
            RecordingCallControl control = new RecordingCallControl();
            LegHandle caller = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), control);
            Long callId = caller.callId();

            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger succeeded = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (orchestrator.answerCall(callId)) succeeded.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            assertThat(succeeded.get()).isEqualTo(1);
            assertThat(control.answerCount()).isEqualTo(1);
            assertThat(engine.streams()).hasSize(1);
        }

        /**
         * 되돌리지 않으면 화면은 통화 중인데 발신자에게는 계속 벨이 울린다.
         */
        @Test
        @DisplayName("게이트웨이 응답이 실패하면 상태를 되돌린다")
        void revertsStateWhenGatewayFails() {
            LegHandle caller = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), RecordingCallControl.failing());
            Long callId = caller.callId();

            assertThatThrownBy(() -> orchestrator.answerCall(callId))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(orchestrator.stateOf(callId)).isEqualTo(CallState.OFFERED);
            assertThat(engine.streams()).isEmpty();
            verify(callSessionService, never()).answerCall(any());
        }

        @Test
        @DisplayName("종료는 코어가 직접 마감하지 않고 게이트웨이에 지시한다")
        void hangupDelegatesToGateway() {
            RecordingCallControl control = new RecordingCallControl();
            LegHandle caller = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), control);

            orchestrator.hangupCall(caller.callId(), CallControl.HangupCause.REJECTED);

            assertThat(control.hangups()).containsExactly(CallControl.HangupCause.REJECTED);
            // 게이트웨이가 실제로 끊기 전이므로 아직 마감되지 않는다.
            verify(callSessionService, never()).endCall(any());
        }

        @Test
        @DisplayName("진행 중이 아닌 통화 제어는 IllegalStateException")
        void controlOnUnknownCallFails() {
            assertThatThrownBy(() -> orchestrator.answerCall(999L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ================================================================
    @Nested
    @DisplayName("통화 종료")
    class Termination {

        @Test
        @DisplayName("leg 가 남아 있는 동안에는 통화를 끝내지 않는다")
        void doesNotEndWhileLegsRemain() {
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            agent.close();

            verify(callSessionService, never()).endCall(any());
        }

        @Test
        @DisplayName("마지막 leg 가 닫히면 통화를 종료한다")
        void endsWhenLastLegCloses() {
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());
            Long callId = agent.callId();

            agent.close();
            caller.close();

            verify(callSessionService, times(1)).endCall(callId);
        }

        @Test
        @DisplayName("leg 를 여러 번 닫아도 종료는 한 번만 일어난다")
        void closeIsIdempotent() {
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());
            Long callId = caller.callId();

            caller.close();
            caller.close();
            caller.close();

            verify(callSessionService, times(1)).endCall(callId);
        }

        /**
         * 상대가 끊으면 양쪽 소켓이 거의 동시에 닫힌다. 두 스레드가 함께
         * "남은 leg 0" 을 보면 종료 이벤트가 두 번 나가고 프런트가 통화를 두 번 닫는다.
         */
        @Test
        @DisplayName("두 leg 가 동시에 닫혀도 종료는 한 번만 일어난다")
        void concurrentCloseEndsOnce() throws Exception {
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());
            Long callId = agent.callId();

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            for (LegHandle leg : List.of(agent, caller)) {
                pool.submit(() -> {
                    try {
                        start.await();
                        leg.close();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            verify(callSessionService, times(1)).endCall(callId);
        }

        @Test
        @DisplayName("leg 를 닫으면 그 leg 의 STT 스트림도 닫힌다")
        void closesSttStream() {
            LegHandle caller = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());
            caller.close();

            assertThat(engine.stream(0).isClosed()).isTrue();
        }

        @Test
        @DisplayName("닫힌 leg 에 들어온 오디오·전사는 무시된다")
        void ignoresWritesAfterClose() {
            CapturingMediaSink peer = new CapturingMediaSink();
            LegHandle agent = attachLive("conv-1", LegRole.AGENT, new CapturingMediaSink());
            attachLive("conv-1", LegRole.CALLER, peer);
            Long callId = agent.callId();

            agent.close();
            agent.writeAudio(pcm(320));
            agent.writeTranscript("닫힌 뒤 발화", true);

            assertThat(peer.frameCount()).isZero();
            assertThat(transcriptCache.snapshot(callId)).isEmpty();
        }

        @Test
        @DisplayName("통화가 끝난 뒤 같은 키로 다시 붙으면 새 통화가 열린다")
        void reusingKeyAfterEndOpensNewCall() {
            LegHandle first = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());
            first.close();

            LegHandle second = attachLive("conv-1", LegRole.CALLER, new CapturingMediaSink());

            assertThat(second.callId()).isNotEqualTo(first.callId());
            verify(callSessionService, times(2)).beginCall(eq(AGENT_ID), any(), any());
        }

        /**
         * 벨만 울리다 끝난 통화는 미디어가 붙은 적이 없어 leg 종료로는 마감이
         * 트리거되지 않는다. 그래도 기록에는 남아야 한다 —
         * 놓친 신고가 사라지면 안 된다.
         */
        @Test
        @DisplayName("leg 없이 벨만 울리다 끝난 통화도 마감된다")
        void closesCallThatNeverGotALeg() {
            Long callId = orchestrator.openCall(callDesc("conv-1"), new RecordingCallControl());

            orchestrator.closeCall(callId);

            verify(callSessionService, times(1)).endCall(callId);
            assertThat(orchestrator.stateOf(callId)).isNull();
        }

        @Test
        @DisplayName("closeCall 은 멱등하다")
        void closeCallIsIdempotent() {
            Long callId = orchestrator.openCall(callDesc("conv-1"), new RecordingCallControl());

            orchestrator.closeCall(callId);
            orchestrator.closeCall(callId);
            orchestrator.closeCall(callId);

            verify(callSessionService, times(1)).endCall(callId);
        }

        @Test
        @DisplayName("openCall 로 연 통화에 나중에 leg 가 합류한다")
        void legJoinsPreviouslyOpenedCall() {
            RecordingCallControl control = new RecordingCallControl();
            Long callId = orchestrator.openCall(callDesc("conv-1"), control);

            LegHandle joined = orchestrator.attachLeg(
                    leg("conv-1", LegRole.CALLER), new CapturingMediaSink(), control);

            assertThat(joined.callId()).isEqualTo(callId);
            verify(callSessionService, times(1)).beginCall(eq(AGENT_ID), any(), any());
        }

        @Test
        @DisplayName("수락 후 합류한 leg 는 즉시 전사를 시작한다")
        void lateLegStartsTranscriptionImmediately() {
            RecordingCallControl control = new RecordingCallControl();
            Long callId = orchestrator.openCall(callDesc("conv-1"), control);
            orchestrator.answerCall(callId);

            assertThat(engine.streams()).isEmpty();   // 아직 leg 가 없다

            orchestrator.attachLeg(leg("conv-1", LegRole.CALLER),
                    new CapturingMediaSink(), control);

            assertThat(engine.streams()).hasSize(1);
        }
    }

    // ================================================================
    @Nested
    @DisplayName("STT 장애 격리")
    class SttFailureIsolation {

        /**
         * 신고 접수 중 인식 서버 장애로 <b>통화가 끊기는</b> 것은
         * 인식 결과가 안 나오는 것보다 훨씬 나쁘다.
         */
        @Test
        @DisplayName("STT 스트림을 못 열어도 통화(오디오 브리지)는 계속된다")
        void bridgeSurvivesSttOpenFailure() {
            DefaultCallOrchestrator failing = new DefaultCallOrchestrator(
                    callSessionService, transcriptCache, new ExplodingEngine());

            CapturingMediaSink peer = new CapturingMediaSink();
            LegHandle agent = failing.attachLeg(
                    leg("conv-1", LegRole.AGENT), new CapturingMediaSink(), CallControl.UNSUPPORTED);
            failing.attachLeg(
                    leg("conv-1", LegRole.CALLER), peer, CallControl.UNSUPPORTED);

            agent.writeAudio(pcm(320));

            assertThat(peer.frameCount()).isEqualTo(1);
        }
    }

    /** 스트림 개설이 항상 실패하는 엔진. */
    private static final class ExplodingEngine implements dgdr.server.stt.TranscriptionEngine {

        @Override public String name() { return "exploding"; }
        @Override public AudioFormat requiredFormat() { return AudioFormat.PCM16_16K_MONO; }
        @Override public boolean supports(AudioFormat format) { return true; }

        @Override
        public Stream open(AudioFormat format,
                           java.util.function.Consumer<dgdr.server.stt.SttResult> onResult,
                           java.util.function.Consumer<Throwable> onError) {
            throw new IllegalStateException("stt unavailable");
        }
    }
}
