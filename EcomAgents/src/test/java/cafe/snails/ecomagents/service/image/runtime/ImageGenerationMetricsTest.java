package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.ImageGenerationJobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageGenerationMetricsTest {

    @Test
    void recordsTerminalOutcomeDurationAndQueueGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImageGenerationJobRepository jobs = mock(ImageGenerationJobRepository.class);
        when(jobs.countByStatus(ImageGenerationJobStatus.PENDING)).thenReturn(3L);
        ImageGenerationMetrics metrics = new ImageGenerationMetrics(registry, jobs);
        ImageGenerationJob job = job();
        job.setStatus(ImageGenerationJobStatus.PARTIALLY_SUCCEEDED);
        job.setStartedAt(LocalDateTime.now().minusSeconds(2));
        job.setCompletedAt(LocalDateTime.now());

        metrics.terminal(job);

        assertEquals(3.0, registry.get("ecomagents.image.runtime.jobs")
                .tag("status", "pending").gauge().value());
        assertEquals(1.0, registry.get("ecomagents.image.runtime.jobs.completed")
                .tag("outcome", "partially_succeeded").counter().count());
        assertTrue(registry.get("ecomagents.image.runtime.jobs.duration")
                .tag("outcome", "partially_succeeded").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 1900);
    }

    @Test
    void classifiesProviderTimeoutAndRecordsProviderLatency() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImageGenerationMetrics metrics = new ImageGenerationMetrics(registry, mock(ImageGenerationJobRepository.class));
        ImageGenerationJob job = job();
        job.setExecutionPhase(ImageGenerationExecutionPhase.POLLING);

        assertThrows(IllegalStateException.class, () -> metrics.timeProviderCall(job, "poll", () -> {
            throw new IllegalStateException("wrapped", new TimeoutException("provider timeout"));
        }));

        assertEquals(1.0, registry.get("ecomagents.image.runtime.provider.errors")
                .tag("operation", "poll").tag("category", "timeout").counter().count());
        assertEquals(1.0, registry.get("ecomagents.image.runtime.timeouts")
                .tag("phase", "polling").counter().count());
        assertEquals(1L, registry.get("ecomagents.image.runtime.provider.duration")
                .tag("operation", "poll").timer().count());
    }

    private ImageGenerationJob job() {
        return ImageGenerationJob.builder()
                .provider("ALIYUN_BAILIAN")
                .protocol(ModelProtocol.BAILIAN_IMAGE)
                .capability(ModelCapability.TEXT_TO_IMAGE)
                .mode(ImageGenerationMode.TEXT_TO_IMAGE)
                .status(ImageGenerationJobStatus.RUNNING)
                .build();
    }
}
