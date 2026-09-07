package dgdr.server.call;

import dgdr.server.telephony.core.CallState;
import dgdr.server.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
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
 *
 * <h2>시각이 세 개인 이유</h2>
 * <p>{@code startTime}(벨이 울리기 시작한 시각)과 {@code answerTime}(요원이
 * 수락한 시각)을 나눠 기록한다. 둘의 차이가 <b>접수 지연</b>이고, 이는 상황실
 * 운영의 핵심 지표다. 하나로 합치면 "몇 초 만에 받았는가"를 영원히 알 수 없다.
 * 미응답 통화는 {@code answerTime} 이 {@code null} 로 남아 그 자체로 구분된다.
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

    /**
     * 통화가 시스템에 도달한 시각(벨이 울리기 시작한 시각).
     *
     * <p>{@code @CreatedDate} 를 쓰지 않고 직접 넣는 이유: 감사 필드가 아니라
     * 도메인 사실이다. 통화 개설 시각은 통화 소스가 알려주는 값이어야 하고,
     * JPA 영속화 시점과 우연히 일치할 뿐인 값에 의존해서는 안 된다.
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** 요원이 수락한 시각. 미응답으로 끝났으면 {@code null}. */
    @Column(name = "answer_time")
    private LocalDateTime answerTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private CallState state;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "call")
    private List<CallRecord> callRecords;

    @Builder
    public Call(Long id, LocalDateTime startTime, User user, CallState state) {
        this.id = id;
        this.startTime = (startTime != null) ? startTime : LocalDateTime.now();
        this.user = user;
        this.state = (state != null) ? state : CallState.OFFERED;
    }

    /**
     * 수락 처리. 이미 수락된 통화면 아무 일도 하지 않는다.
     *
     * <p>멱등한 이유: 요원이 수락 버튼을 연타하거나, 게이트웨이의 응답 이벤트와
     * 요원의 수락 요청이 경합할 수 있다. 두 번째 호출이 {@code answerTime} 을
     * 덮어쓰면 접수 지연 지표가 조용히 왜곡된다.
     *
     * @return 이번 호출로 실제 상태가 바뀌었으면 true
     */
    public boolean answer() {
        if (state != CallState.OFFERED) return false;
        this.answerTime = LocalDateTime.now();
        this.state = CallState.ANSWERED;
        return true;
    }

    /**
     * 종료 처리. 이미 종료된 통화면 아무 일도 하지 않는다.
     *
     * @return 이번 호출로 실제 상태가 바뀌었으면 true
     */
    public boolean endCall() {
        if (state == CallState.ENDED) return false;
        this.endTime = LocalDateTime.now();
        this.state = CallState.ENDED;
        return true;
    }

    /** 수락되지 않은 채 끝난 통화. 신고 내용이 없으므로 전사 기록도 없다. */
    public boolean isMissed() {
        return state == CallState.ENDED && answerTime == null;
    }
}
