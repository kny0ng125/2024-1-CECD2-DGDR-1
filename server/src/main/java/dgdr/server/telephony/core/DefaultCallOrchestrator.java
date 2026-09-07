package dgdr.server.telephony.core;

import dgdr.server.call.CallSessionService;
import dgdr.server.stt.TranscriptionEngine;
import dgdr.server.transcript.CallTranscriptCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link CallOrchestrator} 의 유일한 구현. 통화 도메인의 심장.
 *
 * <h2>책임</h2>
 * <ol>
 *   <li>{@code providerCallKey} 로 leg 를 통화 단위로 묶고 callId 를 발급한다.</li>
 *   <li>통화 상태(OFFERED → ANSWERED → ENDED)를 관리한다.</li>
 *   <li>수락 시점에 leg 당 STT 스트림을 열고, 결과에 화자를 태깅해 캐시에 넣는다.</li>
 *   <li>같은 통화 안에서만 오디오를 릴레이한다.</li>
 *   <li>마지막 leg 가 닫히면 통화 종료 절차를 <b>한 번만</b> 실행한다.</li>
 * </ol>
 *
 * <h2>통화 소스가 갖지 못하는 것</h2>
 * <p>{@code Call} 엔티티 생성, 통화 이벤트 push, 캐시 직접 쓰기,
 * 통화 종료 판단. 전부 여기 있다.
 *
 * <h2>인덱스가 둘인 이유</h2>
 * <p>통화 소스는 자기 프로토콜 키({@code providerCallKey})로 찾아오고,
 * 요원 화면은 도메인 식별자({@code callId})로 찾아온다. 브라우저에 SIP
 * Call-ID 를 알려 줄 이유가 없으므로 두 경로를 각각 열어 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCallOrchestrator implements CallOrchestrator {

    private final CallSessionService callSessionService;
    private final CallTranscriptCache transcriptCache;
    private final TranscriptionEngine transcriptionEngine;

    /** providerCallKey → 진행 중 통화 (통화 소스용 조회 경로). */
    private final ConcurrentMap<String, LiveCall> callsByKey = new ConcurrentHashMap<>();

    /** callId → 진행 중 통화 (요원 화면용 조회 경로). */
    private final ConcurrentMap<Long, LiveCall> callsById = new ConcurrentHashMap<>();

    // ================================================================
    // 통화 개설
    // ================================================================

    @Override
    public Long openCall(CallDescriptor desc, CallControl control) {
        CallControl effective = (control == null) ? CallControl.UNSUPPORTED : control;
        LiveCall call = obtain(desc, effective);
        return call.callId();
    }

    @Override
    public LegHandle attachLeg(LegDescriptor desc, MediaSink sink, CallControl control) {
        MediaSink effectiveSink = (sink == null) ? MediaSink.NULL : sink;
        CallControl effectiveControl = (control == null) ? CallControl.UNSUPPORTED : control;

        LiveCall call = obtain(desc.call(), effectiveControl);

        OrchestratedLeg leg = new OrchestratedLeg(
                call, desc, effectiveSink,
                this::openStt, transcriptionEngine.requiredFormat(),
                this::publish, this::detach);

        call.add(leg);

        // 이미 통화 중인 통화에 뒤늦게 붙은 leg 는 즉시 전사를 시작해야 한다.
        // (수락 후 미디어가 붙는 SIP 경로, 또는 요원 leg 가 늦게 붙는 경우)
        if (call.state() == CallState.ANSWERED) {
            leg.startTranscription();
        }

        log.info("[call {}] leg attached: role={} format={} state={}",
                call.callId(), desc.role(), desc.format(), call.state());
        return leg;
    }

    /**
     * providerCallKey 에 해당하는 통화를 찾거나 새로 연다.
     *
     * <p>{@code computeIfAbsent} 를 쓰는 이유: 같은 통화의 두 leg 가 거의 동시에
     * 붙는다(Vonage 는 신고자/요원 leg 의 WebSocket 을 병렬로 연결한다).
     * 락 없이 하면 Call 이 두 번 생성되어 callId 가 갈라진다.
     */
    private LiveCall obtain(CallDescriptor desc, CallControl control) {
        // 제어할 수 없는 통화는 울려 봐야 요원이 받을 방법이 없다.
        CallState initialState = control.supportsAnswer()
                ? CallState.OFFERED
                : CallState.ANSWERED;

        return callsByKey.computeIfAbsent(desc.providerCallKey(), key -> {
            Long callId = callSessionService.beginCall(
                    desc.agentUserId(), desc.callerPhone(), initialState);
            LiveCall created = new LiveCall(key, callId, desc.agentUserId(), control, initialState);
            callsById.put(callId, created);
            log.info("[call {}] opened (key={}, agent={}, state={})",
                    callId, key, desc.agentUserId(), initialState);
            return created;
        });
    }

    // ================================================================
    // 호 제어
    // ================================================================

    @Override
    public boolean answerCall(Long callId) {
        LiveCall call = requireLiveCall(callId);

        // 상태 전이를 먼저 CAS 로 잡는다. 수락 버튼 연타나 게이트웨이 이벤트와의
        // 경합에서 승자를 하나만 남기기 위해서다. 실패하면 게이트웨이를 부르지도 않는다.
        if (!call.markAnswered()) {
            log.debug("[call {}] answer ignored (already {})", callId, call.state());
            return false;
        }

        // 그다음 게이트웨이에 응답을 지시한다. 실패하면 전이를 되돌린다 —
        // 되돌리지 않으면 화면은 통화 중인데 발신자에게는 계속 벨이 울린다.
        try {
            call.control().answer();
        } catch (RuntimeException e) {
            call.revertAnswer();
            log.error("[call {}] gateway answer failed: {}", callId, e.toString());
            throw e;
        }

        callSessionService.answerCall(callId);

        // 수락된 지금부터 전사를 시작한다. 링백 구간을 인식시키지 않기 위해
        // 여기까지 미뤄 둔 것이다.
        call.legs().forEach(OrchestratedLeg::startTranscription);

        log.info("[call {}] answered — transcription started on {} leg(s)",
                callId, call.legs().size());
        return true;
    }

    @Override
    public void hangupCall(Long callId, CallControl.HangupCause cause) {
        LiveCall call = requireLiveCall(callId);
        log.info("[call {}] hangup requested (cause={})", callId, cause);

        // 소스에 종료를 지시하기만 한다. 실제 마감은 게이트웨이가 회선을 끊고
        // leg 들이 닫히거나 closeCall() 이 불리면서 일어난다. 여기서 직접
        // 마감하면 게이트웨이에는 통화가 살아 있는 상태로 남는다.
        call.control().hangup(cause);
    }

    @Override
    public void closeCall(Long callId) {
        LiveCall call = callsById.get(callId);
        if (call == null) return;   // 이미 마감됨 — 멱등

        // leg 가 남아 있으면 닫는다. 각 close() 가 detach() 를 거쳐 오므로
        // 마지막 leg 에서 마감이 자연히 일어난다.
        List<OrchestratedLeg> remaining = List.copyOf(call.legs());
        if (!remaining.isEmpty()) {
            remaining.forEach(OrchestratedLeg::close);
            return;
        }

        // leg 가 하나도 없이 끝난 통화(벨만 울리다 끝남). detach 가 불릴 일이
        // 없으므로 여기서 직접 마감한다.
        finalizeCall(call);
    }

    @Override
    public CallState stateOf(Long callId) {
        LiveCall call = callsById.get(callId);
        return (call == null) ? null : call.state();
    }

    private LiveCall requireLiveCall(Long callId) {
        LiveCall call = callsById.get(callId);
        if (call == null) {
            throw new IllegalStateException("No active call: " + callId);
        }
        return call;
    }

    // ================================================================
    // STT / 전사
    // ================================================================

    /**
     * leg 의 STT 스트림을 연다. 통화 수락 시점에 leg 가 호출한다.
     *
     * <p>실패해도 null 을 돌려주고 leg 는 살린다. STT 가 안 붙어도
     * <b>통화 자체(오디오 브리지)는 되어야</b> 하기 때문이다. 신고 접수 도중
     * 인식 서버 장애로 통화가 끊기는 것이 인식 결과가 안 나오는 것보다
     * 훨씬 나쁘다.
     */
    private TranscriptionEngine.Stream openStt(OrchestratedLeg leg) {
        try {
            return transcriptionEngine.open(
                    transcriptionEngine.requiredFormat(),
                    result -> leg.emitFromStt(result.text(), result.isFinal()),
                    error -> log.warn("[stt/{}] {} leg error: {}",
                            transcriptionEngine.name(), leg.role(), error.toString())
            );
        } catch (RuntimeException e) {
            log.error("[stt/{}] open failed for {} leg — 통화는 계속하고 인식만 포기한다: {}",
                    transcriptionEngine.name(), leg.role(), e.toString());
            return null;
        }
    }

    /**
     * 전사 한 조각을 캐시에 넣는다. 오디오 경로(STT 결과)와
     * 텍스트 경로({@code writeTranscript})가 여기서 합류한다.
     *
     * <p>이 합류점이 있기 때문에 시나리오 재생기가 실제 통화와 같은
     * 화자 태깅·SSE push·DB flush 경로를 그대로 탄다.
     */
    private void publish(OrchestratedLeg.TranscriptChunk chunk) {
        OrchestratedLeg leg = chunk.leg();
        transcriptCache.append(
                leg.callId(),
                leg.role().wireName(),
                leg.phoneNumber(),
                chunk.text(),
                chunk.isFinal()
        );
    }

    // ================================================================
    // 종료
    // ================================================================

    /**
     * leg 하나가 닫혔다. 통화에서 떼어내고, 마지막이었으면 통화를 종료한다.
     */
    private void detach(OrchestratedLeg leg) {
        LiveCall call = leg.call();
        int remaining = call.remove(leg.legId());
        log.info("[call {}] leg detached: role={} remaining={}",
                call.callId(), leg.role(), remaining);

        if (remaining > 0) return;
        finalizeCall(call);
    }

    /**
     * 통화 마감. 진입권을 한 번만 준다.
     *
     * <p>{@code markTerminated()} 가 필요한 이유: 두 leg 가 동시에 끊기면
     * (상대가 끊으면 양쪽 소켓이 거의 동시에 닫힌다) {@code remaining == 0} 을
     * 두 스레드가 함께 볼 수 있다. {@code finalizeCall} 자체도 멱등이지만,
     * 종료 이벤트가 두 번 나가면 프런트가 통화를 두 번 닫는다.
     */
    private void finalizeCall(LiveCall call) {
        if (!call.markTerminated()) return;

        callsByKey.remove(call.providerCallKey(), call);
        callsById.remove(call.callId(), call);
        try {
            callSessionService.endCall(call.callId());
        } catch (RuntimeException e) {
            log.error("[call {}] endCall failed: {}", call.callId(), e.toString(), e);
        }
    }
}
