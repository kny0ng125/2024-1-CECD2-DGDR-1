package dgdr.server.telephony.core;

/**
 * 통화 leg 가 실어 나르는 PCM 포맷.
 *
 * <p>통화 소스마다 포맷이 다르다는 것이 이 타입이 존재하는 이유다.
 * <ul>
 *   <li>Vonage {@code audio/l16;rate=16000} → 16kHz</li>
 *   <li>Asterisk AudioSocket 기본 {@code slin} → 8kHz (slin16 을 쓰면 16kHz)</li>
 * </ul>
 *
 * <p>STT 엔진은 자기가 받을 수 있는 포맷이 정해져 있으므로
 * ({@code TranscriptionEngine#supports}), 코어가 leg 포맷과 엔진 포맷이
 * 다를 때 리샘플링을 끼워 넣는다. 이 판단을 하려면 leg 가 자기 포맷을
 * 값으로 들고 있어야 한다.
 *
 * @param sampleRate    샘플레이트 (Hz)
 * @param bitsPerSample 샘플당 비트 수
 * @param channels      채널 수
 */
public record AudioFormat(int sampleRate, int bitsPerSample, int channels) {

    /** Vonage WebSocket / Asterisk slin16 */
    public static final AudioFormat PCM16_16K_MONO = new AudioFormat(16000, 16, 1);

    /** Asterisk AudioSocket 기본 포맷 (slin) */
    public static final AudioFormat PCM16_8K_MONO = new AudioFormat(8000, 16, 1);

    public AudioFormat {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        if (bitsPerSample <= 0) throw new IllegalArgumentException("bitsPerSample must be positive");
        if (channels <= 0) throw new IllegalArgumentException("channels must be positive");
    }

    /** 1초 분량의 바이트 수. 프레임 크기 계산·검증용. */
    public int bytesPerSecond() {
        return sampleRate * (bitsPerSample / 8) * channels;
    }

    @Override
    public String toString() {
        return "PCM" + bitsPerSample + "/" + sampleRate + "Hz/" + channels + "ch";
    }
}
