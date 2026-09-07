package dgdr.server.telephony.core;

import dgdr.server.stt.SttResult;
import dgdr.server.stt.TranscriptionEngine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 결정론적 {@link TranscriptionEngine} 테스트 더블.
 *
 * <p>{@code TranscriptionEngine} 인터페이스의 존재 이유가 이것이다.
 * 오케스트레이터의 책임(leg 묶기, 오디오 브리지, 화자 태깅, 종료 처리)은
 * 음성인식과 아무 상관이 없는데, 이 시임이 없으면 검증하려 할 때마다
 * CLOVA 자격증명과 네트워크가 필요해진다.
 *
 * <p>스트림에 들어온 바이트를 기록하고, 테스트가 원할 때
 * {@link Stream#emit} 으로 인식 결과를 흘려보낸다.
 */
public final class RecordingTranscriptionEngine implements TranscriptionEngine {

    /** 열린 순서대로의 스트림. 테스트가 leg 별 스트림을 지목할 수 있게 한다. */
    private final List<Stream> streams = new CopyOnWriteArrayList<>();

    private final AudioFormat required;

    public RecordingTranscriptionEngine() {
        this(AudioFormat.PCM16_16K_MONO);
    }

    public RecordingTranscriptionEngine(AudioFormat required) {
        this.required = required;
    }

    @Override public String name() { return "recording-test-engine"; }
    @Override public AudioFormat requiredFormat() { return required; }
    @Override public boolean supports(AudioFormat format) { return required.equals(format); }

    @Override
    public Stream open(AudioFormat format, Consumer<SttResult> onResult, Consumer<Throwable> onError) {
        Stream s = new Stream(onResult);
        streams.add(s);
        return s;
    }

    public List<Stream> streams() {
        return streams;
    }

    public Stream stream(int index) {
        return streams.get(index);
    }

    public int openStreamCount() {
        return (int) streams.stream().filter(s -> !s.closed).count();
    }

    /** 기록형 스트림. */
    public static final class Stream implements TranscriptionEngine.Stream {

        private final Consumer<SttResult> onResult;
        private final List<byte[]> written = new CopyOnWriteArrayList<>();
        private volatile boolean closed;

        private Stream(Consumer<SttResult> onResult) {
            this.onResult = onResult;
        }

        @Override
        public void write(byte[] pcm) {
            if (closed) return;
            written.add(pcm);
        }

        @Override
        public void close() {
            closed = true;
        }

        /** 이 스트림에 들어온 총 바이트 수. 리샘플링 검증에 쓴다. */
        public int totalBytesWritten() {
            return written.stream().mapToInt(b -> b.length).sum();
        }

        public List<byte[]> written() {
            return written;
        }

        public boolean isClosed() {
            return closed;
        }

        /** 이 leg 에서 인식 결과가 나온 것처럼 흘려보낸다. */
        public void emit(String text, boolean isFinal) {
            onResult.accept(new SttResult(text, isFinal));
        }
    }
}
