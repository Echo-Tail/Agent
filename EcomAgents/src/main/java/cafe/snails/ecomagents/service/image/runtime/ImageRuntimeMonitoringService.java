package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.dto.ImageRuntimeMonitoringResponse;
import cafe.snails.ecomagents.model.ImageGenerationJobStatus;
import cafe.snails.ecomagents.repository.ImageGenerationJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ImageRuntimeMonitoringService {
    private static final String PREFIX = "ecomagents.image.runtime";
    private final MeterRegistry registry;
    private final ImageGenerationJobRepository jobs;

    public ImageRuntimeMonitoringResponse snapshot() {
        Map<String, Long> jobsByStatus = new LinkedHashMap<>();
        for (ImageGenerationJobStatus status : ImageGenerationJobStatus.values()) {
            jobsByStatus.put(status.name(), jobs.countByStatus(status));
        }

        Map<String, ProviderAccumulator> providers = new TreeMap<>();
        long successful = jobsByStatus.get(ImageGenerationJobStatus.SUCCEEDED.name())
                + jobsByStatus.get(ImageGenerationJobStatus.PARTIALLY_SUCCEEDED.name());
        long failed = jobsByStatus.get(ImageGenerationJobStatus.FAILED.name());
        long completed = successful + failed + jobsByStatus.get(ImageGenerationJobStatus.CANCELLED.name());

        // 任务数量来自数据库，避免应用重启后 Micrometer 进程内计数归零。
        for (Object[] row : jobs.countByProviderAndStatus()) {
            String provider = String.valueOf(row[0]).toLowerCase(Locale.ROOT);
            ImageGenerationJobStatus status = (ImageGenerationJobStatus) row[1];
            long count = ((Number) row[2]).longValue();
            ProviderAccumulator accumulator = providers.computeIfAbsent(provider, ignored -> new ProviderAccumulator());
            switch (status) {
                case SUCCEEDED, PARTIALLY_SUCCEEDED -> {
                    accumulator.completed += count;
                    accumulator.successful += count;
                }
                case FAILED -> {
                    accumulator.completed += count;
                    accumulator.failed += count;
                }
                case CANCELLED -> accumulator.completed += count;
                default -> { }
            }
        }

        for (Counter counter : registry.find(PREFIX + ".provider.errors").counters()) {
            ProviderAccumulator accumulator = providers.computeIfAbsent(tag(counter, "provider"), ignored -> new ProviderAccumulator());
            long count = Math.round(counter.count());
            accumulator.errors += count;
            if ("timeout".equals(tag(counter, "category"))) accumulator.timeouts += count;
        }
        for (var row : jobs.aggregateCompletedDurationsByProvider()) {
            ProviderAccumulator accumulator = providers.computeIfAbsent(row.getProvider(), ignored -> new ProviderAccumulator());
            accumulator.averageRequestMs = number(row.getAverageMs());
            accumulator.requestP95Ms = number(row.getP95Ms());
        }

        var durationRow = jobs.aggregateCompletedDurations();
        TimerAggregate jobDuration = new TimerAggregate(
                durationRow == null ? 0 : number(durationRow.getAverageMs()),
                durationRow == null ? 0 : number(durationRow.getP95Ms()));
        var providerMetrics = providers.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey())).toList();
        var recentFailures = jobs.findTop20ByStatusOrderByCompletedAtDesc(ImageGenerationJobStatus.FAILED).stream()
                .map(job -> new ImageRuntimeMonitoringResponse.RecentFailure(
                        job.getId(), job.getModelId(), job.getProvider(), String.valueOf(job.getCapability()),
                        job.getErrorCode(), job.getSafeErrorMessage(), Boolean.TRUE.equals(job.getRetryable()), job.getCompletedAt()))
                .toList();

        return new ImageRuntimeMonitoringResponse(
                LocalDateTime.now(), jobsByStatus, gauge(PREFIX + ".worker.active"), completed,
                rate(successful, successful + failed), rate(failed, successful + failed), counter(PREFIX + ".timeouts"),
                counter(PREFIX + ".retries"), counter(PREFIX + ".jobs.recovered"),
                jobDuration.averageMs, jobDuration.p95Ms, providerMetrics, recentFailures);
    }

    private long counter(String name) {
        return Math.round(registry.find(name).counters().stream().mapToDouble(Counter::count).sum());
    }

    private double gauge(String name) {
        return registry.find(name).gauges().stream().mapToDouble(Gauge::value).sum();
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static String tag(Meter meter, String key) {
        String value = meter.getId().getTag(key);
        return value == null ? "unknown" : value;
    }

    private static double number(Number value) {
        return value == null ? 0 : value.doubleValue();
    }

    private record TimerAggregate(double averageMs, double p95Ms) {}

    private static final class ProviderAccumulator {
        long completed;
        long successful;
        long failed;
        long errors;
        long timeouts;
        double averageRequestMs;
        double requestP95Ms;

        ImageRuntimeMonitoringResponse.ProviderMetrics toResponse(String provider) {
            return new ImageRuntimeMonitoringResponse.ProviderMetrics(
                    provider, completed, failed, rate(successful, successful + failed), errors, timeouts,
                    averageRequestMs, requestP95Ms);
        }
    }
}
