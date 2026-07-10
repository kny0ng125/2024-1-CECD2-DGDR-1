package dgdr.server.vonage;

import com.vonage.client.voice.ncco.Ncco;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Profile("!dev")
public class VonageController {
    private final VonageService vonageService;

    @PostMapping("/answer")
    public Ncco answerWebhook(@RequestBody AnswerWebhookPayload payload) {
        return vonageService.createOrJoinConversationWithWebSocket(
                payload.getFrom(),          // caller phone
                payload.getTo(),            // agent's dedicated Vonage number
                payload.getConversation_uuid()
        );
    }

    @PostMapping("/event")
    public void eventWebhook(@RequestBody String payload) {
        System.out.println("Event: " + payload);
    }
}
