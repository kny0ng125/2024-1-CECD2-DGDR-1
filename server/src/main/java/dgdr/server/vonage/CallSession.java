package dgdr.server.vonage;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CallSession {
    private String conversationUuid;   // Vonage conversation UUID (세션 키)
    private Call call;                 // DB 영속 Call 엔티티
    private String callerPhone;        // 신고자 번호
    private String agentPhone;         // 수보요원 전용 번호 (/answer payload.to)
    private String agentUserId;        // 수보요원 User.userId
    private String callerWsSessionId;  // 신고자 leg WS session id
    private String agentWsSessionId;   // 수보요원 leg WS session id

    // A-3 이후 추가 예정:
    // private io.grpc.stub.StreamObserver<NestRequest> grpcStream;
    // A-5 이후 추가 예정:
    // private java.util.List<CallRecordCache> cachedRecords;
}