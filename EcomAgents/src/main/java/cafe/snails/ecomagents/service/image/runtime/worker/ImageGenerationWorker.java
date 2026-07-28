package cafe.snails.ecomagents.service.image.runtime.worker;

import cafe.snails.ecomagents.service.image.runtime.ImageGenerationMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name="image.runtime.worker-enabled", havingValue="true", matchIfMissing=true)
/** 从任务队列领取并执行图片生成任务。 */
public class ImageGenerationWorker {
    private final ImageJobLeaseStore leases;
    private final ImageGenerationWorkerRunner runner;
    private final Set<Long> activeJobs = ConcurrentHashMap.newKeySet();
    private final String workerId;
    private final int concurrency;
    private final Duration leaseDuration;
    private final ImageGenerationMetrics metrics;

    public ImageGenerationWorker(ImageJobLeaseStore leases, ImageGenerationWorkerRunner runner,
            @Value("${image.runtime.worker-id:}") String configuredWorkerId,
            @Value("${image.runtime.worker-concurrency:2}") int concurrency,
            @Value("${image.runtime.lease-seconds:60}") long leaseSeconds,
            ImageGenerationMetrics metrics) {
        this.leases = leases;
        this.runner = runner;
        this.workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
                ? ManagementFactory.getRuntimeMXBean().getName() + "-" + java.util.UUID.randomUUID()
                : configuredWorkerId;
        this.concurrency = Math.max(1, concurrency);
        this.leaseDuration = Duration.ofSeconds(Math.max(15, leaseSeconds));
        this.metrics = metrics;
        this.metrics.bindActiveJobs(() -> activeJobs.size());
    }

    @Scheduled(fixedDelayString="${image.runtime.poll-interval-ms:1000}")
    public void poll() {
        while (activeJobs.size() < concurrency) {
            var claimed = leases.claimNext(workerId, leaseDuration);
            if (claimed.isEmpty()) return;
            Long jobId = claimed.get();
            activeJobs.add(jobId);
            runner.run(jobId, workerId, () -> activeJobs.remove(jobId));
        }
    }

    @Scheduled(fixedDelayString="${image.runtime.heartbeat-interval-ms:20000}")
    public void heartbeat() {
        activeJobs.removeIf(jobId -> !leases.heartbeat(jobId, workerId, leaseDuration));
    }

    @Scheduled(fixedDelayString="${image.runtime.recovery-interval-ms:30000}")
    public void recover() {
        int recovered = leases.recoverExpired();
        int cancelled = leases.finishRequestedCancellations();
        metrics.recovered(recovered);
        metrics.cancellationFinalized(cancelled);
        if (recovered + cancelled > 0) log.info("Recovered {} image jobs; finalized {} cancellations", recovered, cancelled);
    }

    Set<Long> activeJobs() { return Set.copyOf(activeJobs); }
}
