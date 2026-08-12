package dgdr.server.vonage.call;

import dgdr.server.vonage.privacy.RetentionPolicy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 통화 전사 기록.
 *
 * <p><b>민감정보 주의.</b> {@code transcription} 에는 신고자의 증상·의식·호흡
 * 상태가 그대로 담기므로 개인정보 보호법 제23조의 민감정보에 해당한다.
 * 조회 경로에는 반드시 접속기록(AccessLog)을 남겨야 한다.
 *
 * <p>보존기간은 신고 접수 녹취에 준해 90일이며 {@code retentionExpiresAt} 로
 * 관리한다. 만료분은 RetentionPolicyScheduler 가 파기한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CallRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id")
    private Call call;

    @Column(name = "speaker", length = 20)
    private String speaker;

    /** 발화자 전화번호. 개인정보이므로 보존기간 경과 시 함께 파기된다. */
    @Column(name = "speaker_phone_number", length = 20)
    private String speakerPhoneNumber;

    /**
     * 전사 내용(민감정보).
     *
     * <p>TEXT 로 두는 이유: STT 결과 한 발화가 VARCHAR(255) 를 넘는 경우가 있다.
     *
     * <p>TODO 운영 반영 시 컬럼 암호화 재검토. 법령상 암호화 의무 대상은
     * 비밀번호·고유식별정보·바이오정보로 한정되어 전사본 자체는 필수가
     * 아니지만, 민감정보인 만큼 강화된 보호조치를 적용할 여지가 있다.
     */
    // @Lob 은 붙이지 않는다. columnDefinition 이 있으면 불필요하고,
    // @Lob 만 쓰면 Hibernate 가 LONGTEXT 를 기대해 validate 가 실패한다.
    @Column(name = "transcription", columnDefinition = "TEXT")
    private String transcription;

    @CreatedDate
    @Column(name = "time")
    private LocalDateTime time;

    /** 보존기간 만료 시각. 이 시각이 지난 행은 파기 대상이다. */
    @Column(name = "retention_expires_at", nullable = false)
    private LocalDateTime retentionExpiresAt;

    @Builder
    public CallRecord(Call call, String speaker, String speakerPhoneNumber,
                      String transcription, LocalDateTime retentionExpiresAt) {
        this.call = call;
        this.speaker = speaker;
        this.speakerPhoneNumber = speakerPhoneNumber;
        this.transcription = transcription;
        this.retentionExpiresAt = retentionExpiresAt;
    }

    /**
     * 보존기간이 지정되지 않은 채 저장되는 것을 막는 안전장치.
     * 명시적으로 지정하지 않았다면 생성 시각 기준으로 계산한다.
     */
    @PrePersist
    void applyDefaultRetention() {
        if (this.retentionExpiresAt == null) {
            this.retentionExpiresAt = LocalDateTime.now().plusDays(RetentionPolicy.CALL_RECORD_DAYS);
        }
    }
}
