package dgdr.server.global.utils;

import dgdr.server.global.domain.TokenType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;

@Slf4j
@Component
public class TokenProvider {
    /**
     * HS512 서명 키.
     *
     * <p>기본값을 두지 않는 이유가 두 가지 있다.
     * <ol>
     *   <li>저장소에 커밋된 기본 시크릿은 시크릿이 아니다. 값을 아는 사람은
     *       누구나 임의 사용자의 토큰을 위조할 수 있다.</li>
     *   <li>기존 기본값은 51바이트(408비트)라 HS512 요구치(512비트)에 미달해
     *       어차피 로그인 시점에 WeakKeyException 으로 실패했다. 설정 누락이
     *       기동 시점이 아니라 첫 로그인에서 드러나는 게 더 나쁘다.</li>
     * </ol>
     *
     * <p>설정하지 않으면 기동 시 placeholder 해석 실패로 즉시 멈춘다.
     * {@code application-example.yml} 을 복사해 값을 채울 것.
     */
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.accessToken.expiration:3600}")
    private Long accessTokenExpiration;

    @Value("${jwt.refreshToken.expiration:86400}")
    private Long refreshTokenExpiration;

    public String createAccessToken(String userId) {
        byte[] signingKey = secret.getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .signWith(Keys.hmacShaKeyFor(signingKey), SignatureAlgorithm.HS512)
                .setExpiration(Date.from(ZonedDateTime.now().plusMinutes(accessTokenExpiration).toInstant()))
                .setSubject(userId)
                .claim("type", TokenType.ACCESS)
                .compact();
    }

    public String createRefreshToken(String userId) {
        byte[] signingKey = secret.getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .signWith(Keys.hmacShaKeyFor(signingKey), SignatureAlgorithm.HS512)
                .setExpiration(Date.from(ZonedDateTime.now().plusDays(refreshTokenExpiration).toInstant()))
                .setSubject(userId)
                .claim("type", TokenType.REFRESH)
                .compact();
    }

    public String getUserIdByToken(String token) {
        byte[] signingKey = secret.getBytes(StandardCharsets.UTF_8);

        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            byte[] signingKey = secret.getBytes(StandardCharsets.UTF_8);
            Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getTokenTypeByToken(String token) {
        byte[] signingKey = secret.getBytes(StandardCharsets.UTF_8);

        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("type")
                .toString();
    }


}
