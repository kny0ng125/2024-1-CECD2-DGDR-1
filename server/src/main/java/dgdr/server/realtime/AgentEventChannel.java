package dgdr.server.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [control 채널] 요원 단위 통화 라이프사이클 push.
 *
 * <p>자막 채널({@code CallTranscriptCache})과 수명이 다르다. 이 채널은
 * 요원이 로그인해 있는 <b>근무 내내</b> 열려 있고, 자막 채널은 통화 한 건
 * 동안만 산다. 두 채널을 나눈 이유가 여기 있다 — 프런트는 이 채널로
 * callId 를 받아야만 자막 채널을 구독할 수 있다.
 *
 * <h2>이벤트</h2>
 * <pre>
 *   call_offered  → 벨 울림. 수락 대기. (수락/거절 버튼 표시)
 *   call_answered → 수락됨. 이 시점부터 자막이 흐른다.
 *   call_started  → 수락 절차 없이 곧바로 통화 중으로 시작.
 *   call_ended    → 종료. missed=true 면 수락 없이 끝난 통화.
 * </pre>
 */
@Slf4j
@Component
public class AgentEventChannel {

    private final ConcurrentMap<String, CopyOnWriteArrayList<SseEmitter>> byAgent = new ConcurrentHashMap<>();

    /** 벨 울림. 아직 수락되지 않았으므로 자막을 구독해도 아무것도 오지 않는다. */
    public record CallOffered(Long callId, String callerPhone, String offeredAt) {}

    /** 수락됨. 프런트는 여기서 자막 스트림을 구독한다. */
    public record CallAnswered(Long callId, String answeredAt) {}

    /** 수락 절차 없이 통화 중으로 시작(제어 불가 소스). */
    public record CallStarted(Long callId, String callerPhone, String startedAt) {}

    /** 종료. {@code missed} 는 수락되지 않은 채 끝났음을 뜻한다. */
    public record CallEnded(Long callId, boolean missed) {}

    public SseEmitter subscribe(String agentUserId) {
        SseEmitter emitter = new SseEmitter(0L); // 타임아웃 무제한
        var list = byAgent.computeIfAbsent(agentUserId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> { list.remove(emitter); emitter.complete(); });
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    public void callOffered(String agentUserId, Long callId, String callerPhone, String offeredAt) {
        push(agentUserId, "call_offered", new CallOffered(callId, callerPhone, offeredAt));
    }

    public void callAnswered(String agentUserId, Long callId, String answeredAt) {
        push(agentUserId, "call_answered", new CallAnswered(callId, answeredAt));
    }

    public void callStarted(String agentUserId, Long callId, String callerPhone, String startedAt) {
        push(agentUserId, "call_started", new CallStarted(callId, callerPhone, startedAt));
    }

    public void callEnded(String agentUserId, Long callId, boolean missed) {
        push(agentUserId, "call_ended", new CallEnded(callId, missed));
    }

    /** 이 요원이 지금 이 채널을 듣고 있는가. 벨을 울릴 수 있는지 판단에 쓴다. */
    public boolean hasSubscriber(String agentUserId) {
        var list = byAgent.get(agentUserId);
        return list != null && !list.isEmpty();
    }

    private void push(String agentUserId, String event, Object data) {
        var list = byAgent.get(agentUserId);
        if (list == null || list.isEmpty()) {
            // 요원 화면이 열려 있지 않다. 통화 자체는 계속되지만 알림은 사라진다.
            log.warn("[agent {}] no control-channel subscriber; '{}' dropped", agentUserId, event);
            return;
        }
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                list.remove(em);
            }
        }
    }
}
