package dgdr.server.call;

import dgdr.server.privacy.AccessAction;
import dgdr.server.privacy.AccessLogService;
import dgdr.server.telephony.core.CallControl;
import dgdr.server.telephony.core.CallOrchestrator;
import dgdr.server.telephony.core.CallState;
import dgdr.server.user.domain.PrincipalDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 요원의 호 제어 — 수락 / 거절 / 종료.
 *
 * <h2>이 컨트롤러가 오디오를 다루지 않는 이유</h2>
 * <p>상담원 PC 는 신호만 보내고 오디오는 게이트웨이가 계속 물고 있는
 * <b>3자 제어(third-party call control)</b> 모델이다. 실전 PSAP 과 대형
 * 컨택센터가 쓰는 방식이며, 브라우저가 WebRTC 로 오디오를 종단하는
 * 1자 제어와 대비된다.
 *
 * <p>3자 제어를 택한 이유는 명확하다. 오디오가 요원 PC 를 거치면
 * (1) PC 가 죽으면 통화와 전사가 함께 죽고 (2) PC 성능·오디오 드라이버에
 * 통화 품질이 좌우되며 (3) 요원이 전사 경로를 조작할 수 있어 감사 가능성이
 * 사라진다. 119 신고 접수에서 셋 다 받아들일 수 없는 위험이다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/call/{callId}")
@RequiredArgsConstructor
public class CallControlController {

    private final CallOrchestrator orchestrator;
    private final CallService callService;
    private final AccessLogService accessLogService;

    /**
     * 통화 수락. 이 시점부터 미디어가 흐르고 전사가 시작된다.
     *
     * @return 200 = 이번 요청으로 수락됨, 204 = 이미 수락된 통화
     */
    @PostMapping("/answer")
    public ResponseEntity<Void> answer(@PathVariable Long callId,
                                       @AuthenticationPrincipal PrincipalDetails principal,
                                       HttpServletRequest request) {
        authorize(callId, principal);

        boolean changed = invoke(callId, () -> orchestrator.answerCall(callId));

        // 신고 내용(민감정보)에 접근이 시작되는 시점이므로 기록을 남긴다.
        accessLogService.record(AccessAction.ANSWER_CALL, "CALL",
                String.valueOf(callId), request);

        return changed
                ? ResponseEntity.ok().build()
                : ResponseEntity.noContent().build();
    }

    /**
     * 수신 거절. 발신자에게는 SIP 603 Decline 으로 전달된다.
     *
     * <p>종료와 별도 엔드포인트인 이유: 발신자 단말에 다르게 표시되고,
     * 기록상으로도 "받았다가 끊음"과 "받지 않음"은 구분되어야 한다.
     */
    @PostMapping("/reject")
    public ResponseEntity<Void> reject(@PathVariable Long callId,
                                       @AuthenticationPrincipal PrincipalDetails principal,
                                       HttpServletRequest request) {
        authorize(callId, principal);
        invoke(callId, () -> {
            orchestrator.hangupCall(callId, CallControl.HangupCause.REJECTED);
            return true;
        });
        accessLogService.record(AccessAction.REJECT_CALL, "CALL",
                String.valueOf(callId), request);
        return ResponseEntity.accepted().build();
    }

    /** 통화 종료. */
    @PostMapping("/hangup")
    public ResponseEntity<Void> hangup(@PathVariable Long callId,
                                       @AuthenticationPrincipal PrincipalDetails principal,
                                       HttpServletRequest request) {
        authorize(callId, principal);
        invoke(callId, () -> {
            orchestrator.hangupCall(callId, CallControl.HangupCause.NORMAL);
            return true;
        });
        accessLogService.record(AccessAction.HANGUP_CALL, "CALL",
                String.valueOf(callId), request);
        return ResponseEntity.accepted().build();
    }

    /**
     * 다른 요원의 통화를 제어할 수 없게 한다.
     *
     * <p>열람과 같은 수준의 검증이 필요하다. 소유권 확인 없이 callId 만으로
     * 제어를 허용하면, 번호를 바꿔 가며 남의 신고 통화를 끊을 수 있다.
     */
    private void authorize(Long callId, PrincipalDetails principal) {
        callService.assertOwnership(callId, principal.getUsername());
    }

    /**
     * 진행 중이 아닌 통화에 대한 제어를 409 로 돌려준다.
     *
     * <p>발신자가 먼저 끊는 것과 요원이 버튼을 누르는 것은 흔하게 경합한다.
     * 이때 500 을 내면 화면에 오류가 뜨지만, 실제로는 "이미 끝난 통화"라는
     * 정상적인 상황이다. 프런트가 구분해서 처리할 수 있게 상태 코드를 나눈다.
     */
    private boolean invoke(Long callId, java.util.function.Supplier<Boolean> action) {
        try {
            return action.get();
        } catch (IllegalStateException e) {
            CallState state = orchestrator.stateOf(callId);
            log.info("[call {}] control rejected: {} (state={})", callId, e.getMessage(), state);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is not active");
        }
    }
}
