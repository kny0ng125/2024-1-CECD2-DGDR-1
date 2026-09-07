package dgdr.server.telephony.sip;

/**
 * Asterisk AudioSocket 프레임.
 *
 * <p>와이어 포맷은 극단적으로 단순하다.
 * <pre>
 *   +--------+------------------+------------------+
 *   | type   | length (big-end) | payload          |
 *   | 1 byte | 2 bytes          | length bytes     |
 *   +--------+------------------+------------------+
 * </pre>
 *
 * <p>이 단순함이 AudioSocket 을 고른 이유다. SIP 스택(INVITE/SDP 협상,
 * RTP/RTCP, jitter buffer, DTMF)을 직접 구현하는 것은 이 프로젝트의 범위가
 * 아니고, 그 부분은 Asterisk 같은 검증된 게이트웨이가 훨씬 잘한다.
 * 서버는 게이트웨이가 이미 풀어놓은 <b>순수 PCM 스트림</b>만 받으면 되고,
 * AudioSocket 은 정확히 그 경계에 있는 프로토콜이다.
 *
 * <p>Asterisk dialplan 쪽 대응:
 * <pre>
 *   exten =&gt; _X.,1,Answer()
 *    same =&gt;      n,Set(AUDIOSOCKET_UUID=${UUID})
 *    same =&gt;      n,AudioSocket(${AUDIOSOCKET_UUID},127.0.0.1:9092)
 *    same =&gt;      n,Hangup()
 * </pre>
 */
public record AudioSocketFrame(Type type, byte[] payload) {

    /** 헤더 크기: type 1 + length 2 */
    public static final int HEADER_SIZE = 3;

    /** length 필드가 16bit 이므로 payload 상한. */
    public static final int MAX_PAYLOAD = 0xFFFF;

    public enum Type {
        /** 0x00 — 게이트웨이가 통화 종료를 알림. payload 없음. */
        TERMINATE(0x00),

        /** 0x01 — 통화 식별자(16바이트 UUID). 연결 직후 한 번 온다. */
        UUID(0x01),

        /**
         * 0x03 — DTMF 한 글자(ASCII 1바이트).
         * 수보 업무에서 쓸 일은 없지만, 프레임 타입을 모르면 스트림 동기가
         * 깨지므로 인지하고 버린다.
         */
        DTMF(0x03),

        /**
         * 0x10 — 오디오. signed linear 16bit little-endian.
         *
         * <p>Asterisk 공식 규격은 이 타입을 <b>8kHz mono(slin)</b> 로 정의하며,
         * 한 프레임은 320바이트 = 정확히 20ms 다. 채널 포맷을 slin16 으로
         * 강제하면 16kHz 가 오기도 하지만 게이트웨이 버전에 따라 다르므로,
         * 서버는 8kHz 를 기본으로 두고 필요 시 리샘플링한다
         * ({@code sip.audiosocket.codec}).
         */
        AUDIO(0x10),

        /** 0xFF — 게이트웨이가 보고한 오류. payload 1바이트 코드. */
        ERROR(0xFF);

        private final int code;

        Type(int code) { this.code = code; }

        public int code() { return code; }

        public static Type fromCode(int code) {
            for (Type t : values()) {
                if (t.code == code) return t;
            }
            throw new IllegalArgumentException(
                    String.format("Unknown AudioSocket frame type: 0x%02X", code));
        }
    }

    public static AudioSocketFrame audio(byte[] pcm) {
        return new AudioSocketFrame(Type.AUDIO, pcm);
    }

    public static AudioSocketFrame terminate() {
        return new AudioSocketFrame(Type.TERMINATE, new byte[0]);
    }
}
