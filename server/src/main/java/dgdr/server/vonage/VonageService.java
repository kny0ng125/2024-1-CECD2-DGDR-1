package dgdr.server.vonage;

import com.vonage.client.VonageClient;
import com.vonage.client.voice.ncco.ConnectAction;
import com.vonage.client.voice.ncco.ConversationAction;
import com.vonage.client.voice.ncco.Ncco;
import com.vonage.client.voice.ncco.WebSocketEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!dev")
public class VonageService {
    private final VonageClient vonageClient;
    private final String publicUrl;
    private static final String ConferenceName = "Emergency Conference";

    public VonageService(@Value("${vonage.apiKey}") String apiKey,
                         @Value("${vonage.applicationId}") String applicationId,
                         @Value("${vonage.privateKeyPath}") String privateKeyPath,
                         @Value("${vonage.apiSecret}") String apiSecret,
                         @Value("${server.public-url}") String publicUrl) {

        this.publicUrl = publicUrl;
        this.vonageClient = VonageClient.builder()
                .applicationId(applicationId)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .privateKeyPath(privateKeyPath)
                .build();
    }

    // 통화 및 WebSocket 연결
    public Ncco createOrJoinConversationWithWebSocket(String callerId,
                                                      String agentPhone,
                                                      String conversationUuid) {
        String websocketUri = String.format(
                "wss://%s/ws/audio?caller-id=%s&agent-phone=%s&conversation-uuid=%s",
                publicUrl, callerId, agentPhone, conversationUuid
        );
        ConversationAction conversationAction = ConversationAction.builder(ConferenceName)
                .startOnEnter(true)
                .build();

        WebSocketEndpoint websocketEndpoint = WebSocketEndpoint
                .builder(websocketUri, "audio/l16;rate=16000")
                .build();

        ConnectAction connectAction = ConnectAction.builder()
                .endpoint(websocketEndpoint)
                .from(callerId)
                .build();

        return new Ncco(conversationAction, connectAction);
    }
}
