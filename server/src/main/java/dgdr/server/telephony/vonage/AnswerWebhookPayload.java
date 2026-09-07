package dgdr.server.telephony.vonage;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnswerWebhookPayload {
    private String from;
    private String to;
    private String uuid;
    private String conversation_uuid;
    private String region_url;
}