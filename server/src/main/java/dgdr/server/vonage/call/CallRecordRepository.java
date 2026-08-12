package dgdr.server.vonage.call;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {
    List<CallRecord> findByCallId(Long callId);

    /**
     * 보존기간이 만료된 전사 기록을 파기한다.
     * 개인정보 보호법 제21조: 보존기간 경과 시 지체 없이 파기.
     */
    @Modifying
    @Query("DELETE FROM CallRecord r WHERE r.retentionExpiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /** 파기 대상 건수 확인용(로깅). */
    @Query("SELECT COUNT(r) FROM CallRecord r WHERE r.retentionExpiresAt < :now")
    long countExpired(@Param("now") LocalDateTime now);
}
