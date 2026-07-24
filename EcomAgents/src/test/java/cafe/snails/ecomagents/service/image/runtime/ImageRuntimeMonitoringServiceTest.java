package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.model.ImageGenerationJob;
import cafe.snails.ecomagents.model.ImageGenerationJobStatus;
import cafe.snails.ecomagents.repository.ImageGenerationJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageRuntimeMonitoringServiceTest {
    @Test
    void aggregatesRuntimeMetersAndSafeRecentFailures() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImageGenerationJobRepository jobs = mock(ImageGenerationJobRepository.class);
        when(jobs.countByStatus(ImageGenerationJobStatus.PENDING)).thenReturn(2L);
        when(jobs.countByStatus(ImageGenerationJobStatus.SUCCEEDED)).thenReturn(4L);
        when(jobs.countByStatus(ImageGenerationJobStatus.FAILED)).thenReturn(1L);
        when(jobs.countByProviderAndStatus()).thenReturn(List.of(
                new Object[]{"aliyun_bailian", ImageGenerationJobStatus.SUCCEEDED, 4L},
                new Object[]{"aliyun_bailian", ImageGenerationJobStatus.FAILED, 1L}));
        ImageGenerationJobRepository.DurationAggregate durations =
                mock(ImageGenerationJobRepository.DurationAggregate.class);
        when(durations.getAverageMs()).thenReturn(2000.0);
        when(durations.getP95Ms()).thenReturn(3000.0);
        when(jobs.aggregateCompletedDurations()).thenReturn(durations);
        ImageGenerationJobRepository.ProviderDurationAggregate providerDurations =
                mock(ImageGenerationJobRepository.ProviderDurationAggregate.class);
        when(providerDurations.getProvider()).thenReturn("aliyun_bailian");
        when(providerDurations.getAverageMs()).thenReturn(1500.0);
        when(providerDurations.getP95Ms()).thenReturn(2500.0);
        when(jobs.aggregateCompletedDurationsByProvider()).thenReturn(List.of(providerDurations));
        when(jobs.findTop20ByStatusOrderByCompletedAtDesc(ImageGenerationJobStatus.FAILED)).thenReturn(List.of(
                ImageGenerationJob.builder().id(9L).modelId(3L).provider("ALIYUN_BAILIAN")
                        .status(ImageGenerationJobStatus.FAILED).errorCode("ADAPTER_EXECUTION_FAILED")
                        .safeErrorMessage("图片生成失败").retryable(true).completedAt(LocalDateTime.now()).build()));
        Counter.builder("ecomagents.image.runtime.jobs.completed")
                .tags("provider", "aliyun_bailian", "protocol", "bailian_image", "capability", "text_to_image",
                        "mode", "text_to_image", "outcome", "succeeded")
                .register(registry).increment(4);
        Counter.builder("ecomagents.image.runtime.jobs.completed")
                .tags("provider", "aliyun_bailian", "protocol", "bailian_image", "capability", "text_to_image",
                        "mode", "text_to_image", "outcome", "failed")
                .register(registry).increment();
        Timer.builder("ecomagents.image.runtime.jobs.duration").tags("provider", "aliyun_bailian",
                        "capability", "text_to_image", "outcome", "succeeded")
                .publishPercentiles(0.95).register(registry).record(2, TimeUnit.SECONDS);

        var result = new ImageRuntimeMonitoringService(registry, jobs).snapshot();

        assertEquals(2L, result.jobsByStatus().get("PENDING"));
        assertEquals(5L, result.completed());
        assertEquals(0.8, result.successRate(), 0.001);
        assertEquals(0.2, result.failureRate(), 0.001);
        assertEquals(1, result.providers().size());
        assertEquals(2000.0, result.averageJobDurationMs());
        assertEquals(3000.0, result.p95JobDurationMs());
        assertEquals(1500.0, result.providers().get(0).averageRequestDurationMs());
        assertEquals(2500.0, result.providers().get(0).p95RequestDurationMs());
        assertEquals(9L, result.recentFailures().get(0).id());
        assertEquals("图片生成失败", result.recentFailures().get(0).message());
    }
}
