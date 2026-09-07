package dgdr.server.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 학습한 KorDPR 리트리버를 SageMaker 엔드포인트로 호출하는 구현.
 *
 * <p>{@code manual.retriever=sagemaker} 일 때 활성화된다.
 *
 * <h2>응답 형식</h2>
 * <pre>
 *   { "passage0": "병명", "script0": {"임상적 특징": "...", "환자평가 필수항목": "..."},
 *     "sim0": 114.2, ... "passage5" 까지 }
 * </pre>
 * 인덱스가 고정 6개인 것은 추론 스크립트의 {@code k=6} 때문이다.
 * 여기서는 없는 인덱스를 만나면 조용히 멈춘다 — k 가 바뀌어도 깨지지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "manual.retriever", havingValue = "sagemaker")
public class SageMakerManualRetriever implements ManualRetriever {

    /** 추론 스크립트가 반환하는 최대 후보 수. */
    private static final int MAX_PASSAGES = 16;

    private final SageMakerRuntimeClient client;
    private final String endpointName;
    private final ObjectMapper mapper = new ObjectMapper();

    public SageMakerManualRetriever(
            @Value("${aws.access-key}") String accessKey,
            @Value("${aws.secret-key}") String secretKey,
            @Value("${aws.region}") String region,
            @Value("${aws.sagemaker.endpoint-name}") String endpointName) {
        this.endpointName = endpointName;
        this.client = SageMakerRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Override
    public String name() {
        return "kordpr-sagemaker";
    }

    @Override
    public List<ManualCandidate> retrieve(String conversationText, int topK) {
        // 예전 구현은 String.format("{\"text\": \"%s\"}", ...) 로 JSON 을 손으로
        // 조립했다. 전사본에 따옴표나 개행이 하나만 들어가도 요청이 깨진다.
        // 신고자가 실제로 하는 말에 그런 문자가 없으리라는 보장은 없다.
        ObjectNode payload = mapper.createObjectNode().put("text", conversationText);

        InvokeEndpointResponse response;
        try {
            response = client.invokeEndpoint(InvokeEndpointRequest.builder()
                    .endpointName(endpointName)
                    .contentType("application/json")
                    .body(SdkBytes.fromString(payload.toString(), StandardCharsets.UTF_8))
                    .build());
        } catch (RuntimeException e) {
            // 매뉴얼이 안 뜨는 것은 통화를 중단할 이유가 아니다.
            // 상위에서 빈 목록으로 처리되어 화면에 "결과 없음"이 나온다.
            log.error("[manual/sagemaker] invoke failed: {}", e.toString());
            return List.of();
        }

        return parse(response.body().asUtf8String(), topK);
    }

    private List<ManualCandidate> parse(String body, int topK) {
        List<ManualCandidate> result = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(body);
            for (int i = 0; i < MAX_PASSAGES && result.size() < topK; i++) {
                JsonNode passage = root.get("passage" + i);
                JsonNode script = root.get("script" + i);
                JsonNode sim = root.get("sim" + i);
                if (passage == null || script == null || sim == null) break;

                result.add(new ManualCandidate(
                        passage.asText(),
                        script.path("임상적 특징").asText(""),
                        script.path("환자평가 필수항목").asText(""),
                        sim.asDouble()));
            }
        } catch (Exception e) {
            log.error("[manual/sagemaker] response parse failed: {}", e.toString());
            return List.of();
        }
        return result;
    }
}
