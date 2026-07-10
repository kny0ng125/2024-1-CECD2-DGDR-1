package dgdr.server.vonage.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.*;

@Component
public class AgentEventChannel {

    private final ConcurrentMap<String, CopyOnWriteArrayList<SseEmitter>> byAgent = new ConcurrentHashMap<>();

    public record CallStarted(Long callId, String callerPhone, String startedAt) {}
    public record CallEnded(Long callId) {}

    public SseEmitter subscribe(String agentUserId) {
        SseEmitter emitter = new SseEmitter(0L); // 타임아웃 무제한
        var list = byAgent.computeIfAbsent(agentUserId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> { list.remove(emitter); emitter.complete(); });
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    public void callStarted(String agentUserId, Long callId, String callerPhone, String startedAt) {
        push(agentUserId, "call_started", new CallStarted(callId, callerPhone, startedAt));
    }

    public void callEnded(String agentUserId, Long callId) {
        push(agentUserId, "call_ended", new CallEnded(callId));
    }

    private void push(String agentUserId, String event, Object data) {
        var list = byAgent.get(agentUserId);
        if (list == null) return;
        for (SseEmitter em : list) {
            try { em.send(SseEmitter.event().name(event).data(data)); }
            catch (IOException e) { list.remove(em); }
        }
    }
}