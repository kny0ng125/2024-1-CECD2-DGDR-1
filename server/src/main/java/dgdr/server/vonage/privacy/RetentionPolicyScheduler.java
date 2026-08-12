package dgdr.server.vonage.privacy;

import dgdr.server.vonage.call.CallRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보존기간이 지난 개인정보를 파기하는 배치.
 *
 * <p>개인정보 보호법 제21조는 보존기간이 경과한 개인정보를 지체 없이
 * 파기하도록 규정한다. 수작업 삭제에 의존하면 실제로 이행되지 않으므로
 * 스케줄러로 강제한다.
 *
 * <p>파기 대상
 * <ul>
 *   <li>통화 전사 기록 — {@code retention_expires_at} 경과분(기본 90일)</li>
 *   <li>접속기록 — 보관기간 경과분(기본 730일)</li>
 * </ul>
 *
 * <p>통화 세션({@code calls}) 자체는 삭제하지 않는다. 담당 요원과 시각만
 * 담고 있어 신고 내용이 남지 않으며, 통계·감사 목적으로 필요하기 때문이다.
 *
 * <p>주의: 다중 인스턴스로 운영한다면 동시 실행을 막기 위한 잠금
 * (ShedLock 등)이 필요하다. 현재는 단일 인스턴스를 전제로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionPolicyScheduler {

    private final CallRecordRepository callRecordRepository;
    private final AccessLogRepository accessLogRepository;
    private final AccessLogService accessLogService;

    @Value("${privacy.retention.access-log-days:" + RetentionPolicy.ACCESS_LOG_DAYS + "}")
    private int accessLogDays;

    @Scheduled(cron = "${privacy.retention.purge-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpired() {
        LocalDateTime now = LocalDateTime.now();

        long targetCount = callRecordRepository.countExpired(now);
        int purgedRecords = callRecordRepository.deleteExpired(now);

        LocalDateTime accessLogThreshold = now.minusDays(accessLogDays);
        int purgedLogs = accessLogRepository.deleteByAccessedAtBefore(accessLogThreshold);

        log.info("[RetentionPolicy] 파기 완료 — 전사기록 {}건(대상 {}건), 접속기록 {}건(기준 {} 이전)",
                purgedRecords, targetCount, purgedLogs, accessLogThreshold);

        if (purgedRecords > 0) {
            // 파기 사실 자체도 접속기록으로 남긴다.
            accessLogService.record(AccessAction.PURGE_EXPIRED, "CALL_RECORD",
                    String.valueOf(purgedRecords));
        }
    }
}
