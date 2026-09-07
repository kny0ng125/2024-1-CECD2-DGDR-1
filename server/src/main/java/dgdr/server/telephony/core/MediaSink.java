package dgdr.server.telephony.core;

/**
 * 코어 → 통화 소스 방향의 오디오 출구. <b>통화 소스가 구현한다.</b>
 *
 * <p>코어는 A leg 에서 받은 오디오를 같은 통화의 다른 leg 로 되돌려 보내야
 * 통화가 성립한다(브리지). 그 "되돌려 보내는 방법"은 소스마다 다르다.
 * <ul>
 *   <li>Vonage → 해당 WebSocket 세션에 binary frame</li>
 *   <li>AudioSocket → 해당 TCP 소켓에 {@code 0x10} 프레임</li>
 *   <li>시나리오 재생 → 보낼 곳이 없음 ({@link #NULL})</li>
 * </ul>
 *
 * <p>이 인터페이스가 없으면 코어가 {@code WebSocketSession} 같은 전송 계층
 * 타입을 알아야 하고, 그 순간 코어는 특정 소스에 묶인다.
 */
public interface MediaSink {

    /**
     * 이 leg 로 PCM 을 내보낸다.
     *
     * <p>구현체는 예외를 던지지 않는 편이 낫다. 브리지는 다수 leg 를 순회하며
     * 호출되므로, 한 leg 의 전송 실패가 나머지 leg 로의 전송을 막아서는 안 된다.
     */
    void send(byte[] pcm);

    /** 아직 이 leg 로 보낼 수 있는지. 닫힌 sink 는 브리지 대상에서 제외된다. */
    boolean isOpen();

    /**
     * 오디오를 받을 수 없는 leg 용 no-op sink.
     *
     * <p>{@code isOpen()} 이 {@code false} 이므로 브리지가 알아서 건너뛴다.
     * null 을 흘려보내는 대신 이 상수를 쓴다.
     */
    MediaSink NULL = new MediaSink() {
        @Override public void send(byte[] pcm) { /* no-op */ }
        @Override public boolean isOpen() { return false; }
        @Override public String toString() { return "MediaSink.NULL"; }
    };
}
