package dgdr.server.vonage.call;

import dgdr.server.vonage.realtime.AgentEventChannel;
import dgdr.server.vonage.user.domain.User;
import dgdr.server.vonage.user.infra.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CallSessionService {

    private final CallRepository callRepository;
    private final UserRepository userRepository;
    private final CallService callService;
    private final AgentEventChannel agentEventChannel;

    /** 통화 시작: Call 생성 + 에이전트 채널로 call_started push → callId 반환 */
    @Transactional
    public Long beginCall(String agentUserId, String callerPhone) {
        User agent = userRepository.findById(agentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + agentUserId));
        Call call = Call.builder().user(agent).startTime(LocalDateTime.now()).build();
        callRepository.save(call);
        agentEventChannel.callStarted(agentUserId, call.getId(), callerPhone,
                call.getStartTime().toString());
        return call.getId();
    }

    /** 통화 종료: finalizeCall + 에이전트 채널로 call_ended push */
    public void endCall(Long callId) {
        Call call = callRepository.findById(callId).orElse(null);
        callService.finalizeCall(callId);
        if (call != null && call.getUser() != null) {
            agentEventChannel.callEnded(call.getUser().getUserId(), callId);
        }
    }
}