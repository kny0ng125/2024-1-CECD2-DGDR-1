package dgdr.server.telephony.core;

/**
 * 코어 → 통화 소스 방향의 <b>호 제어</b> 출구. 통화 소스가 구현한다.
 *
 * <h2>{@link MediaSink} 와 별도 인터페이스인 이유</h2>
 * <p>실전 컨택센터는 <b>콜 컨트롤 경로와 미디어 경로를 다른 계층으로</b> 둔다.
 * CSTA(ECMA-269)·TAPI/JTAPI·Avaya TSAPI 같은 CTI 인터페이스가 응답/보류/전환
 * 신호를 나르고, 오디오는 RTP 로 별도 경로를 탄다. Cisco Finesse 가 콜 컨트롤과
 * 화면만 담당하고 오디오는 IP Phone 이 맡는 것이 대표적이다.
 *
 * <p>이 분리는 편의가 아니라 필연이다. 두 경로는
 * <ul>
 *   <li>전달 주체가 다르고 (신호는 게이트웨이 제어면, 오디오는 미디어면)</li>
 *   <li>실패 양상이 다르며 (제어 실패 = 통화가 안 끊김, 미디어 실패 = 소리가 안 남)</li>
 *   <li>제어를 지원하지 않는 소스가 존재한다 (녹음 재생, 단방향 포크)</li>
 * </ul>
 * 하나의 인터페이스로 합치면 이 차이가 전부 뭉개진다.
 *
 * <h2>범위</h2>
 * <p>수락과 종료만 다룬다. 보류·전환·컨퍼런스는 다루지 않는다 — 전환 시
 * 첨부데이터를 살리려면 SIP {@code User-to-User} 헤더(RFC 7433) 같은
 * 호 상관관계 식별자 설계가 따로 필요한데, 그것은 이 시스템의 범위 밖이다.
 */
public interface CallControl {

    /**
     * 걸려온 통화를 수락한다. 이 호출이 성공해야 미디어가 흐르기 시작한다.
     *
     * <p>구현체는 멱등해야 한다. 요원이 수락 버튼을 두 번 눌렀다고 해서
     * 게이트웨이에 응답 요청이 두 번 가서는 안 된다.
     */
    void answer();

    /**
     * 통화를 끊는다.
     *
     * @param cause 종료 사유. SIP 응답 코드로 번역되어 발신자에게 전달되므로
     *              (거절 603 vs 정상 종료 BYE) 의미상 구분이 필요하다.
     */
    void hangup(HangupCause cause);

    /**
     * 요원 수락을 기다리는 상태(벨 울림)를 지원하는가.
     *
     * <p>{@code false} 면 코어는 통화를 곧바로 {@link CallState#ANSWERED} 로
     * 시작한다. 제어할 수 없는 통화를 울려 봐야 요원이 받을 방법이 없기 때문이다.
     * 시나리오 재생기나 이미 응답된 채로 붙는 소스가 여기 해당한다.
     */
    default boolean supportsAnswer() {
        return true;
    }

    enum HangupCause {
        /** 통화가 정상적으로 끝났다. */
        NORMAL,
        /** 요원이 수신을 거절했다. */
        REJECTED,
        /** 요원이 다른 통화 중이다. */
        BUSY,
        /** 정해진 시간 안에 수락되지 않았다. */
        TIMEOUT
    }

    /**
     * 호 제어를 지원하지 않는 소스용.
     *
     * <p>{@link #supportsAnswer()} 가 {@code false} 이므로 통화는 즉시
     * 통화중 상태로 시작하고, 종료는 leg 가 닫힐 때 자연히 일어난다.
     * {@link #hangup}은 조용히 무시한다 — 끊을 수단이 없는 소스에 대고
     * 예외를 던지면 통화 종료 경로 전체가 실패한다.
     */
    CallControl UNSUPPORTED = new CallControl() {
        @Override public void answer() {
            throw new UnsupportedOperationException("This call source cannot be answered");
        }
        @Override public void hangup(HangupCause cause) { /* no-op */ }
        @Override public boolean supportsAnswer() { return false; }
        @Override public String toString() { return "CallControl.UNSUPPORTED"; }
    };
}
