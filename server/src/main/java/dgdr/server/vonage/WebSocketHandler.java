package dgdr.server.vonage;

import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import dgdr.server.vonage.clova.CallTranscriptCache;
import dgdr.server.vonage.clova.ClovaStreamingClient;
import dgdr.server.vonage.clova.SttResult;
import dgdr.server.vonage.clova.TranscriptEntry;
import dgdr.server.vonage.user.infra.UserRepository;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WebSocketHandler extends BinaryWebSocketHandler {

    private final ConcurrentMap<String, ConcurrentWebSocketSessionDecorator> sessionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CallSession> sessionDataMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StreamObserver<NestRequest>> sessionGrpcMap = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final CallRecordRepository callRecordRepository;
    private final ClovaStreamingClient clovaStreamingClient;
    private final CallTranscriptCache transcriptCache;

    @Autowired
    public WebSocketHandler(CallRepository callRepository,
                            CallRecordRepository callRecordRepository,
                            UserRepository userRepository,
                            ClovaStreamingClient clovaStreamingClient,
                            CallTranscriptCache transcriptCache) {
        this.userRepository = userRepository;
        this.callRepository = callRepository;
        this.callRecordRepository = callRecordRepository;
        this.clovaStreamingClient = clovaStreamingClient;
        this.transcriptCache = transcriptCache;
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            // 1) 상대 WS로 오디오 릴레이
            for (ConcurrentWebSocketSessionDecorator decorator : sessionMap.values()) {
                if (decorator.isOpen() && !decorator.getId().equals(session.getId())) {
                    decorator.sendMessage(message);
                }
            }

            // 2) gRPC 스트림으로 PCM 청크 포워딩
            StreamObserver<NestRequest> grpc = sessionGrpcMap.get(session.getId());
            if (grpc != null) {
                byte[] chunk = new byte[message.getPayload().remaining()];
                message.getPayload().duplicate().get(chunk);
                ClovaStreamingClient.sendAudio(grpc, chunk);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        System.out.println("Received text message: " + message.getPayload());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        String callerId = getQueryParam(session, "caller-id");
        String agentPhone = getQueryParam(session, "agent-phone");
        String conversationUuid = getQueryParam(session, "conversation-uuid");

        System.out.println("WS connected. callerId=" + callerId
                + " agentPhone=" + agentPhone
                + " conversationUuid=" + conversationUuid);

        if (conversationUuid == null) {
            System.err.println("No conversation-uuid on WS; closing " + session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        CallSession cs = sessionDataMap.computeIfAbsent(conversationUuid, uuid -> {
            CallSession s = new CallSession();
            s.setConversationUuid(uuid);
            s.setAgentPhone(agentPhone);
            if (agentPhone != null) {
                userRepository.findByPhone(agentPhone).ifPresent(user -> {
                    s.setAgentUserId(user.getUserId());
                    Call call = Call.builder()
                            .user(user)
                            .startTime(LocalDateTime.now())
                            .build();
                    callRepository.save(call);
                    s.setCall(call);
                });
            }
            return s;
        });

        boolean isAgentLeg = agentPhone != null && agentPhone.equals(callerId);
        if (isAgentLeg) {
            cs.setAgentWsSessionId(session.getId());
        } else {
            cs.setCallerPhone(callerId);
            cs.setCallerWsSessionId(session.getId());
        }

        ConcurrentWebSocketSessionDecorator decorator =
                new ConcurrentWebSocketSessionDecorator(session, 10, 1024 * 1024);
        sessionMap.put(session.getId(), decorator);

        // gRPC 스트림 오픈 (WS 세션당 1개)
        String speaker = isAgentLeg ? "agent" : "caller";
        StreamObserver<NestRequest> grpcStream = clovaStreamingClient.openStream(
                result -> onSttResult(session.getId(), speaker, result),
                error -> System.err.println("[CLOVA][" + session.getId() + "] error: " + error.getMessage())
        );
        sessionGrpcMap.put(session.getId(), grpcStream);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        sessionMap.remove(session.getId());

        StreamObserver<NestRequest> grpc = sessionGrpcMap.remove(session.getId());
        if (grpc != null) {
            try { grpc.onCompleted(); } catch (Exception ignore) {}
        }

        CallSession owning = null;
        for (CallSession cs : sessionDataMap.values()) {
            if (session.getId().equals(cs.getAgentWsSessionId())
                    || session.getId().equals(cs.getCallerWsSessionId())) {
                owning = cs;
                break;
            }
        }
        if (owning == null) return;

        if (session.getId().equals(owning.getAgentWsSessionId())) {
            owning.setAgentWsSessionId(null);
        } else if (session.getId().equals(owning.getCallerWsSessionId())) {
            owning.setCallerWsSessionId(null);
        }

        if (owning.getAgentWsSessionId() == null && owning.getCallerWsSessionId() == null) {
            Call call = owning.getCall();
            if (call != null) {
                // 1) 캐시의 final 엔트리를 DB에 일괄 저장
                List<TranscriptEntry> finals = transcriptCache.finalEntries(call.getId());
                for (TranscriptEntry e : finals) {
                    CallRecord rec = CallRecord.builder()
                            .call(call)
                            .transcription(e.text())
                            .speakerPhoneNumber(e.speakerPhone())
                            .build();
                    callRecordRepository.save(rec);
                }

                // 2) 통화 종료 처리
                call.endCall();
                callRepository.save(call);

                // 3) SSE 구독자 end + 캐시 정리
                transcriptCache.close(call.getId());
            }
            sessionDataMap.remove(owning.getConversationUuid());
        }
    }

    private void onSttResult(String wsSessionId, String speaker, SttResult result) {
        if (result == null || result.text() == null || result.text().isBlank()) return;

        System.out.println("[STT][" + speaker + "][final=" + result.isFinal() + "] " + result.text());

        CallSession cs = sessionDataMap.values().stream()
                .filter(s -> wsSessionId.equals(s.getAgentWsSessionId())
                        || wsSessionId.equals(s.getCallerWsSessionId()))
                .findFirst().orElse(null);
        if (cs == null || cs.getCall() == null) return;

        String speakerPhone = "agent".equals(speaker) ? cs.getAgentPhone() : cs.getCallerPhone();
        transcriptCache.append(
                cs.getCall().getId(),
                speaker,
                speakerPhone,
                result.text(),
                result.isFinal()
        );
    }

    private String getQueryParam(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        for (String p : uri.getQuery().split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
