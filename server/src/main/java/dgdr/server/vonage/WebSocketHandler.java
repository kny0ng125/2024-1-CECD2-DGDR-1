package dgdr.server.vonage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dgdr.server.vonage.user.infra.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import reactor.core.publisher.Mono;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

@Component
public class WebSocketHandler extends BinaryWebSocketHandler {
    private final ConcurrentMap<String, ConcurrentWebSocketSessionDecorator> sessionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ByteArrayOutputStream> sessionAudioDataMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, File> sessionAudioFileMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CallSession> sessionDataMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final CallRecordRepository callRecordRepository;
    private final String clovaInvokeUrl;
    private final String clovaSecretKey;

    @Autowired
    public WebSocketHandler(CallRepository callRepository,
                            CallRecordRepository callRecordRepository,
                            UserRepository userRepository,
                            @Value("${clova.speech.invoke-url}") String clovaInvokeUrl,
                            @Value("${clova.speech.secret-key}") String clovaSecretKey) {
        this.userRepository = userRepository;
        this.callRepository = callRepository;
        this.callRecordRepository = callRecordRepository;
        this.clovaInvokeUrl = clovaInvokeUrl;
        this.clovaSecretKey = clovaSecretKey;

        scheduler.scheduleAtFixedRate(this::saveAndSendAudioToFile, 3, 5, TimeUnit.SECONDS);
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            ByteBuffer buffer = message.getPayload();
            sessionAudioDataMap.computeIfAbsent(session.getId(), k -> new ByteArrayOutputStream());
            sessionAudioDataMap.get(session.getId()).write(buffer.array(), buffer.position(), buffer.remaining());

            for (ConcurrentWebSocketSessionDecorator sessionDecorator : sessionMap.values()) {
                if (sessionDecorator.isOpen() && !sessionDecorator.getId().equals(session.getId())) {
                    sessionDecorator.sendMessage(message);
                }
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

        if (agentPhone != null && agentPhone.equals(callerId)) {
            cs.setAgentWsSessionId(session.getId());
        } else {
            cs.setCallerPhone(callerId);
            cs.setCallerWsSessionId(session.getId());
        }

        ConcurrentWebSocketSessionDecorator decorator =
                new ConcurrentWebSocketSessionDecorator(session, 10, 1024 * 1024);
        sessionMap.put(session.getId(), decorator);

        createNewAudioFile(session.getId(), callerId != null ? callerId : "unknown");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        sessionMap.remove(session.getId());
        sessionAudioDataMap.remove(session.getId());
        sessionAudioFileMap.remove(session.getId());

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
                call.endCall();
                callRepository.save(call);
            }
            sessionDataMap.remove(owning.getConversationUuid());
        }
    }

    private void saveAndSendAudioToFile() {
        for (ConcurrentWebSocketSessionDecorator session : sessionMap.values()) {
            if (!session.isOpen()) continue;

            ByteArrayOutputStream buf = sessionAudioDataMap.get(session.getId());
            if (buf == null) continue;

            byte[] audioData = buf.toByteArray();
            buf.reset();

            if (audioData.length > 0) {
                try {
                    appendToWavFile(audioData, sessionAudioFileMap.get(session.getId()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                processAudioFile(session);
            }
        }
    }

    private void processAudioFile(ConcurrentWebSocketSessionDecorator session) {
        try {
            File audioFile = sessionAudioFileMap.get(session.getId());
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                byte[] audioData = Files.readAllBytes(audioFile.toPath());

                sendToClovaSTT(audioData).subscribe(result -> {
                    if (result != null && !result.isEmpty()) {
                        saveConversation(session, result);
                        System.out.println("Transcription result: \n" + result);
                    }
                    String callerId = getCallerIdFromSession(session.getDelegate());
                    createNewAudioFile(session.getId(), callerId);
                }, error -> {
                    System.err.println("Error during transcription: " + error.getMessage());
                    error.printStackTrace();
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNewAudioFile(String sessionId, String callerId) {
        File audioDir = new File("audio");
        if (!audioDir.exists()) audioDir.mkdirs();
        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File audioFile = new File(audioDir, "audio_" + callerId + "_" + timeStamp + ".wav");
        sessionAudioFileMap.put(sessionId, audioFile);
    }

    private void appendToWavFile(byte[] audioData, File file) throws IOException {
        if (file == null) return;
        boolean append = file.exists();
        int bufferSize = 16384;
        try (FileOutputStream fos = new FileOutputStream(file, append);
             AudioInputStream audioInputStream = new AudioInputStream(
                     new ByteArrayInputStream(audioData), getAudioFormat(), audioData.length)) {

            if (append) {
                fos.getChannel().position(file.length());
            } else {
                AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, fos);
                return;
            }

            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    private AudioFormat getAudioFormat() {
        return new AudioFormat(16000, 16, 1, true, false);
    }

    private Mono<String> sendToClovaSTT(byte[] wavData) {
        WebClient clovaClient = WebClient.builder()
                .baseUrl(clovaInvokeUrl)
                .defaultHeader("X-CLOVASPEECH-API-KEY", clovaSecretKey)
                .build();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("media", new ByteArrayResource(wavData))
                .header("Content-Disposition", "form-data; name=media; filename=audio.wav")
                .contentType(MediaType.MULTIPART_FORM_DATA);

        String paramsJson = """
                {
                    "language": "ko-KR",
                    "completion": "sync",
                    "noiseFiltering": true,
                    "fullText": true,
                    "diarization": { "enable": false }
                }
                """;
        builder.part("params", paramsJson).contentType(MediaType.APPLICATION_JSON);

        return clovaClient.post()
                .uri("/recognizer/upload")
                .bodyValue(builder.build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                    return jsonObject.get("text").getAsString();
                })
                .doOnError(e -> {
                    System.err.println("STT API error: " + e.getMessage());
                    e.printStackTrace();
                });
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

    private String getCallerIdFromSession(WebSocketSession session) {
        String v = getQueryParam(session, "caller-id");
        return v != null ? v : "unknown";
    }

    private void saveConversation(ConcurrentWebSocketSessionDecorator session, String transcription) {
        String wsId = session.getId();
        CallSession cs = sessionDataMap.values().stream()
                .filter(s -> wsId.equals(s.getAgentWsSessionId()) || wsId.equals(s.getCallerWsSessionId()))
                .findFirst().orElse(null);

        if (cs == null || cs.getCall() == null) {
            System.err.println("No CallSession for WS " + wsId + " — skipping save");
            return;
        }

        CallRecord callRecord = CallRecord.builder()
                .call(cs.getCall())
                .transcription(transcription)
                .speakerPhoneNumber(getCallerIdFromSession(session.getDelegate()))
                .build();
        callRecordRepository.save(callRecord);
    }
}