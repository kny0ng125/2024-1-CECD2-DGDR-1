package dgdr.server.telephony.vonage;

import dgdr.server.telephony.core.AudioFormat;
import dgdr.server.telephony.core.CallControl;
import dgdr.server.telephony.core.CallDescriptor;
import dgdr.server.telephony.core.CallOrchestrator;
import dgdr.server.telephony.core.LegHandle;
import dgdr.server.telephony.core.LegRole;
import dgdr.server.telephony.core.MediaSink;
import dgdr.server.user.infra.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Vonage Voice 통화 소스 어댑터.
 *
 * <p>NCCO {@code connect} 액션이 통화의 각 leg 를 이 WebSocket 으로 붙인다.
 * 오디오는 {@code audio/l16;rate=16000} (16kHz/16bit/mono PCM).
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>오디오 브리지, STT 스트림 관리, {@code Call} 엔티티 생성, 통화 종료 판단.
 * 전부 {@link CallOrchestrator} 로 갔다. 예전 구현은 이 넷을 전부 여기서 했고,
 * 그 결과 (1) 릴레이가 <b>열린 모든 세션</b>을 대상으로 해서 동시 통화 시
 * 다른 신고의 음성이 섞였고 (2) {@code call_started} 를 push 하지 않아
 * 실제 통화에서는 프런트가 callId 를 못 받았다.
 *
 * <p>지금 남은 책임은 셋뿐이다: 쿼리 파라미터 해석, {@link MediaSink} 구현,
 * 세션↔leg 매핑.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VonageAudioWebSocketHandler extends BinaryWebSocketHandler {

    /** Vonage WebSocket endpoint 가 보내는 포맷 ({@code audio/l16;rate=16000}). */
    private static final AudioFormat VONAGE_FORMAT = AudioFormat.PCM16_16K_MONO;

    /** 동시 전송 시 세션을 보호하는 데코레이터 파라미터. */
    private static final int SEND_TIME_LIMIT_MS = 10;
    private static final int SEND_BUFFER_LIMIT = 1024 * 1024;

    private final CallOrchestrator orchestrator;
    private final UserRepository userRepository;

    /** WebSocket session id → 코어 leg 핸들. */
    private final ConcurrentMap<String, LegHandle> legsBySession = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        Map<String, String> q = queryParams(session);
        String callerId = q.get("caller-id");
        String agentPhone = q.get("agent-phone");
        String conversationUuid = q.get("conversation-uuid");

        if (conversationUuid == null || conversationUuid.isBlank()) {
            log.error("[vonage] no conversation-uuid on WS {}; closing", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 요원 번호로 계정을 해석한다. 실패하면 통화를 열 수 없으므로 leg 를 거절한다.
        // 예전에는 조용히 Call 생성을 건너뛰어, 통화는 되는데 기록이 남지 않았다.
        String agentUserId = userRepository.findByPhone(agentPhone)
                .map(u -> u.getUserId())
                .orElse(null);
        if (agentUserId == null) {
            log.error("[vonage] unknown agent phone '{}' (conversation={}); closing {}",
                    agentPhone, conversationUuid, session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("unknown agent"));
            return;
        }

        // Vonage 는 두 leg 를 같은 WebSocket URL 로 붙이고, 요원 leg 는
        // caller-id 가 agent-phone 과 같게 온다. 이것이 역할 판별의 유일한 단서다.
        LegRole role = agentPhone.equals(callerId) ? LegRole.AGENT : LegRole.CALLER;
        String callerPhone = (role == LegRole.CALLER) ? callerId : null;

        ConcurrentWebSocketSessionDecorator safeSession =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);

        // Vonage 는 NCCO 의 answer 웹훅 시점에 이미 통화를 받은 상태로 leg 를
        // 붙인다. 즉 서버가 "받을지 말지"를 정할 여지가 없으므로 호 제어를
        // 지원하지 않는 소스로 선언하고, 코어는 이 통화를 곧바로 통화 중으로 연다.
        // 수락 흐름이 필요하면 NCCO 를 조건부로 반환하도록 바꿔야 한다.
        CallDescriptor callDesc = new CallDescriptor(
                conversationUuid, agentUserId, agentPhone, callerPhone);

        LegHandle leg = orchestrator.attachLeg(
                callDesc.leg(role, VONAGE_FORMAT),
                new WebSocketMediaSink(safeSession),
                CallControl.UNSUPPORTED);

        legsBySession.put(session.getId(), leg);
        log.info("[vonage] leg attached: role={} conversation={} callId={}",
                role, conversationUuid, leg.callId());
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        LegHandle leg = legsBySession.get(session.getId());
        if (leg == null) return;

        ByteBuffer payload = message.getPayload();
        byte[] chunk = new byte[payload.remaining()];
        payload.duplicate().get(chunk);
        leg.writeAudio(chunk);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        LegHandle leg = legsBySession.remove(session.getId());
        if (leg != null) {
            leg.close();   // 마지막 leg 였다면 코어가 통화 종료까지 처리한다.
        }
    }

    /**
     * Vonage WebSocket 세션으로 오디오를 되돌려 보내는 {@link MediaSink}.
     *
     * <p>전송 실패를 삼키는 이유: 브리지는 통화의 모든 leg 를 순회하며 호출되므로,
     * 한쪽이 이미 끊긴 상황에서 예외가 올라가면 나머지 leg 로의 릴레이까지 멈춘다.
     */
    private record WebSocketMediaSink(ConcurrentWebSocketSessionDecorator session) implements MediaSink {

        @Override
        public void send(byte[] pcm) {
            if (!session.isOpen()) return;
            try {
                session.sendMessage(new BinaryMessage(pcm));
            } catch (Exception e) {
                log.debug("[vonage] send failed on {}: {}", session.getId(), e.toString());
            }
        }

        @Override
        public boolean isOpen() {
            return session.isOpen();
        }
    }

    private static Map<String, String> queryParams(WebSocketSession session) {
        Map<String, String> result = new ConcurrentHashMap<>();
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return result;
        for (String pair : uri.getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}
