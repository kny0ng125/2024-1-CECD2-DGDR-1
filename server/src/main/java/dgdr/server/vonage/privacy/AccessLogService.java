package dgdr.server.vonage.privacy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 접속기록 기록기.
 *
 * <p>개인정보 보호법 제29조 및 「개인정보의 안전성 확보조치 기준」이 요구하는
 * 접속기록을 남긴다.
 *
 * <h2>트랜잭션을 {@code @Transactional} 대신 TransactionTemplate 으로 다루는 이유</h2>
 * <ol>
 *   <li><b>독립 커밋.</b> 호출한 트랜잭션이 롤백되어도 "누가 접근을 시도했다"는
 *       사실은 남아야 하므로 REQUIRES_NEW 로 분리한다.</li>
 *   <li><b>커밋 예외까지 잡기 위해.</b> {@code @Transactional} 을 쓰면 커밋이
 *       프록시 경계에서 일어나 메서드 안의 try/catch 로는 잡히지 않는다.
 *       그러면 기록 실패가 본래 요청까지 실패시킨다. 템플릿으로 감싸면
 *       커밋이 try 블록 안에서 일어나 예외를 확실히 가둘 수 있다.</li>
 *   <li><b>자기 호출 문제 회피.</b> 오버로드끼리 호출해도 프록시를 우회하지
 *       않는다.</li>
 * </ol>
 *
 * <p>기록에 실패하면 접속기록이 누락되므로 ERROR 로그로 반드시 추적할 것.
 */
@Slf4j
@Service
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;
    private final TransactionTemplate transactionTemplate;

    public AccessLogService(AccessLogRepository accessLogRepository,
                            PlatformTransactionManager transactionManager) {
        this.accessLogRepository = accessLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(AccessAction action, String targetType, String targetId,
                       HttpServletRequest request) {
        // 인증 정보는 호출 스레드의 SecurityContext 에서 미리 읽는다.
        String actor = currentUserId();
        String ip = clientIp(request);
        try {
            transactionTemplate.executeWithoutResult(status ->
                    accessLogRepository.save(AccessLog.builder()
                            .actorUserId(actor)
                            .action(action)
                            .targetType(targetType)
                            .targetId(targetId)
                            .ipAddress(ip)
                            .build()));
        } catch (Exception e) {
            log.error("[AccessLog] 접속기록 저장 실패 actor={} action={} target={}:{}",
                    actor, action, targetType, targetId, e);
        }
    }

    /** HTTP 요청 컨텍스트가 없는 경우(스케줄러 등). */
    public void record(AccessAction action, String targetType, String targetId) {
        record(action, targetType, targetId, null);
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        return "anonymousUser".equals(name) ? null : name;
    }

    /**
     * 프록시·로드밸런서 뒤에 있을 때를 고려해 X-Forwarded-For 를 우선 확인한다.
     * 이 헤더는 위조가 가능하므로 신뢰 경계 안에서만 의미가 있다.
     */
    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
