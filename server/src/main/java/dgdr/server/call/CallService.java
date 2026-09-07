package dgdr.server.call;

import dgdr.server.privacy.RetentionPolicy;
import dgdr.server.transcript.CallTranscriptCache;
import dgdr.server.transcript.TranscriptEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CallService {
    private final CallRepository callRepository;
    private final CallRecordRepository callRecordRepository;
    private final CallTranscriptCache transcriptCache;

    public List<CallDto> getCallList(String userId) {
        return callRepository.findAllByUserId(userId)
                .stream()
                .map(call -> CallDto.builder()
                        .id(call.getId())
                        .startTime(call.getStartTime())
                        .user(call.getUser())
                        .build())
                .collect(Collectors.toList());
    }

    public List<CallDto> getCallListByDateRange(String userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        return callRepository.findAllByStartTimeBetween(userId, startDateTime, endDateTime)
                .stream()
                .map(call -> CallDto.builder()
                        .id(call.getId())
                        .startTime(call.getStartTime())
                        .user(call.getUser())
                        .build())
                .collect(Collectors.toList());
    }

    public List<CallRecordDto> getLatestCall(String userId) {
        Call latestCall = callRepository.findFirstByOrderByStartTimeDesc(userId);
        if (latestCall == null) return List.of();
        return callRecordRepository.findByCallId(latestCall.getId())
                .stream()
                .map(callRecord -> CallRecordDto.builder()
                        .id(callRecord.getId())
                        .speaker(callRecord.getSpeaker())
                        .speakerPhoneNumber(callRecord.getSpeakerPhoneNumber())
                        .transcription(callRecord.getTranscription())
                        .time(callRecord.getTime())
                        .build())
                .collect(Collectors.toList());
    }

    public List<CallRecordDto> getCallRecord(Long callId) {
        return callRecordRepository.findByCallId(callId)
                .stream()
                .map(callRecord -> CallRecordDto.builder()
                        .id(callRecord.getId())
                        .speaker(callRecord.getSpeaker())
                        .speakerPhoneNumber(callRecord.getSpeakerPhoneNumber())
                        .transcription(callRecord.getTranscription())
                        .time(callRecord.getTime())
                        .build())
                .collect(Collectors.toList());
    }

    public void assertOwnership(Long callId, String agentUserId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call not found: " + callId));
        if (call.getUser() == null || !agentUserId.equals(call.getUser().getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your call");
        }
    }

    /**
     * 통화 마감 공통 절차. 통화 소스 종류와 무관하게 코어(CallOrchestrator)가
     * 마지막 leg 가 닫힐 때 이 경로 하나로 들어온다.
     *  1) 캐시의 final 엔트리만 CallRecord 로 일괄 저장
     *  2) Call.endTime 세팅
     *  3) SSE 구독자에 end 이벤트 + 캐시 비움
     * endTime 이 이미 세팅된 Call 이면 no-op (멱등성 보장).
     */
    @Transactional
    public void finalizeCall(Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call not found: " + callId));

        // 상태 전이 자체가 멱등성 래치다. 이미 종료된 통화면 캐시만 정리하고 끝낸다.
        if (!call.endCall()) {
            transcriptCache.close(callId);
            return;
        }

        // 전사본은 민감정보이므로 저장 시점에 보존기간 만료 시각을 함께 확정한다.
        // 이후 파기 배치가 이 값만 보고 삭제 대상을 판단한다.
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(RetentionPolicy.CALL_RECORD_DAYS);

        List<TranscriptEntry> finals = transcriptCache.finalEntries(callId);
        List<CallRecord> records = finals.stream()
                .map(e -> CallRecord.builder()
                        .call(call)
                        .speaker(e.speaker())
                        .transcription(e.text())
                        .speakerPhoneNumber(e.speakerPhone())
                        .retentionExpiresAt(expiresAt)
                        .build())
                .toList();
        // 수락되지 않고 끝난 통화는 전사가 없어 빈 목록이 저장된다.
        // 그래도 calls 행은 남는다 — 놓친 신고가 기록에서 사라지면 안 된다.
        callRecordRepository.saveAll(records);

        transcriptCache.close(callId);
    }
}
