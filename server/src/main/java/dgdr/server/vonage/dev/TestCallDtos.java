package dgdr.server.vonage.dev;

import java.time.LocalDateTime;

public final class TestCallDtos {
    private TestCallDtos() {}

    public record StartFakeCallRequest(
            String scenarioId,
            Double speedMultiplier
    ) {}

    public record FakeCallStatus(
            Long callId,
            String scenarioId,
            LocalDateTime startedAt
    ) {}
}
