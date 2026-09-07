package dgdr.server.telephony.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 8kHz SIP leg 와 16kHz CLOVA 사이를 잇는 변환 검증.
 */
class PcmResamplerTest {

    private static byte[] pcm(short... samples) {
        ByteBuffer bb = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : samples) bb.putShort(s);
        return bb.array();
    }

    private static short[] samples(byte[] pcm) {
        ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[pcm.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = bb.getShort();
        return out;
    }

    @Test
    @DisplayName("같은 샘플레이트면 입력을 그대로 돌려준다")
    void noOpWhenRatesMatch() {
        byte[] input = pcm((short) 1, (short) 2, (short) 3);

        assertThat(PcmResampler.resample(input, 16000, 16000)).isSameAs(input);
    }

    @Test
    @DisplayName("8k → 16k 업샘플링은 샘플 수가 두 배가 된다")
    void upsampleDoublesSampleCount() {
        byte[] input = pcm(new short[160]);   // 8kHz 20ms

        byte[] output = PcmResampler.resample(input, 8000, 16000);

        assertThat(samples(output)).hasSize(320);
    }

    @Test
    @DisplayName("16k → 8k 다운샘플링은 샘플 수가 절반이 된다")
    void downsampleHalvesSampleCount() {
        byte[] input = pcm(new short[320]);

        byte[] output = PcmResampler.resample(input, 16000, 8000);

        assertThat(samples(output)).hasSize(160);
    }

    @Test
    @DisplayName("양 끝점 값은 보존된다")
    void preservesEndpoints() {
        byte[] input = pcm((short) 1000, (short) 2000, (short) 3000, (short) 4000);

        short[] out = samples(PcmResampler.resample(input, 8000, 16000));

        assertThat(out[0]).isEqualTo((short) 1000);
        assertThat(out[out.length - 1]).isEqualTo((short) 4000);
    }

    @Test
    @DisplayName("선형 보간이므로 중간값은 이웃 샘플 사이에 놓인다")
    void interpolatesBetweenNeighbours() {
        byte[] input = pcm((short) 0, (short) 1000);

        short[] out = samples(PcmResampler.resample(input, 8000, 16000));

        assertThat(out).hasSize(4);
        assertThat(out[1]).isBetween((short) 0, (short) 1000);
        assertThat(out[2]).isBetween((short) 0, (short) 1000);
        // 단조 증가 신호는 변환 후에도 단조 증가여야 한다.
        for (int i = 1; i < out.length; i++) {
            assertThat(out[i]).isGreaterThanOrEqualTo(out[i - 1]);
        }
    }

    @Test
    @DisplayName("음수 샘플에서도 부호를 잃지 않는다")
    void handlesNegativeSamples() {
        byte[] input = pcm((short) -20000, (short) -10000);

        short[] out = samples(PcmResampler.resample(input, 8000, 16000));

        assertThat(out[0]).isEqualTo((short) -20000);
        assertThat(out[out.length - 1]).isEqualTo((short) -10000);
    }

    @Test
    @DisplayName("빈 입력이나 1바이트 입력은 그대로 통과시킨다")
    void toleratesTinyInput() {
        assertThat(PcmResampler.resample(new byte[0], 8000, 16000)).isEmpty();
        assertThat(PcmResampler.resample(null, 8000, 16000)).isNull();
    }

    @Test
    @DisplayName("AudioFormat 변환은 샘플레이트만 다룬다")
    void convertHandlesSampleRateOnly() {
        byte[] input = pcm(new short[160]);

        byte[] out = PcmResampler.convert(input, AudioFormat.PCM16_8K_MONO, AudioFormat.PCM16_16K_MONO);

        assertThat(out).hasSize(640);
    }

    /**
     * 조용히 깨진 오디오를 STT 로 흘려보내는 것보다 터지는 편이 낫다.
     * 인식 결과가 이상해진 뒤 원인을 역추적하는 비용이 훨씬 크다.
     */
    @Test
    @DisplayName("비트수·채널수가 다르면 변환하지 않고 예외를 던진다")
    void refusesNonSampleRateConversion() {
        AudioFormat stereo = new AudioFormat(16000, 16, 2);

        assertThatThrownBy(() ->
                PcmResampler.convert(pcm(new short[10]), AudioFormat.PCM16_16K_MONO, stereo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
