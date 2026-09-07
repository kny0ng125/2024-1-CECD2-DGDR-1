package dgdr.server.telephony.core;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 진행 중인 통화 한 건. {@code providerCallKey} 로 식별되며 leg 들을 소유한다.
 *
 * <p>이 클래스의 존재 자체가 이전 구조의 버그를 막는다. 예전 브리지는
 * "열려 있는 모든 WebSocket 세션"을 순회했기 때문에 통화가 둘 이상 동시에
 * 진행되면 다른 신고의 음성이 섞였다. 릴레이 대상이 {@code LiveCall} 안으로
 * 한정되면서 그 실수를 저지를 방법이 없어졌다.
 */
final class LiveCall {

    private final String providerCallKey;
    private final Long callId;
    private final String agentUserId;

    /** 호 제어 창구. 소스가 제어를 지원하지 않으면 {@link CallControl#UNSUPPORTED}. */
    private final CallControl control;

    /** legId → leg. leg 는 attach/close 가 서로 다른 스레드에서 일어난다. */
    private final ConcurrentMap<String, OrchestratedLeg> legs = new ConcurrentHashMap<>();

    private final AtomicReference<CallState> state;

    /** 종료 절차를 한 번만 타게 하는 래치. */
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    LiveCall(String providerCallKey, Long callId, String agentUserId,
             CallControl control, CallState initialState) {
        this.providerCallKey = providerCallKey;
        this.callId = callId;
        this.agentUserId = agentUserId;
        this.control = control;
        this.state = new AtomicReference<>(initialState);
    }

    String providerCallKey() { return providerCallKey; }
    Long callId()            { return callId; }
    String agentUserId()     { return agentUserId; }
    CallControl control()    { return control; }
    CallState state()        { return state.get(); }

    void add(OrchestratedLeg leg) {
        legs.put(leg.legId(), leg);
    }

    /** @return 이 leg 를 뺀 뒤 통화에 남은 leg 수 */
    int remove(String legId) {
        legs.remove(legId);
        return legs.size();
    }

    Collection<OrchestratedLeg> legs() {
        return legs.values();
    }

    /**
     * OFFERED → ANSWERED 전이를 한 번만 허용한다.
     *
     * <p>CAS 를 쓰는 이유: 요원의 수락 요청과 게이트웨이의 응답 이벤트가
     * 동시에 도착할 수 있다. 둘 다 전이에 성공하면 STT 스트림이 leg 당 두 개
     * 열리고, 같은 발화가 두 번 전사된다.
     *
     * @return 이번 호출로 실제 전이가 일어났으면 true
     */
    boolean markAnswered() {
        return state.compareAndSet(CallState.OFFERED, CallState.ANSWERED);
    }

    /**
     * 게이트웨이가 응답에 실패했을 때 전이를 되돌린다.
     *
     * <p>이미 종료된 통화는 되돌리지 않는다 — 되돌리는 사이에 회선이 끊겼다면
     * ENDED 가 맞는 상태이고, 여기서 OFFERED 로 밀어 올리면 끝난 통화가
     * 다시 울리는 것처럼 보인다.
     */
    void revertAnswer() {
        state.compareAndSet(CallState.ANSWERED, CallState.OFFERED);
    }

    /** 종료 절차 진입권을 딱 한 번만 준다. */
    boolean markTerminated() {
        state.set(CallState.ENDED);
        return terminated.compareAndSet(false, true);
    }

    /**
     * 오디오를 <b>같은 통화 안의</b> 다른 leg 들로 릴레이한다.
     *
     * <p>포맷이 다른 leg 로 보낼 때는 변환한다. 실제로 필요한 경우가 있다 —
     * 8kHz 로 들어오는 SIP leg 와 16kHz 인 Vonage leg 가 한 통화에 섞일 수 있다.
     */
    void bridgeFrom(String senderLegId, byte[] pcm, AudioFormat senderFormat) {
        for (OrchestratedLeg peer : legs.values()) {
            if (peer.legId().equals(senderLegId)) continue;
            MediaSink sink = peer.sink();
            if (!sink.isOpen()) continue;
            try {
                byte[] payload = senderFormat.equals(peer.format())
                        ? pcm
                        : PcmResampler.convert(pcm, senderFormat, peer.format());
                sink.send(payload);
            } catch (RuntimeException e) {
                // 한 leg 로의 전송 실패가 나머지 leg 브리지를 막아서는 안 된다.
                peer.logRelayFailure(e);
            }
        }
    }
}
