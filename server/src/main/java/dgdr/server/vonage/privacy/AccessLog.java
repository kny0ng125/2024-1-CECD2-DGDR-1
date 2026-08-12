package dgdr.server.vonage.privacy;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 개인정보 접속기록.
 *
 * <p>개인정보 보호법 제29조와 「개인정보의 안전성 확보조치 기준」은
 * 개인정보처리시스템에 대한 접속기록을 남기고 위·변조되지 않도록
 * 보관할 것을 요구한다. 본 시스템은 통화 전사본(민감정보)을 다루므로
 * {@link RetentionPolicy#ACCESS_LOG_DAYS} 기간 동안 보관한다.
 *
 * <p><b>불변 엔티티.</b> 위·변조 방지를 위해 수정자를 두지 않으며,
 * 삭제는 보존기간 만료 파기 배치에서만 수행한다.
 *
 * <p>주의: {@code actorUserId} 는 users 테이블로의 FK 를 두지 않는다.
 * 계정이 삭제되어도 접속기록 자체는 남아야 하기 때문이다.
 */
@Entity
@Table(name = "access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 수행자 계정. 인증 정보를 확인할 수 없는 요청이면 null. */
    @Column(name = "actor_user_id", length = 50)
    private String actorUserId;

    @Column(name = "action", length = 40, nullable = false)
    @Enumerated(EnumType.STRING)
    private AccessAction action;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    /** IPv6 표기를 고려해 45자. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    @Builder
    public AccessLog(String actorUserId, AccessAction action, String targetType,
                     String targetId, String ipAddress) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.ipAddress = ipAddress;
        this.accessedAt = LocalDateTime.now();
    }
}
