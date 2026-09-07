package dgdr.server.telephony.vonage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Vonage 오디오 WebSocket 엔드포인트 등록.
 *
 * <p>{@code setAllowedOrigins("*")} 인 이유: 이 엔드포인트의 접속 주체는
 * 브라우저가 아니라 Vonage 미디어 서버이므로 Origin 헤더가 없다.
 * 브라우저에서 접근할 엔드포인트가 아니며, 인증은 NCCO 가 넘겨준
 * {@code conversation-uuid} 로 대체된다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class VonageWebSocketConfig implements WebSocketConfigurer {

    private final VonageAudioWebSocketHandler vonageAudioWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(vonageAudioWebSocketHandler, "/ws/audio")
                .setAllowedOrigins("*");
    }

    /**
     * 통화 오디오는 프레임이 크므로 기본 버퍼(8KB)로는 부족하다.
     *
     * <p>주의: 이 빈은 초기화 시점에 ServletContext 에서
     * {@code jakarta.websocket.server.ServerContainer} 를 찾는다. 테스트에서
     * MOCK 웹 환경을 쓰면 컨테이너가 없어 컨텍스트 로딩이 실패하므로
     * {@code webEnvironment = RANDOM_PORT} 가 필요하다.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        return container;
    }
}
