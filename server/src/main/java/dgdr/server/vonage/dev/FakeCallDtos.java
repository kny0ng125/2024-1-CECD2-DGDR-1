package dgdr.server.vonage.dev;

import java.time.LocalDateTime;

public final class FakeCallDtos {
    private FakeCallDtos() {}

    public record StartFakeCallRequest(String mode, String scenarioId, Double speedMultiplier) {}

    public record FakeCallStatus(
            Long callId,
            String scenarioId,
            LocalDateTime startedAt
    ) {}
}
