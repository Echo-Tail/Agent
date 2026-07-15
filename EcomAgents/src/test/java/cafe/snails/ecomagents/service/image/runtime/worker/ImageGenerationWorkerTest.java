package cafe.snails.ecomagents.service.image.runtime.worker;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageGenerationWorkerTest {
    @Mock ImageJobLeaseStore leases;
    @Mock ImageGenerationWorkerRunner runner;

    @Test
    void shouldNotClaimBeyondLocalConcurrencyAndShouldResumeAfterCompletion() {
        var worker = new ImageGenerationWorker(leases, runner, "worker-a", 1, 60, mock(cafe.snails.ecomagents.service.image.runtime.ImageGenerationMetrics.class));
        when(leases.claimNext(eq("worker-a"), any(Duration.class)))
                .thenReturn(Optional.of(1L), Optional.of(2L));

        worker.poll();
        worker.poll();

        verify(leases, times(1)).claimNext(eq("worker-a"), any(Duration.class));
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        verify(runner).run(eq(1L), eq("worker-a"), completion.capture());
        assertEquals(java.util.Set.of(1L), worker.activeJobs());

        completion.getValue().run();
        worker.poll();
        verify(runner).run(eq(2L), eq("worker-a"), any(Runnable.class));
    }

    @Test
    void heartbeatShouldDropJobWhenLeaseOwnershipWasLost() {
        var worker = new ImageGenerationWorker(leases, runner, "worker-a", 1, 60, mock(cafe.snails.ecomagents.service.image.runtime.ImageGenerationMetrics.class));
        when(leases.claimNext(eq("worker-a"), any(Duration.class))).thenReturn(Optional.of(1L));
        worker.poll();
        when(leases.heartbeat(eq(1L), eq("worker-a"), any(Duration.class))).thenReturn(false);

        worker.heartbeat();

        assertTrue(worker.activeJobs().isEmpty());
    }

    @Test
    void recoveryShouldHandleExpiredJobsAndCancellationRequests() {
        var worker = new ImageGenerationWorker(leases, runner, "worker-a", 1, 60, mock(cafe.snails.ecomagents.service.image.runtime.ImageGenerationMetrics.class));
        when(leases.recoverExpired()).thenReturn(2);
        when(leases.finishRequestedCancellations()).thenReturn(1);
        worker.recover();
        verify(leases).recoverExpired();
        verify(leases).finishRequestedCancellations();
    }
}
