package dgdr.server.telephony.sip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dgdr.server.telephony.core.CallControl;
import dgdr.server.telephony.core.CallDescriptor;
import dgdr.server.telephony.core.CallOrchestrator;
import dgdr.server.telephony.core.LegRole;
import dgdr.server.user.infra.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ARI 이벤트 WebSocket 구독. 통화의 <b>제어 경로</b>를 담당한다.
 *
 * <p>미디어 경로({@link AudioSocketServer})와 완전히 분리되어 있다.
 * 이 연결이 끊겨도 이미 붙은 통화의 오디오와 전사는 계속 흐른다 —
 * 새 통화를 받거나 끊지 못할 뿐이다. 실전 CTI 에서 콜 컨트롤 채널과
 * 미디어 채널이 별개의 접속인 것과 같은 이유다.
 *
 * <h2>처리하는 이벤트</h2>
 * <pre>
 *   StasisStart          채널이 Stasis 앱에 진입 → 통화 개설(OFFERED) + 벨
 *   StasisEnd            채널이 앱을 떠남 (수락 후 dialplan 복귀 시에도 발생)
 *   ChannelHangupRequest 발신자가 끊음 → 통화 마감
 *   ChannelDestroyed     채널 소멸 → 통화 마감 (최종 방어선)
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sip.ari.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AriEventListener {

    private final AudioSocketProperties properties;
    private final AriClient ari;
    private final SipCallRegistry registry;
    private final CallOrchestrator orchestrator;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Asterisk channelId → 우리 callId. 종료 이벤트를 통화로 되짚는 데 쓴다. */
    private final ConcurrentMap<String, Long> callIdByChannel = new ConcurrentHashMap<>();

    /** callId → 벨 타임아웃 예약. 수락되면 취소한다. */
    private final ConcurrentMap<Long, ScheduledFuture<?>> ringTimeouts = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ari-ring-timeout");
                t.setDaemon(true);
                return t;
            });

    private volatile WebSocketSession session;
    private volatile boolean running;

    // ================================================================
    // 연결 관리
    // ================================================================

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        connect();
    }

    private void connect() {
        if (!running) return;
        AudioSocketProperties.Ari cfg = properties.getAri();

        // ARI 이벤트 WebSocket 은 Basic 이 아니라 쿼리스트링 자격증명을 쓴다.
        // (WebSocket 핸드셰이크에 헤더를 붙이기 어려운 클라이언트가 많아
        //  Asterisk 가 이 방식을 제공한다.)
        String url = cfg.getBaseUrl().replaceFirst("^http", "ws")
                + "/ari/events?api_key=" + enc(cfg.getUsername()) + ":" + enc(cfg.getPassword())
                + "&app=" + enc(cfg.getAppName())
                + "&subscribeAll=true";

        try {
            new StandardWebSocketClient()
                    .execute(new Handler(), null, URI.create(url))
                    .get(10, TimeUnit.SECONDS);
            log.info("[ari] event stream connected (app={})", cfg.getAppName());
        } catch (Exception e) {
            log.error("[ari] event stream connect failed: {} — retrying in {}s",
                    e.toString(), cfg.getReconnectInterval().toSeconds());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running) return;
        scheduler.schedule(this::connect,
                properties.getAri().getReconnectInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    private final class Handler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession ws) {
            session = ws;
        }

        @Override
        protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
            try {
                dispatch(mapper.readTree(message.getPayload()));
            } catch (Exception e) {
                log.error("[ari] event handling failed: {}", e.toString(), e);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
            session = null;
            if (running) {
                log.warn("[ari] event stream closed ({}) — reconnecting", status);
                scheduleReconnect();
            }
        }
    }

    // ================================================================
    // 이벤트 처리
    // ================================================================

    private void dispatch(JsonNode event) {
        String type = event.path("type").asText();
        switch (type) {
            case "StasisStart"          -> onStasisStart(event);
            case "ChannelHangupRequest",
                 "ChannelDestroyed"     -> onChannelGone(event);
            default -> log.trace("[ari] ignored event {}", type);
        }
    }

    /**
     * 채널이 Stasis 앱에 진입했다. 통화를 열고 벨을 울린다.
     *
     * <p>여기서 <b>leg 를 붙이지 않는</b> 것이 핵심이다. 아직 미디어 연결이
     * 없다 — 요원이 수락해야 dialplan 이 재개되고 AudioSocket 이 붙는다.
     * 통화만 먼저 열어 두고 leg 는 나중에 합류한다.
     */
    private void onStasisStart(JsonNode event) {
        JsonNode channel = event.path("channel");
        String channelId = channel.path("id").asText();
        String callerPhone = channel.path("caller").path("number").asText(null);
        String dialed = channel.path("dialplan").path("exten").asText(null);

        // SIP Call-ID 를 통화 키로 쓴다. 같은 통화의 leg 들이 공유하는 값이다.
        String sipCallId = ari.getChannelVar(channelId, "CHANNEL(pjsip,call-id)");
        String providerCallKey = (sipCallId != null && !sipCallId.isBlank())
                ? sipCallId : "ari-" + channelId;

        String agentUserId = userRepository.findByPhone(dialed)
                .map(u -> u.getUserId())
                .orElse(null);
        if (agentUserId == null) {
            log.error("[ari] unknown agent extension '{}' on channel {}; rejecting", dialed, channelId);
            ari.hangup(channelId, "congestion");
            return;
        }

        String audioSocketUuid = UUID.randomUUID().toString();
        SipCallRegistry.Ticket ticket = new SipCallRegistry.Ticket(
                audioSocketUuid, providerCallKey, LegRole.CALLER,
                dialed, callerPhone, java.time.Instant.now());

        CallControl control = new SipCallControl(
                channelId, audioSocketUuid, ticket, ari, registry, properties);

        Long callId = orchestrator.openCall(
                new CallDescriptor(providerCallKey, agentUserId, dialed, callerPhone),
                control);

        callIdByChannel.put(channelId, callId);
        ari.ring(channelId);   // 발신자가 무음을 듣지 않게

        scheduleRingTimeout(callId, channelId);
        log.info("[ari] call {} offered: channel={} from={} to={}",
                callId, channelId, callerPhone, dialed);
    }

    /**
     * 요원이 받지 않은 채 시간이 지나면 끊는다.
     *
     * <p>무한정 울리게 두면 Asterisk 채널과 서버의 통화 객체가 계속 쌓인다.
     * 실제 상황실이라면 다른 요원에게 넘기는 것이 맞지만, 이 시스템은 큐잉·
     * 분배를 다루지 않으므로 끊고 미응답으로 기록한다.
     */
    private void scheduleRingTimeout(Long callId, String channelId) {
        long millis = properties.getAri().getRingTimeout().toMillis();
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            log.warn("[ari] call {} not answered within {}ms — hanging up", callId, millis);
            try {
                orchestrator.hangupCall(callId, CallControl.HangupCause.TIMEOUT);
            } catch (RuntimeException e) {
                log.debug("[ari] ring timeout hangup failed: {}", e.toString());
            }
        }, millis, TimeUnit.MILLISECONDS);
        ringTimeouts.put(callId, task);
    }

    /**
     * 채널이 사라졌다. 통화를 마감한다.
     *
     * <p>{@code closeCall} 을 쓰는 이유: 벨만 울리다 끝난 통화는 leg 가
     * 하나도 없어서 leg 종료로는 마감이 트리거되지 않는다. 그래도 기록에는
     * 남아야 한다 — 놓친 신고가 사라지면 안 된다.
     */
    private void onChannelGone(JsonNode event) {
        String channelId = event.path("channel").path("id").asText();
        Long callId = callIdByChannel.remove(channelId);
        if (callId == null) return;

        ScheduledFuture<?> timeout = ringTimeouts.remove(callId);
        if (timeout != null) timeout.cancel(false);

        log.info("[ari] call {} channel gone ({})", callId, event.path("type").asText());
        try {
            orchestrator.closeCall(callId);
        } catch (RuntimeException e) {
            log.error("[ari] closeCall failed for {}: {}", callId, e.toString());
        }
    }

    /** 통화가 수락되면 벨 타임아웃을 취소한다. */
    public void cancelRingTimeout(Long callId) {
        ScheduledFuture<?> timeout = ringTimeouts.remove(callId);
        if (timeout != null) timeout.cancel(false);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @PreDestroy
    public void stop() {
        running = false;
        ringTimeouts.values().forEach(f -> f.cancel(false));
        scheduler.shutdownNow();
        WebSocketSession ws = session;
        if (ws != null && ws.isOpen()) {
            try { ws.close(); } catch (Exception ignored) { }
        }
    }
}
