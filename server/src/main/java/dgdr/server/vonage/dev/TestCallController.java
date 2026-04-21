package dgdr.server.vonage.dev;

import dgdr.server.vonage.dev.TestCallDtos.FakeCallStatus;
import dgdr.server.vonage.dev.TestCallDtos.StartFakeCallRequest;
import dgdr.server.vonage.user.domain.PrincipalDetails;
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

@RestController
@RequestMapping("/api/v1/dev/fake-call")
@Profile("dev")
@ConditionalOnProperty(name = "dev.fake-call.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TestCallController {

    private final TestCallSimulator simulator;

    @PostMapping("/start")
    public ResponseEntity<FakeCallStatus> start(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestBody(required = false) StartFakeCallRequest req
    ) {
        String scenarioId = (req == null || req.scenarioId() == null) ? "emergency-119" : req.scenarioId();
        double speed = (req == null || req.speedMultiplier() == null) ? 1.0 : req.speedMultiplier();
        FakeCallStatus status = simulator.start(pd.getUsername(), scenarioId, speed);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/stop/{callId}")
    public ResponseEntity<Void> stop(@PathVariable Long callId,
                                     @AuthenticationPrincipal PrincipalDetails pd) {
        simulator.stop(callId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active")
    public ResponseEntity<List<FakeCallStatus>> active(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return ResponseEntity.ok(simulator.active());
    }
}
