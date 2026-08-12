package dgdr.server.vonage.call;

import dgdr.server.vonage.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 통화 세션.
 *
 * <p>테이블명이 {@code calls} 인 이유: {@code CALL} 은 MySQL 예약어라
 * 기본 매핑(call)을 쓰면 DDL 과 쿼리가 모두 문법 오류를 낸다.
 *
 * <p>이 엔티티에는 신고 내용이 담기지 않으므로(담당 요원과 시각뿐)
 * 전사 기록({@link CallRecord})의 3개월 보존기간과 별개의 수명을 가진다.
 */
@Entity
@Table(name = "calls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Call {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @CreatedDate
    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    public void endCall() {
        this.endTime = LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "call")
    private List<CallRecord> callRecords;

    @Builder
    public Call(Long id, LocalDateTime startTime, User user) {
        this.id = id;
        this.startTime = startTime;
        this.user = user;
    }
}
