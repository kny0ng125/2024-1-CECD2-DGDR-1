package dgdr.server.telephony.core;

/**
 * 통화 생명주기 상태.
 *
 * <pre>
 *   OFFERED ──answer()──> ANSWERED ──종료──> ENDED
 *      │                                       ▲
 *      └──reject / timeout / 발신자 포기────────┘
 * </pre>
 *
 * <h2>OFFERED 를 별도 상태로 두는 이유</h2>
 * <p>수락 버튼이 의미를 가지려면 "아직 받지 않은" 상태가 존재해야 한다.
 * 그리고 그 구분은 UI 문제로 끝나지 않는다.
 * <ul>
 *   <li><b>전사 시작 시점</b> — OFFERED 구간에서 STT 스트림을 열면 링백 톤과
 *       무음을 인식하게 된다. 코어는 ANSWERED 로 넘어간 뒤에야 스트림을 연다.</li>
 *   <li><b>보존기간 판단</b> — 미응답 통화는 신고 내용이 없다. 전사 기록이
 *       없으므로 파기 대상도 없고, {@code calls} 행만 통계·감사용으로 남는다.</li>
 *   <li><b>응답 시각</b> — 접수 지연(벨 울린 시각 ~ 수락 시각)은 상황실의
 *       핵심 성과 지표다. 두 시각을 구분해 기록해야 산출할 수 있다.</li>
 * </ul>
 */
public enum CallState {

    /** 걸려왔고 요원 수락을 기다리는 중. 미디어는 아직 흐르지 않는다. */
    OFFERED,

    /** 요원이 수락해 통화가 성립했다. 이 시점부터 전사가 시작된다. */
    ANSWERED,

    /** 종료됨. 수락 없이 끝났을 수도 있다(거절·타임아웃·발신자 포기). */
    ENDED;

    public boolean isTerminal() {
        return this == ENDED;
    }
}
