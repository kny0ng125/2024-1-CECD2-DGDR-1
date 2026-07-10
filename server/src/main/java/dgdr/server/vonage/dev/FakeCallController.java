package dgdr.server.vonage.dev;

import dgdr.server.vonage.dev.FakeCallDtos.FakeCallStatus;
import dgdr.server.vonage.dev.FakeCallDtos.StartFakeCallRequest;
import dgdr.server.vonage.user.domain.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev/fake-call")
@Profile("dev")
@ConditionalOnProperty(name = "dev.fake-call.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FakeCallController {

    /** 빈 이름("transcript" / "audio") → 드라이버. Spring이 자동 주입 */
    private final Map<String, FakeCallDriver> drivers;

    @PostMapping("/start")
    public ResponseEntity<FakeCallStatus> start(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestBody(required = false) StartFakeCallRequest req
    ) {
        String mode       = (req == null || req.mode() == null) ? "transcript" : req.mode();
        String scenarioId = (req == null || req.scenarioId() == null) ? "emergency-119" : req.scenarioId();
        double speed      = (req == null || req.speedMultiplier() == null) ? 1.0 : req.speedMultiplier();

        FakeCallDriver driver = drivers.get(mode);
        if (driver == null) {
            throw new IllegalArgumentException("Unknown fake-call mode: " + mode);
        }
        return ResponseEntity.ok(driver.start(pd.getUsername(), scenarioId, speed));
    }

    @PostMapping("/stop/{callId}")
    public ResponseEntity<Void> stop(@PathVariable Long callId,
                                     @AuthenticationPrincipal PrincipalDetails pd) {
        // 어느 드라이버가 소유하는지 모르니 전부에 시도 (미소유 드라이버는 no-op)
        drivers.values().forEach(d -> d.stop(callId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active")
    public ResponseEntity<List<FakeCallStatus>> active(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        // 모든 드라이버의 active 합침
        List<FakeCallStatus> all = drivers.values().stream()
                .flatMap(d -> d.active().stream())
                .toList();
        return ResponseEntity.ok(all);
    }
}