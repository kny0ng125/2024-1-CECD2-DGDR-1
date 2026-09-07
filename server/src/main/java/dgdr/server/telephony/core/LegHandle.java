package dgdr.server.telephony.core;

/**
 * 통화 소스가 코어로부터 돌려받는 leg 핸들. <b>코어가 구현한다.</b>
 *
 * <p>소스는 이 핸들에 데이터를 밀어 넣기만 하면 되고, 그 뒤에서 벌어지는
 * 브리지·STT·화자 태깅·SSE push·통화 종료 처리는 전부 코어 몫이다.
 *
 * <h2>입력 경로가 둘인 이유</h2>
 * <p>{@link #writeAudio}는 오디오를 주는 소스용, {@link #writeTranscript}는
 * 전사 텍스트를 직접 주는 소스용이다. 후자가 있어야
 * <ul>
 *   <li>시나리오 재생기가 캐시를 직접 건드리는 특권을 잃고,
 *       실제 통화와 <b>같은</b> leg 생명주기·callId 발급·종료 경로를 탄다.
 *       "가짜에서는 되는데 실제로는 안 되는" 부류의 버그가 구조적으로 사라진다.</li>
 *   <li>자체 STT 가 붙은 IPCC·SIP 게이트웨이(음성인식 결과만 넘겨주는 경우)도
 *       같은 문으로 들어올 수 있다.</li>
 * </ul>
 * 두 메서드는 배타적이지 않다. 한 leg 가 둘 다 써도 코어는 문제 삼지 않는다.
 */
public interface LegHandle extends AutoCloseable {

    /** 이 leg 가 속한 통화의 DB callId. 소스가 프런트에 알려줄 때 쓴다. */
    Long callId();

    /** 통화 소스가 부여한 통화 키 (같은 값끼리 한 통화로 묶임). */
    String providerCallKey();

    LegRole role();

    /**
     * 소스가 수신한 오디오를 코어에 밀어 넣는다.
     *
     * <p>코어는 이 오디오를 (1) 같은 통화의 다른 leg 들의 {@link MediaSink} 로
     * 릴레이하고 (2) 이 leg 전용 STT 스트림에 기록한다.
     * 호출자는 소유권을 넘긴다고 가정한다 — 코어는 배열을 보관할 수 있으므로
     * 호출 후 재사용하지 말 것.
     */
    void writeAudio(byte[] pcm);

    /**
     * 오디오 없이 전사 결과를 직접 밀어 넣는다.
     *
     * @param isFinal {@code false} 면 실시간 자막용 partial (DB 저장 대상 아님),
     *                {@code true} 면 확정 발화 (통화 종료 시 DB flush 대상)
     */
    void writeTranscript(String text, boolean isFinal);

    /**
     * 이 leg 를 닫는다. STT 스트림을 정리하고 통화에서 제외한다.
     * 통화의 마지막 leg 였다면 코어가 통화 종료 절차까지 수행한다.
     *
     * <p>여러 번 호출해도 안전해야 한다(멱등).
     */
    @Override
    void close();
}
