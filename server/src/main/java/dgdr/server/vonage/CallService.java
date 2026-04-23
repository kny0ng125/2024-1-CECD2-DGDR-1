package dgdr.server.vonage;

import dgdr.server.vonage.clova.CallTranscriptCache;
import dgdr.server.vonage.clova.TranscriptEntry;
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
     * 통화 마감 공통 절차 (WebSocket 종료 / FakeCallSimulator 공용).
     *  1) 캐시의 final 엔트리만 CallRecord 로 일괄 저장
     *  2) Call.endTime 세팅
     *  3) SSE 구독자에 end 이벤트 + 캐시 비움
     * endTime 이 이미 세팅된 Call 이면 no-op (멱등성 보장).
     */
    @Transactional
    public void finalizeCall(Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call not found: " + callId));

        if (call.getEndTime() != null) {
            transcriptCache.close(callId);
            return;
        }

        List<TranscriptEntry> finals = transcriptCache.finalEntries(callId);
        List<CallRecord> records = finals.stream()
                .map(e -> CallRecord.builder()
                        .call(call)
                        .speaker(e.speaker())
                        .transcription(e.text())
                        .speakerPhoneNumber(e.speakerPhone())
                        .build())
                .toList();
        callRecordRepository.saveAll(records);

        call.endCall();

        transcriptCache.close(callId);
    }
}
