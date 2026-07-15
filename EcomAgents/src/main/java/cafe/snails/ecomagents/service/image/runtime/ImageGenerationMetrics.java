package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.model.ImageGenerationExecutionPhase;
import cafe.snails.ecomagents.model.ImageGenerationJob;
import cafe.snails.ecomagents.model.ImageGenerationJobStatus;
import cafe.snails.ecomagents.repository.ImageGenerationJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ImageGenerationMetrics {
    private static final String PREFIX = "ecomagents.image.runtime";
    private final MeterRegistry registry;

    public ImageGenerationMetrics(MeterRegistry registry, ImageGenerationJobRepository jobs) {
        this.registry = registry;
        for (ImageGenerationJobStatus status : ImageGenerationJobStatus.values()) {
            Gauge.builder(PREFIX + ".jobs", jobs, repository -> repository.countByStatus(status))
                    .description("Current image generation jobs by status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
    }

    public <T> T timeProviderCall(ImageGenerationJob job, String operation, Supplier<T> call) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return call.get();
        } catch (RuntimeException error) {
            providerError(job, operation, error);
            throw error;
        } finally {
            sample.stop(Timer.builder(PREFIX + ".provider.duration")
                    .description("Image provider call latency")
                    .tag("provider", tag(job.getProvider()))
                    .tag("protocol", tag(job.getProtocol()))
                    .tag("capability", tag(job.getCapability()))
                    .tag("operation", operation)
                    .publishPercentiles(0.5, 0.95)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    public void providerError(ImageGenerationJob job, String operation, Throwable error) {
        String category = isTimeout(error) ? "timeout" : "error";
        Counter.builder(PREFIX + ".provider.errors")
                .description("Image provider errors")
                .tag("provider", tag(job.getProvider()))
                .tag("protocol", tag(job.getProtocol()))
                .tag("capability", tag(job.getCapability()))
                .tag("operation", operation)
                .tag("category", category)
                .register(registry).increment();
        if ("timeout".equals(category)) {
            Counter.builder(PREFIX + ".timeouts")
                    .description("Image runtime timeouts")
                    .tag("provider", tag(job.getProvider()))
                    .tag("phase", phase(job.getExecutionPhase()))
                    .register(registry).increment();
        }
    }

    public void retryScheduled(ImageGenerationJob job) {
        Counter.builder(PREFIX + ".retries")
                .description("Image job retries scheduled")
                .tag("provider", tag(job.getProvider()))
                .tag("capability", tag(job.getCapability()))
                .register(registry).increment();
    }

    public void terminal(ImageGenerationJob job) {
        Counter.builder(PREFIX + ".jobs.completed")
                .description("Terminal image jobs")
                .tag("provider", tag(job.getProvider()))
                .tag("protocol", tag(job.getProtocol()))
                .tag("capability", tag(job.getCapability()))
                .tag("mode", tag(job.getMode()))
                .tag("outcome", tag(job.getStatus()))
                .register(registry).increment();
        LocalDateTime start = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
        if (start != null && job.getCompletedAt() != null) {
            long millis = Math.max(0, Duration.between(start, job.getCompletedAt()).toMillis());
            Timer.builder(PREFIX + ".jobs.duration")
                    .description("End-to-end image job duration")
                    .tag("provider", tag(job.getProvider()))
                    .tag("capability", tag(job.getCapability()))
                    .tag("outcome", tag(job.getStatus()))
                    .publishPercentiles(0.5, 0.95)
                    .publishPercentileHistogram()
                    .register(registry).record(millis, TimeUnit.MILLISECONDS);
        }
    }

    public void recovered(int count) { increment("jobs.recovered", count); }
    public void cancellationFinalized(int count) { increment("jobs.cancellations.finalized", count); }

    public void bindActiveJobs(Supplier<Number> supplier) {
        Gauge.builder(PREFIX + ".worker.active", supplier)
                .description("Locally active image jobs")
                .register(registry);
    }

    private void increment(String suffix, int count) {
        if (count > 0) Counter.builder(PREFIX + "." + suffix).register(registry).increment(count);
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.util.concurrent.TimeoutException) return true;
            String name = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase(Locale.ROOT);
            if (name.contains("timeout") || message.contains("timeout") || message.contains("超时")) return true;
        }
        return false;
    }

    private static String phase(ImageGenerationExecutionPhase phase) {
        return phase == null ? "unknown" : phase.name().toLowerCase(Locale.ROOT);
    }

    private static String tag(Object value) {
        return value == null ? "unknown" : value.toString().toLowerCase(Locale.ROOT);
    }
}
