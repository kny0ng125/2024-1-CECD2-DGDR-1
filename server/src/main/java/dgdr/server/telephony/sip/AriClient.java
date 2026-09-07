package dgdr.server.telephony.sip;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Asterisk REST Interface 호출 (호 제어 액션 전용).
 *
 * <p>이벤트 수신은 {@link AriEventListener} 가 WebSocket 으로 따로 한다.
 * ARI 는 "액션은 REST, 이벤트는 WebSocket" 구조라 두 클래스로 나뉜다.
 *
 * <p>동기 블로킹으로 부르는 이유: 호출자는 요원의 수락 요청을 처리하는
 * HTTP 스레드이고, 게이트웨이 응답을 기다렸다가 성공/실패를 그대로
 * 돌려줘야 한다. 응답을 안 기다리면 "수락했는데 안 받아지는" 상태를
 * 화면이 알 수 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sip.ari.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AriClient {

    /** ARI 액션은 통화 중 조작이라 오래 기다릴 이유가 없다. */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final AudioSocketProperties properties;

    private WebClient client;

    private WebClient client() {
        if (client == null) {
            AudioSocketProperties.Ari cfg = properties.getAri();
            String basic = Base64.getEncoder().encodeToString(
                    (cfg.getUsername() + ":" + cfg.getPassword()).getBytes(StandardCharsets.UTF_8));
            client = WebClient.builder()
                    .baseUrl(cfg.getBaseUrl() + "/ari")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .build();
        }
        return client;
    }

    /**
     * 채널에 응답한다 (SIP 200 OK). 이 시점부터 미디어가 흐른다.
     *
     * <p>이미 응답된 채널에 다시 호출해도 Asterisk 는 오류를 내지 않으므로
     * 멱등성은 자연히 성립한다.
     */
    public void answer(String channelId) {
        client().post()
                .uri("/channels/{id}/answer", channelId)
                .retrieve()
                .toBodilessEntity()
                .block(TIMEOUT);
        log.info("[ari] answered channel {}", channelId);
    }

    /**
     * 채널을 끊는다.
     *
     * <p>{@code reason} 이 SIP 응답 코드를 결정한다. 거절(603 Decline)과
     * 정상 종료(BYE)는 발신자 단말에 다르게 표시되므로 구분해서 보낸다.
     *
     * <p>404 를 삼키는 이유: 요원이 종료를 누르는 것과 발신자가 먼저 끊는 것이
     * 경합할 수 있다. 이미 사라진 채널에 대한 종료 요청은 실패가 아니라
     * 원하던 결과가 이미 이루어진 것이다.
     */
    public void hangup(String channelId, String reason) {
        try {
            client().delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/channels/{id}")
                            .queryParam("reason", reason)
                            .build(channelId))
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
            log.info("[ari] hung up channel {} (reason={})", channelId, reason);
        } catch (WebClientResponseException.NotFound e) {
            log.debug("[ari] channel {} already gone", channelId);
        }
    }

    /** 링백 톤을 들려준다. 수락 전 발신자가 무음을 듣지 않게 한다. */
    public void ring(String channelId) {
        try {
            client().post()
                    .uri("/channels/{id}/ring", channelId)
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
        } catch (RuntimeException e) {
            // 링백 실패로 통화를 포기할 이유는 없다.
            log.debug("[ari] ring failed on {}: {}", channelId, e.toString());
        }
    }

    /**
     * 채널 변수를 설정한다. AudioSocket UUID 를 dialplan 에 전달하는 데 쓴다.
     *
     * <p>서버가 UUID 를 만들어 내려보내는 이유: 티켓을 서버가 소유해야
     * 수락 시점에 원자적으로 등록할 수 있다. dialplan 이 UUID 를 만들고
     * CURL 로 등록하는 방식은 ARI 를 쓰지 않는 배포에서만 필요하다.
     */
    public void setChannelVar(String channelId, String variable, String value) {
        client().post()
                .uri(uriBuilder -> uriBuilder
                        .path("/channels/{id}/variable")
                        .queryParam("variable", variable)
                        .queryParam("value", value)
                        .build(channelId))
                .retrieve()
                .toBodilessEntity()
                .block(TIMEOUT);
    }

    /**
     * 채널을 Stasis 앱에서 내보내 dialplan 의 지정 위치로 되돌린다.
     *
     * <p>이 호출이 있어야 AudioSocket 이 실행된다. Stasis 앱에 머무는 동안
     * dialplan 은 정지 상태이므로, 수락 전까지 미디어 연결이 생기지 않는
     * 것도 같은 이유다.
     */
    public void continueInDialplan(String channelId, String context, String extension, int priority) {
        client().post()
                .uri(uriBuilder -> uriBuilder
                        .path("/channels/{id}/continue")
                        .queryParam("context", context)
                        .queryParam("extension", extension)
                        .queryParam("priority", priority)
                        .build(channelId))
                .retrieve()
                .toBodilessEntity()
                .block(TIMEOUT);
    }

    /** 채널 변수 조회. dialplan 이 심어 둔 값(발신번호 등)을 읽는 데 쓴다. */
    public String getChannelVar(String channelId, String variable) {
        try {
            Map<?, ?> body = client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/channels/{id}/variable")
                            .queryParam("variable", variable)
                            .build(channelId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(TIMEOUT);
            return (body == null) ? null : (String) body.get("value");
        } catch (RuntimeException e) {
            log.debug("[ari] getVar {} on {} failed: {}", variable, channelId, e.toString());
            return null;
        }
    }

    /** 기동 시 연결 확인용. 실패하면 예외가 올라간다. */
    public void ping() {
        client().get()
                .uri("/asterisk/info")
                .retrieve()
                .toBodilessEntity()
                .block(TIMEOUT);
    }
}
