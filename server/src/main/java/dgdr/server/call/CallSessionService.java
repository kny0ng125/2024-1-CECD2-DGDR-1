package dgdr.server.call;

import dgdr.server.realtime.AgentEventChannel;
import dgdr.server.telephony.core.CallState;
import dgdr.server.user.domain.User;
import dgdr.server.user.infra.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 통화 생명주기의 영속화 + 요원 알림.
 *
 * <p>코어({@code CallOrchestrator})가 상태 전이를 판단하고, 이 서비스가
 * 그 결과를 DB 에 남기고 요원의 control 채널로 알린다. 판단과 기록을
 * 나눠 둔 덕분에 코어는 트랜잭션·JPA 를 모르고, 이 서비스는 통화 소스·
 * 오디오·STT 를 모른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallSessionService {

    private final CallRepository callRepository;
    private final UserRepository userRepository;
    private final CallService callService;
    private final AgentEventChannel agentEventChannel;

    /**
     * 통화 개설: Call 생성 + 요원 채널로 알림 push → callId 반환.
     *
     * <p>{@code initialState} 에 따라 보내는 이벤트가 다르다.
     * {@link CallState#OFFERED} 면 {@code call_offered}(벨 울림, 수락 대기),
     * {@link CallState#ANSWERED} 면 {@code call_started}(이미 통화 중)를 보낸다.
     * 프런트는 전자에서 수락 버튼을 띄우고, 후자에서 바로 자막을 구독한다.
     */
    @Transactional
    public Long beginCall(String agentUserId, String callerPhone, CallState initialState) {
        User agent = userRepository.findById(agentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + agentUserId));

        Call call = Call.builder()
                .user(agent)
                .startTime(LocalDateTime.now())
                .state(initialState)
                .build();
        callRepository.save(call);

        String startedAt = call.getStartTime().toString();
        if (initialState == CallState.OFFERED) {
            agentEventChannel.callOffered(agentUserId, call.getId(), callerPhone, startedAt);
        } else {
            agentEventChannel.callStarted(agentUserId, call.getId(), callerPhone, startedAt);
        }
        return call.getId();
    }

    /** 수락 처리: answer_time·state 기록 + 요원 채널로 {@code call_answered} push. */
    @Transactional
    public void answerCall(Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call not found: " + callId));

        if (!call.answer()) {
            log.debug("[call {}] answer ignored (state={})", callId, call.getState());
            return;
        }
        if (call.getUser() != null) {
            agentEventChannel.callAnswered(call.getUser().getUserId(), callId,
                    call.getAnswerTime().toString());
        }
    }

    /**
     * 통화 종료: 전사 flush + 종료 시각 기록 + 요원 채널로 {@code call_ended} push.
     *
     * <p>미응답으로 끝난 통화도 이 경로로 들어온다. 전사 기록이 없을 뿐
     * {@code calls} 행은 남아야 한다 — 놓친 신고가 기록에서 사라지면 안 된다.
     */
    public void endCall(Long callId) {
        Call call = callRepository.findById(callId).orElse(null);
        boolean missed = (call != null) && call.getAnswerTime() == null;

        callService.finalizeCall(callId);

        if (call != null && call.getUser() != null) {
            agentEventChannel.callEnded(call.getUser().getUserId(), callId, missed);
        }
    }
}
