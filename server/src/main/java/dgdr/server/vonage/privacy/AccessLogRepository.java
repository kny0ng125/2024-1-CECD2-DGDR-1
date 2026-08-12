package dgdr.server.vonage.privacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    List<AccessLog> findByActorUserIdOrderByAccessedAtDesc(String actorUserId);

    List<AccessLog> findByTargetTypeAndTargetIdOrderByAccessedAtDesc(String targetType, String targetId);

    /** 보관기간이 지난 접속기록 파기. */
    @Modifying
    @Query("DELETE FROM AccessLog a WHERE a.accessedAt < :threshold")
    int deleteByAccessedAtBefore(@Param("threshold") LocalDateTime threshold);
}
