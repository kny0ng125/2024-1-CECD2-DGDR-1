package dgdr.server.vonage.call;

import dgdr.server.vonage.manual.ManualService;
import dgdr.server.vonage.realtime.AgentEventChannel;
import dgdr.server.vonage.user.domain.PrincipalDetails;
import dgdr.server.vonage.transcript.CallTranscriptCache;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CallController {
    /** 통화·기록 조회 + 소유권 검증 (getCallList, getCallRecord, assertOwnership 등) */
    private final CallService callService;

    /** [data 채널] 통화별 자막 캐시 + transcript SSE 구독 관리 */
    private final CallTranscriptCache transcriptCache;

    /** 통화 STT 기반 AI 매뉴얼 조회 (SageMaker 호출) */
    private final ManualService manualService;

    /** [control 채널] 요원 단위 통화 라이프사이클(call_started/ended) push — callId 트리거 */
    private final AgentEventChannel agentEventChannel;

    // ============================================================
    // 통화/기록 조회 (REST) — 요원 본인 통화 대상
    // ============================================================

    /** 요원의 전체 통화 목록 */
    @GetMapping("/api/v1/call")
    public ResponseEntity<List<CallDto>> getCallList(
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        List<CallDto> callList = callService.getCallList(principalDetails.getUsername());
        return ResponseEntity.ok(callList);
    }

    /** 기간별 통화 목록 */
    @GetMapping("/api/v1/call/date")
    public ResponseEntity<List<CallDto>> getCallListByDateRange(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {

        LocalDate startDateParsed = LocalDate.parse(startDate);
        LocalDate endDateParsed = LocalDate.parse(endDate);

        List<CallDto> callList = callService.getCallListByDateRange(principalDetails.getUsername(), startDateParsed, endDateParsed);
        return ResponseEntity.ok(callList);
    }

    /** 최근 통화의 기록 */
    @GetMapping("/api/v1/call/latest")
    public ResponseEntity<List<CallRecordDto>> getLatestCall(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        List<CallRecordDto> latestCall = callService.getLatestCall(principalDetails.getUsername());
        return ResponseEntity.ok(latestCall);
    }

    /** 특정 통화의 기록 (TODO: assertOwnership 추가 예정) */
    @GetMapping("/api/v1/{callId}/call-record")
    public ResponseEntity<List<CallRecordDto>> getCallRecord(@PathVariable Long callId) {
        List<CallRecordDto> callRecord = callService.getCallRecord(callId);
        return ResponseEntity.ok(callRecord);
    }

    // ============================================================
    // 매뉴얼 (REST) — 통화 내용 기반 AI 매뉴얼 조회
    // ============================================================

    /** 통화 STT 기반 매뉴얼 조회 (TODO: assertOwnership 추가 예정) */
    @GetMapping("/manual/{callId}")
    public ResponseEntity<Map<String, Object>> sendManual(@PathVariable Long callId) {
        Map<String, Object> response = manualService.getManual(callId);
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // 실시간 스트림 (SSE)
    //  - [control] agent 단위: 어느 통화가 이 요원에 붙었는지(call_started/ended)
    //  - [data]    call  단위: 해당 통화의 자막 내용
    //  두 채널은 수명이 다름 — agent=근무 내내, transcript=통화 1건 동안
    // ============================================================

    /** [control 채널] 요원 단위 통화 라이프사이클 — callId 트리거 (로그인 시 구독) */
    @GetMapping(value = "/api/v1/agent/events", produces = "text/event-stream")
    public SseEmitter agentEvents(@AuthenticationPrincipal PrincipalDetails pd) {
        return agentEventChannel.subscribe(pd.getUsername());
    }

    /** [data 채널] 통화 단위 자막 스트림 — callId 확보 후 구독 */
    @GetMapping(value = "/api/v1/call/{callId}/transcript/stream",
            produces = "text/event-stream")
    public SseEmitter streamTranscript(
            @PathVariable Long callId,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        callService.assertOwnership(callId, principalDetails.getUsername());
        return transcriptCache.subscribe(callId);
    }
}