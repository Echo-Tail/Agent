package cafe.snails.ecomagents.service.image.runtime.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageJobInputRetentionCleaner {
    private final JdbcTemplate jdbc;
    @Value("${file.upload-dir:./uploads}") private String uploadDir;

    @Scheduled(cron="${image.runtime.input-cleanup-cron:0 30 3 * * ?}")
    public void clean() {
        List<Candidate> candidates = jdbc.query("""
                SELECT input.id, input.snapshot_path
                  FROM image_generation_job_inputs input
                  JOIN image_generation_jobs job ON job.id = input.job_id
                 WHERE ((job.status IN ('SUCCEEDED','PARTIALLY_SUCCEEDED')
                           AND job.completed_at < CURRENT_TIMESTAMP - INTERVAL '30 days')
                     OR (job.status IN ('FAILED','CANCELLED')
                           AND job.completed_at < CURRENT_TIMESTAMP - INTERVAL '7 days'))
                   AND NOT EXISTS (
                       SELECT 1 FROM image_generation_job_inputs other_input
                       JOIN image_generation_jobs other_job ON other_job.id = other_input.job_id
                       WHERE other_input.snapshot_path = input.snapshot_path
                         AND other_input.id <> input.id
                         AND (other_job.status IN ('PENDING','RUNNING','CANCEL_REQUESTED')
                           OR other_job.completed_at IS NULL
                           OR (other_job.status IN ('SUCCEEDED','PARTIALLY_SUCCEEDED')
                               AND other_job.completed_at >= CURRENT_TIMESTAMP - INTERVAL '30 days')
                           OR (other_job.status IN ('FAILED','CANCELLED')
                               AND other_job.completed_at >= CURRENT_TIMESTAMP - INTERVAL '7 days')))
                 LIMIT 500
                """, (rs, row) -> new Candidate(rs.getLong(1), rs.getString(2)));
        int deleted = 0;
        for (Candidate candidate : candidates) {
            try {
                Path file = resolve(candidate.path());
                Files.deleteIfExists(file);
                deleted += jdbc.update("DELETE FROM image_generation_job_inputs WHERE id = ?", candidate.id());
            } catch (Exception e) {
                log.warn("Failed to clean image job input {}: {}", candidate.id(), e.getMessage());
            }
        }
        if (deleted > 0) log.info("Cleaned {} expired image job input snapshots", deleted);
    }

    Path resolve(String publicPath) {
        String normalized = publicPath == null ? "" : publicPath.replace('\\', '/');
        if (!normalized.startsWith("/uploads/image-jobs/"))
            throw new IllegalArgumentException("快照路径不在图片任务目录中");
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(normalized.substring("/uploads/".length())).normalize();
        if (!resolved.startsWith(root.resolve("image-jobs").normalize()))
            throw new IllegalArgumentException("非法快照路径");
        return resolved;
    }

    private record Candidate(Long id, String path) {}
}
