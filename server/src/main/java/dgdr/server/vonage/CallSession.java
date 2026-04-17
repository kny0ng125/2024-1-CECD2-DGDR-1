package dgdr.server.vonage;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class CallSession {
    private Call call;
    private String callerPhone;
    private String agentUserId;
}
