package dgdr.server.telephony.sip;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * AudioSocket 프레임 인코딩/디코딩. 순수 함수 모음 — 소켓을 모른다.
 *
 * <p>I/O 와 분리해 둔 이유는 테스트다. 프레임 파싱은 스트림 동기가 걸린
 * 코드라 한 바이트만 어긋나도 이후 전부가 쓰레기가 되는데, 실제 TCP 연결을
 * 띄우지 않고는 검증할 수 없다면 아무도 검증하지 않게 된다.
 * {@code ByteArrayInputStream} 하나로 테스트할 수 있게 열어 둔다.
 */
public final class AudioSocketCodec {

    private AudioSocketCodec() {}

    /**
     * 프레임 하나를 읽는다. 스트림이 정상 종료되면 {@code null}.
     *
     * <p>{@code readFully} 를 쓰는 것이 중요하다. TCP 는 프레임 경계를
     * 보장하지 않으므로 {@code read()} 한 번으로 헤더 3바이트가 다 온다는
     * 보장이 없다. 부분 읽기를 방치하면 그 순간부터 스트림 동기가 깨진다.
     *
     * @throws IOException              전송 오류
     * @throws IllegalArgumentException 알 수 없는 프레임 타입 (동기 손실 신호)
     */
    public static AudioSocketFrame read(DataInputStream in) throws IOException {
        int typeCode;
        try {
            typeCode = in.readUnsignedByte();
        } catch (EOFException eof) {
            return null;   // 게이트웨이가 소켓을 닫았다. 정상 종료.
        }

        AudioSocketFrame.Type type = AudioSocketFrame.Type.fromCode(typeCode);

        int length = in.readUnsignedShort();   // big-endian
        if (length == 0) {
            return new AudioSocketFrame(type, new byte[0]);
        }

        byte[] payload = new byte[length];
        in.readFully(payload);
        return new AudioSocketFrame(type, payload);
    }

    /** 프레임 하나를 쓴다. 호출자가 동기화를 책임진다. */
    public static void write(OutputStream out, AudioSocketFrame frame) throws IOException {
        byte[] payload = frame.payload();
        if (payload.length > AudioSocketFrame.MAX_PAYLOAD) {
            throw new IllegalArgumentException(
                    "payload too large: " + payload.length + " > " + AudioSocketFrame.MAX_PAYLOAD);
        }
        byte[] header = new byte[AudioSocketFrame.HEADER_SIZE];
        header[0] = (byte) frame.type().code();
        header[1] = (byte) ((payload.length >> 8) & 0xFF);
        header[2] = (byte) (payload.length & 0xFF);

        out.write(header);
        if (payload.length > 0) out.write(payload);
        out.flush();
    }

    /**
     * UUID 프레임 payload 를 문자열로 푼다.
     *
     * <p>Asterisk 는 16바이트 바이너리로 보내지만, 구현/버전에 따라 36자
     * ASCII 문자열로 보내는 경우도 있다. 둘 다 받아준다 — 여기서 실패하면
     * 통화 키가 없어 leg 를 붙일 수 없으므로 관용적으로 처리할 가치가 있다.
     */
    public static String decodeUuid(byte[] payload) {
        if (payload.length == 16) {
            ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
            return new UUID(bb.getLong(), bb.getLong()).toString();
        }
        String text = new String(payload, java.nio.charset.StandardCharsets.US_ASCII).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty AudioSocket UUID payload");
        }
        return text;
    }

    /** 테스트/디버깅용: 스트림 없이 프레임을 바이트 배열로. */
    public static byte[] encode(AudioSocketFrame frame) {
        byte[] payload = frame.payload();
        byte[] out = new byte[AudioSocketFrame.HEADER_SIZE + payload.length];
        out[0] = (byte) frame.type().code();
        out[1] = (byte) ((payload.length >> 8) & 0xFF);
        out[2] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, out, AudioSocketFrame.HEADER_SIZE, payload.length);
        return out;
    }

    /** 테스트용 헬퍼 — {@link InputStream} 을 {@link DataInputStream} 으로 감싼다. */
    public static DataInputStream wrap(InputStream in) {
        return in instanceof DataInputStream dis ? dis : new DataInputStream(in);
    }
}
