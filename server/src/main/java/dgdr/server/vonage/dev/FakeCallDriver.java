package dgdr.server.vonage.dev;

import dgdr.server.vonage.dev.FakeCallDtos.FakeCallStatus;
import java.util.List;

public interface FakeCallDriver {
    FakeCallStatus start(String userId, String scenarioId, double speed);
    void stop(Long callId);
    List<FakeCallStatus> active();
}