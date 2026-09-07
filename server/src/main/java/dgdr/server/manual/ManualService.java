package dgdr.server.manual;

import dgdr.server.call.CallRecordDto;
import dgdr.server.call.CallService;
import dgdr.server.telephony.core.LegRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 통화 내용으로 응급처치 매뉴얼을 조회한다.
 *
 * <p>이 클래스는 검색을 하지 않는다 — 전사본을 질의문으로 다듬고
 * ({@link #formatConversation}) 결과를 프런트 계약에 맞춰 옮길 뿐이며,
 * 실제 검색은 {@link ManualRetriever} 구현체가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualService {

    /** 화면에 띄울 매뉴얼 카드 수. */
    private static final int TOP_K = 6;

    private final CallService callService;
    private final ManualRetriever retriever;

    /**
     * @return 프런트가 기대하는 {@code passage0..N} 형태의 응답.
     *         키 형식은 기존 프런트 어댑터와의 호환을 위해 유지한다.
     */
    public Map<String, Object> getManual(Long callId) {
        List<CallRecordDto> records = callService.getCallRecord(callId);
        String query = formatConversation(records);

        if (query.isBlank()) {
            // 아직 확정된 발화가 없다. 검색기를 부를 이유가 없다.
            log.debug("[manual] call {} has no final transcript yet", callId);
            return Map.of();
        }

        List<ManualCandidate> candidates = retriever.retrieve(query, TOP_K);
        log.info("[manual] call {} → {} candidate(s) via {}",
                callId, candidates.size(), retriever.name());

        return toWireFormat(candidates);
    }

    /**
     * 전사 기록을 검색 질의문으로 합친다.
     *
     * <p>화자 라벨을 붙이는 이유: 같은 단어라도 누가 말했는지에 따라 의미가
     * 다르다. "의식이 없어요"(신고자)는 환자 상태이고, 요원의 "의식이
     * 있습니까?"는 질문이다. 라벨 없이 합치면 검색기가 이를 구분할 수 없다.
     */
    private String formatConversation(List<CallRecordDto> records) {
        return records.stream()
                .filter(r -> r.getTranscription() != null && !r.getTranscription().isBlank())
                .map(r -> (LegRole.AGENT.wireName().equals(r.getSpeaker()) ? "상황실: " : "신고자: ")
                        + r.getTranscription())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 프런트 계약({@code passage0..N})으로 옮긴다.
     *
     * <p>{@link LinkedHashMap} 을 쓰는 이유: 프런트 어댑터가 유사도로 다시
     * 정렬하긴 하지만, 응답 자체가 순위 순이어야 디버깅할 때 눈으로 확인된다.
     */
    private Map<String, Object> toWireFormat(List<ManualCandidate> candidates) {
        Map<String, Object> wire = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            ManualCandidate c = candidates.get(i);
            wire.put("passage" + i, Map.of(
                    "병명", c.disease(),
                    "임상적 특징", c.clinicalFeatures(),
                    "환자평가 필수항목", c.patientAssessment(),
                    "유사도", c.similarity()
            ));
        }
        return wire;
    }
}
