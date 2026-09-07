package dgdr.server.telephony.scenario;

import dgdr.server.telephony.scenario.ScenarioCallSource.PlaybackStatus;
import dgdr.server.user.domain.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 시나리오 재생 제어 (개발 전용).
 *
 * <p>경로는 프런트 호환을 위해 {@code /api/v1/dev/fake-call} 을 유지한다.
 * {@code dev} 프로파일 + {@code dev.fake-call.enabled=true} 두 조건이 모두
 * 만족될 때만 빈이 만들어지므로 운영 빌드에는 라우트 자체가 존재하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/dev/fake-call")
@Profile("dev")
@ConditionalOnProperty(name = "dev.fake-call.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioCallSource scenarioCallSource;

    /**
     * @param scenarioId {@code emergency-119} | {@code overlap-test}
     * @param speedMultiplier 재생 배속. 2.0 이면 절반 시간에 끝난다.
     */
    /**
     * @param ring {@code true} 면 벨 울림(OFFERED)으로 시작해 수락 UI 를 테스트한다.
     *             생략하면 {@code false} — 곧바로 통화 중으로 시작해 자막이 바로 흐른다.
     */
    public record StartRequest(String scenarioId, Double speedMultiplier, Boolean ring) {}

    public record ScenarioSummary(String id, int lineCount, long durationMs) {}

    /** 재생 가능한 시나리오 목록. 프런트 셀렉트 박스를 서버 기준으로 채운다. */
    @GetMapping("/scenarios")
    public ResponseEntity<List<ScenarioSummary>> scenarios() {
        return ResponseEntity.ok(Scenarios.all().stream()
                .map(s -> new ScenarioSummary(s.id(), s.lines().size(), s.totalDurationMs()))
                .toList());
    }

    @PostMapping("/start")
    public ResponseEntity<PlaybackStatus> start(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) StartRequest req
    ) {
        String scenarioId = (req == null) ? null : req.scenarioId();
        double speed = (req == null || req.speedMultiplier() == null) ? 1.0 : req.speedMultiplier();
        boolean ring = (req != null && Boolean.TRUE.equals(req.ring()));
        return ResponseEntity.ok(
                scenarioCallSource.start(principal.getUsername(), scenarioId, speed, ring));
    }

    @PostMapping("/stop/{callId}")
    public ResponseEntity<Void> stop(@PathVariable Long callId) {
        scenarioCallSource.stop(callId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active")
    public ResponseEntity<List<PlaybackStatus>> active() {
        return ResponseEntity.ok(scenarioCallSource.active());
    }
}
