package dgdr.server.vonage;

import com.vonage.client.voice.ncco.Ncco;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Profile("!dev")
public class VonageController {
    private final VonageService vonageService;
    private final ManualService manualService;

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

    @GetMapping("/manual/{callId}")
    public ResponseEntity<Map<String, Object>> sendManual(@PathVariable Long callId) {
        Map<String, Object> response = manualService.getManual(callId);
        return ResponseEntity.ok(response);
    }
}