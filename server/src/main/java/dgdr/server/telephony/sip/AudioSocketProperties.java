package dgdr.server.telephony.sip;

import dgdr.server.telephony.core.AudioFormat;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SIP 게이트웨이(AudioSocket) 연동 설정.
 *
 * <pre>
 * sip:
 *   audiosocket:
 *     enabled: true
 *     port: 9092
 *     bind-address: 127.0.0.1   # 게이트웨이와 같은 호스트/사설망에서만 접근
 *     codec: slin               # slin(8kHz) | slin16(16kHz)
 *     max-connections: 64
 *   gateway:
 *     shared-secret: ...        # 통화 등록 API 인증용
 *     registration-ttl: 60s
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sip")
public class AudioSocketProperties {

    private AudioSocket audiosocket = new AudioSocket();
    private Gateway gateway = new Gateway();
    private Ari ari = new Ari();

    /**
     * Asterisk REST Interface — 호 제어 전용.
     *
     * <p>미디어(AudioSocket)와 <b>별도 설정</b>인 것이 핵심이다. 실전 CTI 가
     * 콜 컨트롤 경로와 미디어 경로를 분리하듯, 이 둘은 서로 다른 포트·자격증명·
     * 실패 양상을 가진다. ARI 가 끊겨도 이미 붙은 통화의 오디오와 전사는
     * 계속 흐른다 — 새 통화를 받거나 끊지 못할 뿐이다.
     */
    @Getter
    @Setter
    public static class Ari {

        /** 꺼져 있으면 수락/종료를 지원하지 않는다(통화는 dialplan 이 알아서 받음). */
        private boolean enabled = false;

        /** Asterisk HTTP 인터페이스. 기본 8088. */
        private String baseUrl = "http://127.0.0.1:8088";

        /** {@code ari.conf} 의 사용자. */
        private String username = "";
        private String password = "";

        /**
         * Stasis 애플리케이션 이름. dialplan 의 {@code Stasis(<name>)} 와 일치해야 한다.
         * 이 이름으로 이벤트 WebSocket 을 구독한다.
         */
        private String appName = "dgdr";

        /**
         * 요원이 수락하지 않은 채 이 시간이 지나면 통화를 끊는다.
         *
         * <p>무한정 울리게 두면 Asterisk 채널과 서버의 LiveCall 이 계속 쌓인다.
         * 실제 상황실이라면 다른 요원에게 넘기는 것이 맞지만, 이 시스템은
         * 큐잉·분배를 다루지 않으므로 끊는다.
         */
        private Duration ringTimeout = Duration.ofSeconds(45);

        /** 이벤트 WebSocket 이 끊겼을 때 재연결 간격. */
        private Duration reconnectInterval = Duration.ofSeconds(5);

        /**
         * 수락 후 채널을 되돌려 보낼 dialplan 위치.
         *
         * <p>Stasis 앱에 머무는 동안에는 dialplan 이 진행되지 않으므로,
         * AudioSocket 을 실행시키려면 여기로 continue 시켜야 한다.
         * {@code extensions.conf} 에 이 컨텍스트·익스텐션이 있어야 한다.
         */
        private String mediaContext = "dgdr-media";
        private String mediaExtension = "start";
    }

    @Getter
    @Setter
    public static class AudioSocket {

        /** 꺼져 있으면 TCP 리스너를 열지 않는다. 기본 off — SIP 게이트웨이가 없는 환경이 정상. */
        private boolean enabled = false;

        private int port = 9092;

        /**
         * 기본값이 loopback 인 이유: AudioSocket 에는 인증이 없다.
         * 이 포트에 붙을 수 있는 자는 임의의 통화를 열고 오디오를 주입할 수 있으므로
         * 공인 IP 에 바인딩해서는 안 된다. 게이트웨이가 다른 호스트라면
         * 사설망 주소로 바꾸고 방화벽으로 출처를 제한할 것.
         */
        private String bindAddress = "127.0.0.1";

        /** {@code slin}(8kHz) 또는 {@code slin16}(16kHz). Asterisk dialplan 설정과 일치해야 한다. */
        private String codec = "slin";

        /** 동시 통화 leg 상한. 스레드 per 연결이므로 상한이 필요하다. */
        private int maxConnections = 64;

        /** 게이트웨이가 UUID 프레임을 보내기까지 기다리는 시간. */
        private Duration handshakeTimeout = Duration.ofSeconds(5);

        public AudioFormat resolveFormat() {
            return switch (codec.toLowerCase()) {
                case "slin", "slin8", "pcm8" -> AudioFormat.PCM16_8K_MONO;
                case "slin16", "pcm16" -> AudioFormat.PCM16_16K_MONO;
                default -> throw new IllegalArgumentException(
                        "Unsupported AudioSocket codec: " + codec + " (use slin or slin16)");
            };
        }
    }

    @Getter
    @Setter
    public static class Gateway {

        /**
         * 통화 등록 API 인증용 공유 비밀.
         *
         * <p>이 API 는 사람이 아니라 게이트웨이가 부르므로 JWT 로 보호할 수 없다.
         * 값이 비어 있으면 등록 엔드포인트는 모든 요청을 거절한다 —
         * 설정 누락이 곧 무인증 개방이 되는 것보다 낫다.
         */
        private String sharedSecret = "";

        /**
         * 등록 후 AudioSocket 연결이 오기까지 티켓을 유지하는 시간.
         * 지나면 버린다. 게이트웨이가 등록만 하고 연결하지 않는 경우
         * (통화가 응답 전에 끊긴 경우 등) 메모리가 새는 것을 막는다.
         */
        private Duration registrationTtl = Duration.ofSeconds(60);
    }
}
