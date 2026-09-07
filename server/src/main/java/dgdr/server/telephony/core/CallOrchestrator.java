package dgdr.server.telephony.core;

/**
 * 통화 도메인의 유일한 진입점. 통화 소스는 이것 하나만 알면 된다.
 *
 * <p>이 인터페이스의 좁음이 설계의 요지다. 통화 소스가 할 수 있는 일은
 * "leg 를 붙인다" 뿐이고, 그 뒤는 전부 코어 안에서 벌어진다.
 * 소스가 {@code Call} 엔티티를 만들거나, 캐시에 직접 쓰거나,
 * {@code call_started} 를 스스로 push 하는 경로는 존재하지 않는다.
 *
 * <h2>이 경계가 해결한 것</h2>
 * <ol>
 *   <li><b>callId 발급 이원화</b> — 이전에는 Vonage 경로가 {@code Call} 을
 *       직접 저장하고 시나리오 경로는 {@code CallSessionService.beginCall()} 을
 *       썼다. 전자는 control 채널로 {@code call_started} 를 쏘지 않았고,
 *       그래서 실제 통화에서는 프런트가 callId 를 못 받아 자막 구독을
 *       시작할 수 없었다. 발급을 코어가 독점하면서 두 경로가 합쳐졌다.</li>
 *   <li><b>통화 간 오디오 혼선</b> — 이전 브리지는 열린 WebSocket 세션
 *       <i>전부</i>를 순회하며 자기 자신만 빼고 릴레이했다. 통화가 둘 이상
 *       동시에 진행되면 서로 다른 신고의 음성이 섞인다. leg 를
 *       {@code providerCallKey} 로 묶으면서 구조적으로 불가능해졌다.</li>
 * </ol>
 */
public interface CallOrchestrator {

    /**
     * leg 보다 <b>먼저</b> 통화를 연다. 벨이 울리는 구간을 만들기 위한 것이다.
     *
     * <p>SIP 게이트웨이에서 통화가 도착하면 요원이 수락하기 전까지 오디오
     * 연결이 없다. 그 구간에도 통화는 존재해야 한다 — 요원 화면에 벨을 띄우려면
     * callId 가 필요하기 때문이다. 이 메서드가 그 공백을 메운다.
     *
     * <p>통화는 {@link CallState#OFFERED} 로 시작하고 {@code call_offered} 가
     * 요원 채널로 나간다. 이후 {@link #answerCall}이 불리면 소스가 미디어를
     * 붙이고, 그때 {@link #attachLeg} 가 <b>같은 providerCallKey 로</b> 들어와
     * 이 통화에 합류한다.
     *
     * @return 발급된 callId
     */
    Long openCall(CallDescriptor desc, CallControl control);

    /**
     * leg 하나를 통화에 붙인다.
     *
     * <p>{@code desc.providerCallKey()} 에 해당하는 통화가 없으면 새로 만들면서
     * DB {@code Call} 을 생성하고 담당 요원의 control 채널로 알림을 push 한다.
     * 이미 있으면 그 통화에 합류한다.
     *
     * <p>새 통화의 초기 상태는 {@code control.supportsAnswer()} 가 결정한다.
     * 제어 가능한 소스면 {@link CallState#OFFERED}(벨 울림)로 시작해 요원의
     * 수락을 기다리고, 아니면 곧바로 {@link CallState#ANSWERED} 로 시작한다.
     *
     * @param desc    이 leg 의 신원·포맷
     * @param sink    코어가 이 leg 로 오디오를 되돌려 보낼 출구.
     *                오디오를 받을 수 없는 소스는 {@link MediaSink#NULL}.
     * @param control 코어가 이 통화를 수락·종료할 창구.
     *                제어할 수 없는 소스는 {@link CallControl#UNSUPPORTED}.
     * @return 소스가 데이터를 밀어 넣고 종료를 알릴 핸들
     */
    LegHandle attachLeg(LegDescriptor desc, MediaSink sink, CallControl control);

    /**
     * 요원이 통화를 수락했다. 미디어가 흐르기 시작하고 전사가 개시된다.
     *
     * <p>멱등하다. 이미 수락된 통화에 대한 호출은 조용히 무시한다.
     *
     * @return 이번 호출로 실제로 수락이 일어났으면 true
     * @throws IllegalStateException 진행 중이 아닌 통화인 경우
     */
    boolean answerCall(Long callId);

    /**
     * 요원이 통화를 종료하거나 거절했다.
     *
     * <p>게이트웨이에 종료를 지시하고, 그 결과로 leg 들이 닫히면서 통화 마감
     * 절차가 자연히 진행된다. 코어가 직접 통화를 끝내지 않고 소스를 통해
     * 끊는 이유는, 발신자 쪽 회선을 실제로 해제할 수 있는 주체가 소스뿐이기
     * 때문이다. 여기서 임의로 마감하면 게이트웨이에는 통화가 살아 있는
     * 상태로 남는다.
     */
    void hangupCall(Long callId, CallControl.HangupCause cause);

    /**
     * 통화가 실제로 끝났음을 소스가 통보한다. 남은 leg 를 닫고 마감한다.
     *
     * <p>{@link #hangupCall} 이 "끊어 달라는 요청"인 반면 이것은 "끊겼다는 사실"이다.
     * 둘을 나눠 둔 이유: leg 가 하나도 없는 채로 끝나는 통화가 존재한다.
     * 벨만 울리다 발신자가 포기하거나 요원이 거절한 경우, 미디어가 붙은 적이
     * 없으므로 leg 종료로는 마감이 트리거되지 않는다. 그런 통화도 기록에는
     * 남아야 한다 — 놓친 신고가 사라지면 안 된다.
     *
     * <p>멱등하다.
     */
    void closeCall(Long callId);

    /** 진행 중인 통화의 현재 상태. 통화가 없으면 {@code null}. */
    CallState stateOf(Long callId);
}
