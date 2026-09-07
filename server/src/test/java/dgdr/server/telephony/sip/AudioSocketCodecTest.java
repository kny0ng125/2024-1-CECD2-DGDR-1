package dgdr.server.telephony.sip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AudioSocket 와이어 포맷 검증.
 *
 * <p>프레임 파싱은 스트림 동기가 걸린 코드라 한 바이트만 어긋나도 이후
 * 모든 프레임이 쓰레기가 된다. 코덱을 소켓에서 분리해 둔 덕분에
 * Asterisk 없이 여기서 전부 검증할 수 있다.
 */
class AudioSocketCodecTest {

    private static DataInputStream stream(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("헤더는 type 1바이트 + length 2바이트 big-endian 이다")
    void headerLayout() {
        byte[] encoded = AudioSocketCodec.encode(
                AudioSocketFrame.audio(new byte[]{1, 2, 3}));

        assertThat(encoded).hasSize(AudioSocketFrame.HEADER_SIZE + 3);
        assertThat(encoded[0]).isEqualTo((byte) 0x10);   // AUDIO
        assertThat(encoded[1]).isEqualTo((byte) 0x00);   // length 상위
        assertThat(encoded[2]).isEqualTo((byte) 0x03);   // length 하위
    }

    @Test
    @DisplayName("length 가 255를 넘어도 상·하위 바이트로 올바르게 쪼개진다")
    void multiByteLength() {
        byte[] payload = new byte[640];   // 16kHz 20ms
        byte[] encoded = AudioSocketCodec.encode(AudioSocketFrame.audio(payload));

        assertThat(encoded[1]).isEqualTo((byte) 0x02);   // 640 = 0x0280
        assertThat(encoded[2]).isEqualTo((byte) 0x80);
    }

    @Test
    @DisplayName("인코딩한 프레임을 그대로 다시 읽을 수 있다")
    void roundTrip() throws IOException {
        byte[] payload = new byte[320];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;

        byte[] wire = AudioSocketCodec.encode(AudioSocketFrame.audio(payload));
        AudioSocketFrame decoded = AudioSocketCodec.read(stream(wire));

        assertThat(decoded).isNotNull();
        assertThat(decoded.type()).isEqualTo(AudioSocketFrame.Type.AUDIO);
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    @DisplayName("연속된 프레임을 순서대로 읽어낸다 (스트림 동기 유지)")
    void readsConsecutiveFrames() throws IOException {
        UUID uuid = UUID.randomUUID();
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(AudioSocketCodec.encode(
                new AudioSocketFrame(AudioSocketFrame.Type.UUID, uuidBytes(uuid))));
        wire.write(AudioSocketCodec.encode(AudioSocketFrame.audio(new byte[160])));
        wire.write(AudioSocketCodec.encode(AudioSocketFrame.audio(new byte[320])));
        wire.write(AudioSocketCodec.encode(AudioSocketFrame.terminate()));

        DataInputStream in = stream(wire.toByteArray());

        assertThat(AudioSocketCodec.read(in).type()).isEqualTo(AudioSocketFrame.Type.UUID);
        assertThat(AudioSocketCodec.read(in).payload()).hasSize(160);
        assertThat(AudioSocketCodec.read(in).payload()).hasSize(320);
        assertThat(AudioSocketCodec.read(in).type()).isEqualTo(AudioSocketFrame.Type.TERMINATE);
        assertThat(AudioSocketCodec.read(in)).as("EOF 는 null").isNull();
    }

    /**
     * TCP 는 프레임 경계를 보장하지 않는다. 한 번의 read 로 헤더가 다 오지
     * 않는 상황이 실제로 발생하며, 부분 읽기를 방치하면 그 지점부터
     * 스트림 전체가 깨진다.
     */
    @Test
    @DisplayName("페이로드가 여러 TCP 세그먼트로 쪼개져 도착해도 온전히 읽는다")
    void handlesSplitReads() throws IOException {
        byte[] payload = new byte[640];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        byte[] wire = AudioSocketCodec.encode(AudioSocketFrame.audio(payload));

        // 한 번에 1바이트씩만 내주는 스트림
        InputStream trickle = new InputStream() {
            private int pos = 0;
            @Override public int read() { return pos < wire.length ? (wire[pos++] & 0xFF) : -1; }
            @Override public int read(byte[] b, int off, int len) {
                if (pos >= wire.length) return -1;
                b[off] = wire[pos++];
                return 1;
            }
        };

        AudioSocketFrame decoded = AudioSocketCodec.read(new DataInputStream(trickle));
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    @DisplayName("빈 페이로드 프레임(TERMINATE)을 처리한다")
    void emptyPayload() throws IOException {
        byte[] wire = AudioSocketCodec.encode(AudioSocketFrame.terminate());
        AudioSocketFrame decoded = AudioSocketCodec.read(stream(wire));

        assertThat(decoded.type()).isEqualTo(AudioSocketFrame.Type.TERMINATE);
        assertThat(decoded.payload()).isEmpty();
    }

    @Test
    @DisplayName("알 수 없는 프레임 타입은 예외로 알린다 (동기 손실 신호)")
    void unknownTypeFails() {
        byte[] wire = {(byte) 0x7A, 0x00, 0x00};

        assertThatThrownBy(() -> AudioSocketCodec.read(stream(wire)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x7A");
    }

    @Test
    @DisplayName("빈 스트림은 EOF 로 null 을 돌려준다")
    void emptyStreamIsEof() throws IOException {
        assertThat(AudioSocketCodec.read(stream(new byte[0]))).isNull();
    }

    @Test
    @DisplayName("16바이트 바이너리 UUID 를 표준 표기로 푼다")
    void decodesBinaryUuid() {
        UUID uuid = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");

        assertThat(AudioSocketCodec.decodeUuid(uuidBytes(uuid)))
                .isEqualTo(uuid.toString());
    }

    @Test
    @DisplayName("ASCII 문자열로 온 UUID 도 받아준다 (게이트웨이 구현 편차)")
    void decodesAsciiUuid() {
        String uuid = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

        assertThat(AudioSocketCodec.decodeUuid(uuid.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .isEqualTo(uuid);
    }

    @Test
    @DisplayName("페이로드가 16bit length 상한을 넘으면 거부한다")
    void rejectsOversizedPayload() {
        byte[] tooBig = new byte[AudioSocketFrame.MAX_PAYLOAD + 1];

        assertThatThrownBy(() ->
                AudioSocketCodec.write(new ByteArrayOutputStream(), AudioSocketFrame.audio(tooBig)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
