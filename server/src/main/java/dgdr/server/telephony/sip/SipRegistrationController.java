package dgdr.server.telephony.sip;

import dgdr.server.telephony.core.LegRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * SIP 게이트웨이가 AudioSocket 을 열기 직전에 통화 메타데이터를 등록하는 창구.
 *
 * <p>AudioSocket 프로토콜에는 통화 UUID 말고 아무 정보도 없다
 * ({@link SipCallRegistry} 참고). 이 엔드포인트가 그 공백을 메운다.
 *
 * <h2>인증</h2>
 * <p>호출자가 사람이 아니라 게이트웨이이므로 JWT 를 쓸 수 없다.
 * 공유 비밀을 헤더로 받되 <b>상수 시간 비교</b>를 한다. 문자열
 * {@code equals} 는 첫 불일치에서 빠져나오므로 응답 시간 차이로
 * 비밀을 한 바이트씩 알아낼 수 있다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sip")
@ConditionalOnProperty(name = "sip.audiosocket.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SipRegistrationController {

    private static final String SECRET_HEADER = "X-Sip-Gateway-Secret";

    private final SipCallRegistry registry;
    private final AudioSocketProperties properties;

    /**
     * @param audioSocketUuid AudioSocket 이 UUID 프레임으로 보낼 값
     * @param providerCallKey 같은 통화의 두 leg 가 공유하는 키. 보통 SIP Call-ID.
     * @param role            {@code AGENT} | {@code CALLER}
     */
    public record RegisterRequest(
            String audioSocketUuid,
            String providerCallKey,
            String role,
            String agentPhone,
            String callerPhone
    ) {
        boolean isComplete() {
            return notBlank(audioSocketUuid) && notBlank(providerCallKey)
                    && notBlank(role) && notBlank(agentPhone);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    @PostMapping("/registrations")
    public ResponseEntity<Void> register(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody RegisterRequest req
    ) {
        String expected = properties.getGateway().getSharedSecret();
        if (expected == null || expected.isBlank()) {
            // 설정 누락이 무인증 개방으로 이어지지 않게 한다.
            log.error("[sip] registration rejected — sip.gateway.shared-secret is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!constantTimeEquals(expected, secret)) {
            log.warn("[sip] registration rejected — bad gateway secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!req.isComplete()) {
            return ResponseEntity.badRequest().build();
        }

        LegRole role;
        try {
            role = LegRole.valueOf(req.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        registry.register(new SipCallRegistry.Ticket(
                req.audioSocketUuid(),
                req.providerCallKey(),
                role,
                req.agentPhone(),
                req.callerPhone(),
                Instant.now()
        ));
        return ResponseEntity.accepted().build();
    }

    /**
     * 길이 차이까지 감추지는 못하지만, 내용에 대한 타이밍 누출은 막는다.
     * 비밀 길이는 공개해도 무방한 정보로 본다.
     */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
