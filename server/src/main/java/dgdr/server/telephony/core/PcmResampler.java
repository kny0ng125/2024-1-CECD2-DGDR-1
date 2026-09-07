package dgdr.server.telephony.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 16bit little-endian mono PCM 의 샘플레이트 변환.
 *
 * <p>필요해진 이유는 구체적이다. Asterisk AudioSocket 의 기본 코덱 {@code slin}
 * 은 8kHz 인데 CLOVA Nest 는 16kHz 만 받는다. Asterisk dialplan 에서
 * {@code slin16} 으로 강제할 수도 있지만, 게이트웨이 설정에 의존하지 않고
 * 서버가 스스로 맞추는 편이 배포 조건을 덜 탄다.
 *
 * <p>선형 보간이다. 통화 음성(8k→16k, 정수배)에는 충분하고,
 * 품질을 더 올리려면 폴리페이즈 FIR 이 필요하지만 STT 정확도에 유의미한
 * 차이를 만들지 못한다고 판단했다.
 */
public final class PcmResampler {

    private PcmResampler() {}

    /**
     * @param pcm  16bit LE mono PCM
     * @param from 원본 샘플레이트
     * @param to   목표 샘플레이트
     * @return 변환된 PCM. {@code from == to} 면 입력을 그대로 돌려준다.
     */
    public static byte[] resample(byte[] pcm, int from, int to) {
        if (pcm == null || pcm.length < 2) return pcm;
        if (from == to) return pcm;

        ByteBuffer in = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int inCount = pcm.length / 2;
        short[] src = new short[inCount];
        for (int i = 0; i < inCount; i++) src[i] = in.getShort();

        int outCount = (int) ((long) inCount * to / from);
        if (outCount <= 0) return new byte[0];

        ByteBuffer out = ByteBuffer.allocate(outCount * 2).order(ByteOrder.LITTLE_ENDIAN);
        double step = (double) (inCount - 1) / Math.max(1, outCount - 1);
        for (int i = 0; i < outCount; i++) {
            double pos = i * step;
            int idx = (int) pos;
            double frac = pos - idx;
            short a = src[Math.min(idx, inCount - 1)];
            short b = src[Math.min(idx + 1, inCount - 1)];
            out.putShort((short) Math.round(a + (b - a) * frac));
        }
        return out.array();
    }

    /**
     * leg 포맷에서 엔진 포맷으로 변환. 샘플레이트만 다루므로
     * 비트수·채널수가 다르면 변환하지 않고 예외를 던진다 — 조용히 깨진 오디오를
     * STT 에 흘려보내는 것보다 기동/연결 시점에 터지는 편이 낫다.
     */
    public static byte[] convert(byte[] pcm, AudioFormat from, AudioFormat to) {
        if (from.equals(to)) return pcm;
        if (from.bitsPerSample() != to.bitsPerSample() || from.channels() != to.channels()) {
            throw new IllegalArgumentException(
                    "Only sample-rate conversion is supported: " + from + " -> " + to);
        }
        return resample(pcm, from.sampleRate(), to.sampleRate());
    }
}
