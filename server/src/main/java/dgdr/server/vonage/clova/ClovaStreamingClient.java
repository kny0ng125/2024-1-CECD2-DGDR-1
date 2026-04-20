package dgdr.server.vonage.clova;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.nbp.cdncp.nest.grpc.proto.v1.NestConfig;
import com.nbp.cdncp.nest.grpc.proto.v1.NestData;
import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import com.nbp.cdncp.nest.grpc.proto.v1.NestServiceGrpc;
import com.nbp.cdncp.nest.grpc.proto.v1.RequestType;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class ClovaStreamingClient {

    private static final String HOST = "clovaspeech-gw.ncloud.com";
    private static final int PORT = 50051;

    private static final String CONFIG_JSON = """
            {
              "transcription": { "language": "ko" },
              "semanticEpd": { "skipEmptyText": true }
            }
            """;

    private final String secretKey;
    private final ManagedChannel channel;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClovaStreamingClient(@Value("${clova.speech.secret-key}") String secretKey) {
        this.secretKey = secretKey;
        this.channel = NettyChannelBuilder.forAddress(HOST, PORT)
                .useTransportSecurity()
                .build();
    }

    /** WS 세션당 1회 호출. CONFIG 전송까지 마친 상태의 request StreamObserver 반환. */
    public StreamObserver<NestRequest> openStream(Consumer<SttResult> onResult,
                                                  Consumer<Throwable> onError) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + secretKey);

        NestServiceGrpc.NestServiceStub stub = NestServiceGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<NestResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(NestResponse response) {
                try {
                    JsonNode root = mapper.readTree(response.getContents());
                    JsonNode transcription = root.path("transcription");
                    if (transcription.isMissingNode()) return;

                    String text = transcription.path("text").asText("");
                    // CLOVA Nest: 최종 결과인 경우 "epd" 또는 "stable" 플래그가 표시됨 (스펙에 따라 조정)
                    boolean isFinal = root.has("epd") || transcription.path("stable").asBoolean(false);

                    onResult.accept(new SttResult(text, isFinal));
                } catch (Exception e) {
                    onError.accept(e);
                }
            }

            @Override
            public void onError(Throwable t) { onError.accept(t); }

            @Override
            public void onCompleted() { /* no-op */ }
        };

        StreamObserver<NestRequest> requestObserver = stub.recognize(responseObserver);

        // CONFIG 먼저 전송
        requestObserver.onNext(NestRequest.newBuilder()
                .setType(RequestType.CONFIG)
                .setConfig(NestConfig.newBuilder().setConfig(CONFIG_JSON).build())
                .build());

        return requestObserver;
    }

    /** PCM 청크 전송 (16kHz, 16bit, mono, little-endian) */
    public static void sendAudio(StreamObserver<NestRequest> stream, byte[] chunk) {
        NestRequest req = NestRequest.newBuilder()
                .setType(RequestType.DATA)
                .setData(NestData.newBuilder()
                        .setChunk(ByteString.copyFrom(chunk))
                        .build())
                .build();
        stream.onNext(req);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
