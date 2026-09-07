package dgdr.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링 컨텍스트가 정상적으로 뜨는지 확인한다.
 *
 * <p>{@code test} 프로파일은 H2 인메모리 DB 와 더미 외부 연동 값을 쓴다.
 * 실제 MySQL 이나 개발자 로컬의 application.yml 이 없어도 통과해야 한다.
 *
 * <p>{@code dev} 를 함께 켜는 이유: {@code VonageService} 는 생성자에서
 * 실제 private key 파일을 읽어 클라이언트를 만들기 때문에 키가 없으면
 * 컨텍스트 로딩이 실패한다. 이 빈은 {@code @Profile("!dev")} 이므로
 * dev 프로파일에서는 fake 구현으로 대체된다.
 *
 * <p>{@code webEnvironment = RANDOM_PORT} 인 이유: {@code WebSocketConfig} 의
 * {@code ServletServerContainerFactoryBean} 은 초기화 시점에 ServletContext 에서
 * {@code jakarta.websocket.server.ServerContainer} 를 찾는다. 기본값인 MOCK
 * 환경에는 실제 컨테이너가 없어 컨텍스트 로딩이 실패하므로, 임베디드 Tomcat 을
 * 임의 포트로 띄운다.
 */
@ActiveProfiles({"test", "dev"})
@SpringBootTest(
        classes = ServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
