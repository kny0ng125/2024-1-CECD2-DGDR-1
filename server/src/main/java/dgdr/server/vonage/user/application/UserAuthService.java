package dgdr.server.vonage.user.application;

import dgdr.server.vonage.global.domain.TokenResponse;
import dgdr.server.vonage.global.utils.TokenProvider;
import dgdr.server.vonage.user.domain.User;
import dgdr.server.vonage.user.infra.UserRepository;
import dgdr.server.vonage.user.domain.dto.LoginReq;
import dgdr.server.vonage.user.domain.dto.UserSignUpReq;
import dgdr.server.vonage.privacy.AccessAction;
import dgdr.server.vonage.privacy.AccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AccessLogService accessLogService;

    public void signUp(UserSignUpReq userSignUpReq) {
        // 요청 객체를 통째로 로깅하면 평문 비밀번호가 로그에 남는다. 아이디만 남긴다.
        log.info("[UserAuthService] signUp id={}", userSignUpReq.id());
        userRepository.save(
                userSignUpReq.toEntity(passwordEncoder.encode(userSignUpReq.password())));
    }

    public boolean checkId(String id) {
        return userRepository.existsById(id);
    }

    public TokenResponse login(LoginReq loginReq) {
        log.info("[UserAuthService] login id={}", loginReq.id());

        User user = userRepository.findById(loginReq.id())
                .orElseThrow(() -> {
                    accessLogService.record(AccessAction.LOGIN_FAILURE, "USER", loginReq.id());
                    // 존재하지 않는 아이디와 비밀번호 불일치를 구분해서 알려주면
                    // 계정 존재 여부가 노출되므로 같은 메시지를 쓴다.
                    return new IllegalArgumentException("Invalid credentials");
                });

        if (!passwordEncoder.matches(loginReq.password(), user.getPassword())) {
            accessLogService.record(AccessAction.LOGIN_FAILURE, "USER", loginReq.id());
            throw new IllegalArgumentException("Invalid credentials");
        }

        accessLogService.record(AccessAction.LOGIN_SUCCESS, "USER", user.getUserId());

        return TokenResponse.builder()
                .accessToken(tokenProvider.createAccessToken(user.getUserId()))
                .refreshToken(tokenProvider.createRefreshToken(user.getUserId()))
                .build();
    }
}
