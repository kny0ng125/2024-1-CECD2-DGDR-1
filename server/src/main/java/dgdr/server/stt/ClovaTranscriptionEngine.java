package dgdr.server.stt;

import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import dgdr.server.telephony.core.AudioFormat;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * CLOVA Speech Nest (gRPC 양방향 스트리밍) 기반 {@link TranscriptionEngine}.
 *
 * <p>gRPC 프로토콜 세부사항은 {@link ClovaStreamingClient} 가 계속 담당하고,
 * 이 클래스는 그것을 코어가 아는 어휘({@link TranscriptionEngine.Stream})로
 * 옮기는 얇은 어댑터다. 코어가 {@code StreamObserver<NestRequest>} 같은
 * 벤더 타입을 보지 않게 하는 것이 목적이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClovaTranscriptionEngine implements TranscriptionEngine {

    private final ClovaStreamingClient client;

    @Override
    public String name() {
        return "clova-nest";
    }

    /** CLOVA Nest 는 16kHz/16bit/mono PCM 만 받는다. */
    @Override
    public AudioFormat requiredFormat() {
        return AudioFormat.PCM16_16K_MONO;
    }

    @Override
    public boolean supports(AudioFormat format) {
        return requiredFormat().equals(format);
    }

    @Override
    public Stream open(AudioFormat format, Consumer<SttResult> onResult, Consumer<Throwable> onError) {
        if (!supports(format)) {
            throw new IllegalArgumentException(
                    name() + " requires " + requiredFormat() + " but got " + format);
        }
        StreamObserver<NestRequest> upstream = client.openStream(onResult, onError);
        return new ClovaStream(upstream);
    }

    /**
     * gRPC request observer 를 감싼 스트림.
     *
     * <p>{@code closed} 플래그를 두는 이유: gRPC observer 는 {@code onCompleted()}
     * 이후 {@code onNext()} 를 호출하면 {@code IllegalStateException} 을 던진다.
     * leg 종료와 마지막 오디오 청크 도착이 경합할 수 있으므로
     * (WebSocket 종료 콜백과 IO 스레드가 서로 다른 스레드다) 닫힌 뒤의
     * 쓰기를 조용히 버린다.
     */
    private static final class ClovaStream implements Stream {

        private final StreamObserver<NestRequest> upstream;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ClovaStream(StreamObserver<NestRequest> upstream) {
            this.upstream = upstream;
        }

        @Override
        public void write(byte[] pcm) {
            if (closed.get() || pcm == null || pcm.length == 0) return;
            try {
                ClovaStreamingClient.sendAudio(upstream, pcm);
            } catch (RuntimeException e) {
                // 스트림이 이미 서버 쪽에서 끊긴 경우. 통화 자체를 끊을 이유는 없다.
                log.debug("[clova] dropped {} bytes: {}", pcm.length, e.toString());
                closed.set(true);
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                upstream.onCompleted();
            } catch (RuntimeException e) {
                log.debug("[clova] close failed: {}", e.toString());
            }
        }
    }
}
