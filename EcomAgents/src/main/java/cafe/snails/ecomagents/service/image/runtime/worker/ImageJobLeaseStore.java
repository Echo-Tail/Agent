package cafe.snails.ecomagents.service.image.runtime.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.*;

@Repository
@RequiredArgsConstructor
/** 管理图片生成任务的数据库租约和并发领取。 */
public class ImageJobLeaseStore {
    private final JdbcTemplate jdbc;

    @Transactional
    public Optional<Long> claimNext(String workerId, Duration leaseDuration) {
        String sql = """
                WITH candidate AS (
                    SELECT id FROM image_generation_jobs
                    WHERE status = 'PENDING'
                      AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE image_generation_jobs job
                   SET status = 'RUNNING', execution_phase = 'PREPARING', worker_id = ?,
                       lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                       started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                       attempt_count = attempt_count + 1, updated_at = CURRENT_TIMESTAMP
                  FROM candidate
                 WHERE job.id = candidate.id
                RETURNING job.id
                """;
        List<Long> ids = jdbc.query(sql, (rs, row) -> rs.getLong(1), workerId, leaseDuration.toSeconds());
        return ids.stream().findFirst();
    }

    public boolean heartbeat(Long jobId, String workerId, Duration leaseDuration) {
        int updated = jdbc.update("""
                UPDATE image_generation_jobs
                   SET lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND worker_id = ? AND status IN ('RUNNING','CANCEL_REQUESTED')
                """, leaseDuration.toSeconds(), jobId, workerId);
        return updated == 1;
    }

    public int recoverExpired() {
        int unknown = jdbc.update("""
                UPDATE image_generation_jobs
                   SET status = 'FAILED', execution_phase = NULL, worker_id = NULL, lease_until = NULL,
                       error_code = 'SUBMISSION_OUTCOME_UNKNOWN',
                       safe_error_message = '供应商提交结果不确定，请人工确认后重试',
                       retryable = FALSE, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP
                   AND execution_phase = 'SUBMITTING' AND provider_task_token IS NULL
                """);
        int requeued = jdbc.update("""
                UPDATE image_generation_jobs
                   SET status = 'PENDING', execution_phase = NULL, worker_id = NULL, lease_until = NULL,
                       next_attempt_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP
                   AND (execution_phase IS NULL OR execution_phase = 'PREPARING'
                        OR (execution_phase = 'POLLING' AND provider_task_token IS NOT NULL))
                """);
        int requiresResume = jdbc.update("""
                UPDATE image_generation_jobs
                   SET status = 'FAILED', execution_phase = NULL, worker_id = NULL, lease_until = NULL,
                       error_code = 'RECOVERY_REQUIRES_RESUME_SUPPORT',
                       safe_error_message = '任务中断且当前适配器不支持安全续跑，请人工确认',
                       retryable = FALSE, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP
                """);
        return unknown + requeued + requiresResume;
    }

    public int finishRequestedCancellations() {
        return jdbc.update("""
                UPDATE image_generation_jobs
                   SET status = 'CANCELLED', execution_phase = NULL, worker_id = NULL, lease_until = NULL,
                       completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'CANCEL_REQUESTED' AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)
                """);
    }
}
