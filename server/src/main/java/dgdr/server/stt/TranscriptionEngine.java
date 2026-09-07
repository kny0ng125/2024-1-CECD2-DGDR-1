package dgdr.server.stt;

import dgdr.server.telephony.core.AudioFormat;

import java.util.function.Consumer;

/**
 * 스트리밍 음성인식 엔진.
 *
 * <p>프로덕션 구현체는 {@link ClovaTranscriptionEngine} 하나다. 구현이 하나인데
 * 인터페이스를 두는 이유는 <b>테스트 시임</b>이다. 오케스트레이터의 책임
 * (leg 묶기, 브리지, 화자 태깅, 종료 처리)을 검증하려면 CLOVA 자격증명도
 * 네트워크도 없이 컨텍스트를 띄울 수 있어야 하고, 그러려면 여기가 끊겨야 한다.
 * 실제로 {@code src/test} 의 결정론적 구현이 두 번째 구현체 역할을 한다.
 *
 * <p>스트림은 <b>leg 당 하나</b>다. CLOVA Nest 는 한 스트림에 한 화자를
 * 가정하므로, 두 화자의 오디오를 한 스트림에 섞으면 화자 분리가 불가능해진다.
 */
public interface TranscriptionEngine {

    /** 로그·진단용 엔진 이름. */
    String name();

    /**
     * 이 엔진이 해당 포맷을 그대로 받을 수 있는지.
     *
     * <p>{@code false} 면 코어가 리샘플링을 끼워 넣는다. AudioSocket 기본
     * 포맷이 8kHz 인데 CLOVA 는 16kHz 를 요구하는 상황이 실제로 이 분기를 탄다.
     */
    boolean supports(AudioFormat format);

    /** 이 엔진이 요구하는 포맷. 코어가 리샘플링 목표로 삼는다. */
    AudioFormat requiredFormat();

    /**
     * 인식 스트림을 연다. 반환 시점에 이미 세션 설정(config)까지 끝난 상태여야 한다.
     *
     * @param format   호출자가 {@link #write} 로 밀어 넣을 PCM 의 포맷.
     *                 {@link #supports}를 만족해야 한다.
     * @param onResult 부분/최종 인식 결과 콜백. 엔진 스레드에서 호출된다.
     * @param onError  스트림 오류 콜백
     */
    Stream open(AudioFormat format, Consumer<SttResult> onResult, Consumer<Throwable> onError);

    /** 열려 있는 인식 스트림 하나. */
    interface Stream extends AutoCloseable {

        /** PCM 청크를 밀어 넣는다. 닫힌 스트림에 대한 호출은 무시되어야 한다. */
        void write(byte[] pcm);

        /** 스트림을 정상 종료한다. 여러 번 호출해도 안전해야 한다(멱등). */
        @Override
        void close();
    }
}
