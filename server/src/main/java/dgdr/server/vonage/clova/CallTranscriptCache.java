package dgdr.server.vonage.clova;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 통화별 STT 결과를 in-memory 로 누적하고 SSE 구독자에게 push.
 * - final 결과만 DB flush 대상
 * - partial 결과는 실시간 자막용 (통화 종료 시 폐기)
 */
@Component
public class CallTranscriptCache {

    private final ConcurrentMap<Long, CopyOnWriteArrayList<TranscriptEntry>> entriesByCall = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByCall = new ConcurrentHashMap<>();

    /** STT 결과를 캐시에 추가하고 구독자에게 즉시 push */
    public void append(Long callId, String speaker, String speakerPhone,
                       String text, boolean isFinal) {
        if (callId == null || text == null || text.isBlank()) return;

        TranscriptEntry entry = new TranscriptEntry(
                speaker, speakerPhone, text, isFinal, LocalDateTime.now());

        entriesByCall.computeIfAbsent(callId, k -> new CopyOnWriteArrayList<>()).add(entry);
        broadcast(callId, entry);
    }

    /** 통화 종료 시 final 결과만 반환 (DB 저장용). 캐시는 남겨둠(flush 후 별도 clear 호출). */
    public List<TranscriptEntry> finalEntries(Long callId) {
        CopyOnWriteArrayList<TranscriptEntry> list = entriesByCall.get(callId);
        if (list == null) return List.of();
        return list.stream().filter(TranscriptEntry::isFinal).toList();
    }

    /** 현재까지의 전체 엔트리 (초기 로드 시 스냅샷용) */
    public List<TranscriptEntry> snapshot(Long callId) {
        CopyOnWriteArrayList<TranscriptEntry> list = entriesByCall.get(callId);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 통화 종료 후 캐시 제거 (구독자에게 end 이벤트 전송 포함) */
    public void close(Long callId) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByCall.remove(callId);
        if (emitters != null) {
            for (SseEmitter em : emitters) {
                try {
                    em.send(SseEmitter.event().name("end").data("done"));
                    em.complete();
                } catch (Exception ignore) {}
            }
        }
        entriesByCall.remove(callId);
    }

    /** SSE 구독 등록 */
    public SseEmitter subscribe(Long callId) {
        SseEmitter emitter = new SseEmitter(0L); // timeout 무제한
        CopyOnWriteArrayList<SseEmitter> list =
                emittersByCall.computeIfAbsent(callId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> { list.remove(emitter); emitter.complete(); });
        emitter.onError(e -> list.remove(emitter));

        // 구독 직후 현재까지의 엔트리 일괄 전송 (재연결 대응)
        try {
            for (TranscriptEntry e : snapshot(callId)) {
                emitter.send(SseEmitter.event().name("transcript").data(e));
            }
        } catch (IOException e) {
            list.remove(emitter);
        }
        return emitter;
    }

    private void broadcast(Long callId, TranscriptEntry entry) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByCall.get(callId);
        if (emitters == null) return;
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().name("transcript").data(entry));
            } catch (IOException e) {
                emitters.remove(em);
            }
        }
    }
}
