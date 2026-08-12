package dgdr.server.vonage.global.config;

import dgdr.server.vonage.global.security.JwtTokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    private final JwtTokenAuthFilter jwtTokenAuthFilter;

    public SecurityConfig(JwtTokenAuthFilter jwtTokenAuthFilter) {
        this.jwtTokenAuthFilter = jwtTokenAuthFilter;
    }

    /**
     * 비밀번호 해시.
     *
     * <p>「개인정보의 안전성 확보조치 기준」은 비밀번호를 복호화되지 않도록
     * 일방향 암호화하여 저장할 것을 요구한다. BCrypt 는 salt 를 해시에 포함하며
     * 결과가 항상 60자이므로 users.password 컬럼도 VARCHAR(60) 이다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers("/api/v1/user/auth/**", "/ws/*").permitAll() // '/api/v1/user/auth' 하위 경로 허용
                                .anyRequest().authenticated() // 다른 모든 요청은 인증 필요
                )
                .addFilterBefore(jwtTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // 기본값은 미인증 요청에도 403 을 내려준다. 프런트의 authFetch 는
                // 401 을 만료 신호로 삼아 로그아웃 → 로그인 화면 리다이렉트를 수행하므로
                // 미인증(=인증 정보 없음)은 401 로 구분해 준다. 403 은 권한 부족 전용으로 남긴다.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(headerConfig ->
                        headerConfig.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                );
        return http.build();
    }
}
