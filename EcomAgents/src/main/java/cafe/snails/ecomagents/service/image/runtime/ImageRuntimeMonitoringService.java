package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.dto.ImageRuntimeMonitoringResponse;
import cafe.snails.ecomagents.model.ImageGenerationJobStatus;
import cafe.snails.ecomagents.repository.ImageGenerationJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

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
        long completed = 0;
        long successful = 0;
        long failed = 0;
        for (Counter counter : registry.find(PREFIX + ".jobs.completed").counters()) {
            long count = Math.round(counter.count());
            String provider = tag(counter, "provider");
            String outcome = tag(counter, "outcome");
            ProviderAccumulator accumulator = providers.computeIfAbsent(provider, ignored -> new ProviderAccumulator());
            accumulator.completed += count;
            completed += count;
            if ("failed".equals(outcome)) {
                accumulator.failed += count;
                failed += count;
            } else if ("succeeded".equals(outcome) || "partially_succeeded".equals(outcome)) {
                accumulator.successful += count;
                successful += count;
            }
        }

        for (Counter counter : registry.find(PREFIX + ".provider.errors").counters()) {
            ProviderAccumulator accumulator = providers.computeIfAbsent(tag(counter, "provider"), ignored -> new ProviderAccumulator());
            long count = Math.round(counter.count());
            accumulator.errors += count;
            if ("timeout".equals(tag(counter, "category"))) accumulator.timeouts += count;
        }
        for (Timer timer : registry.find(PREFIX + ".provider.duration").timers()) {
            ProviderAccumulator accumulator = providers.computeIfAbsent(tag(timer, "provider"), ignored -> new ProviderAccumulator());
            accumulator.requestCount += timer.count();
            accumulator.requestTotalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            accumulator.requestP95Ms = Math.max(accumulator.requestP95Ms, percentile(timer, 0.95));
        }

        TimerAggregate jobDuration = timerAggregate(PREFIX + ".jobs.duration");
        var providerMetrics = providers.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey())).toList();
        var recentFailures = jobs.findTop20ByStatusOrderByCompletedAtDesc(ImageGenerationJobStatus.FAILED).stream()
                .map(job -> new ImageRuntimeMonitoringResponse.RecentFailure(
                        job.getId(), job.getModelId(), job.getProvider(), String.valueOf(job.getCapability()),
                        job.getErrorCode(), job.getSafeErrorMessage(), Boolean.TRUE.equals(job.getRetryable()), job.getCompletedAt()))
                .toList();

        return new ImageRuntimeMonitoringResponse(
                LocalDateTime.now(), jobsByStatus, gauge(PREFIX + ".worker.active"), completed,
                rate(successful, completed), rate(failed, completed), counter(PREFIX + ".timeouts"),
                counter(PREFIX + ".retries"), counter(PREFIX + ".jobs.recovered"),
                jobDuration.averageMs, jobDuration.p95Ms, providerMetrics, recentFailures);
    }

    private TimerAggregate timerAggregate(String name) {
        long count = 0;
        double total = 0;
        double p95 = 0;
        for (Timer timer : registry.find(name).timers()) {
            count += timer.count();
            total += timer.totalTime(TimeUnit.MILLISECONDS);
            p95 = Math.max(p95, percentile(timer, 0.95));
        }
        return new TimerAggregate(count == 0 ? 0 : total / count, p95);
    }

    private double percentile(Timer timer, double wanted) {
        return Arrays.stream(timer.takeSnapshot().percentileValues())
                .filter(value -> Math.abs(value.percentile() - wanted) < 0.001)
                .mapToDouble(value -> value.value(TimeUnit.MILLISECONDS)).findFirst().orElse(0);
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

    private record TimerAggregate(double averageMs, double p95Ms) {}

    private static final class ProviderAccumulator {
        long completed;
        long successful;
        long failed;
        long errors;
        long timeouts;
        long requestCount;
        double requestTotalMs;
        double requestP95Ms;

        ImageRuntimeMonitoringResponse.ProviderMetrics toResponse(String provider) {
            return new ImageRuntimeMonitoringResponse.ProviderMetrics(
                    provider, completed, failed, rate(successful, completed), errors, timeouts,
                    requestCount == 0 ? 0 : requestTotalMs / requestCount, requestP95Ms);
        }
    }
}
