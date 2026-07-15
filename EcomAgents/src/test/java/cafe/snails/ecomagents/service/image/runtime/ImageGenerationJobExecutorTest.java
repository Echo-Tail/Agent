package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import cafe.snails.ecomagents.service.ModelCredentialService;
import cafe.snails.ecomagents.service.image.runtime.provider.*;
import cafe.snails.ecomagents.service.image.runtime.storage.ImageOutputStorage;
import cafe.snails.ecomagents.service.image.runtime.storage.ImageRemoteDownloader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageGenerationJobExecutorTest {
    @Mock ImageGenerationJobRepository jobs;
    @Mock ImageGenerationJobInputRepository inputs;
    @Mock ImageGenerationRecordRepository records;
    @Mock AiModelRepository models;
    @Mock ModelCredentialService credentials;
    @TempDir Path tempDir;

    @Test
    void mockAdapterShouldRunJobToSucceededAndPersistOrderedOutputs() {
        ImageGenerationJob job = pendingJob();
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inputs.findByJobIdOrderByInputIndex(5L)).thenReturn(List.of());
        when(models.findById(2L)).thenReturn(Optional.of(AiModel.builder().apiKey("legacy-test-key").build()));
        ImageOutputStorage storage = new ImageOutputStorage();
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        var executor = new ImageGenerationJobExecutor(jobs, inputs, records, models, credentials,
                new ImageAdapterRegistry(List.of(new MockImageAdapter())), storage, mock(ImageRemoteDownloader.class),
                mock(ImageGenerationUsageRecorder.class), metrics());

        ImageGenerationJob completed = executor.execute(5L);

        assertEquals(ImageGenerationJobStatus.SUCCEEDED, completed.getStatus());
        assertEquals(2, completed.getSuccessCount());
        assertEquals(0, completed.getFailureCount());
        assertNull(completed.getExecutionPhase());
        verify(records, times(2)).save(argThat(record -> record.getJobId().equals(5L)
                && "SUCCEEDED".equals(record.getStatus())
                && record.getResultPath().startsWith("/uploads/image-jobs/5/outputs/")));
    }

    @Test
    void adapterFailureShouldPersistSafeFailedState() {
        ImageGenerationJob job = pendingJob();
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inputs.findByJobIdOrderByInputIndex(5L)).thenReturn(List.of());
        when(models.findById(2L)).thenReturn(Optional.of(AiModel.builder().apiKey("key").build()));
        ImageGenerationProviderAdapter failing = new ImageGenerationProviderAdapter() {
            public boolean supports(ModelProtocol protocol, ModelCapability capability) { return true; }
            public List<GeneratedProviderImage> generate(ImageGenerationJob ignored,
                    List<ImageGenerationJobInput> ignoredInputs, String ignoredSecret) {
                throw new IllegalStateException("provider internal secret");
            }
        };
        var executor = new ImageGenerationJobExecutor(jobs, inputs, records, models, credentials,
                new ImageAdapterRegistry(List.of(failing)), mock(ImageOutputStorage.class), mock(ImageRemoteDownloader.class),
                mock(ImageGenerationUsageRecorder.class), metrics());

        ImageGenerationJob failed = executor.execute(5L);

        assertEquals(ImageGenerationJobStatus.FAILED, failed.getStatus());
        assertEquals("SUBMISSION_OUTCOME_UNKNOWN", failed.getErrorCode());
        assertFalse(failed.getSafeErrorMessage().contains("provider internal secret"));
        assertFalse(failed.getRetryable());
        verifyNoInteractions(records);
    }

    @Test
    void claimedExecutionShouldRejectDifferentWorker() {
        ImageGenerationJob job = pendingJob();
        job.setStatus(ImageGenerationJobStatus.RUNNING);
        job.setWorkerId("worker-a");
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        var executor = new ImageGenerationJobExecutor(jobs, inputs, records, models, credentials,
                new ImageAdapterRegistry(List.of(new MockImageAdapter())), mock(ImageOutputStorage.class), mock(ImageRemoteDownloader.class),
                mock(ImageGenerationUsageRecorder.class), metrics());
        assertThrows(cafe.snails.ecomagents.exception.BusinessException.class,
                () -> executor.executeClaimed(5L, "worker-b"));
        verifyNoInteractions(inputs, records);
    }

    @Test
    void preparationFailureShouldScheduleExponentialRetryBeforeSubmission() {
        ImageGenerationJob job = pendingJob();
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inputs.findByJobIdOrderByInputIndex(5L)).thenReturn(List.of());
        when(models.findById(2L)).thenReturn(Optional.of(AiModel.builder().apiKey("key").build()));
        ImageGenerationProviderAdapter transientPreparationFailure = new MockImageAdapter() {
            @Override public void validate(ImageGenerationJob ignored, List<ImageGenerationJobInput> ignoredInputs) {
                throw new IllegalStateException("temporary preparation failure");
            }
        };
        var executor = new ImageGenerationJobExecutor(jobs, inputs, records, models, credentials,
                new ImageAdapterRegistry(List.of(transientPreparationFailure)), mock(ImageOutputStorage.class),
                mock(ImageRemoteDownloader.class), mock(ImageGenerationUsageRecorder.class), metrics());

        ImageGenerationJob retrying = executor.execute(5L);

        assertEquals(ImageGenerationJobStatus.PENDING, retrying.getStatus());
        assertEquals("PREPARATION_RETRY_SCHEDULED", retrying.getErrorCode());
        assertNotNull(retrying.getNextAttemptAt());
        assertNull(retrying.getCompletedAt());
        assertTrue(retrying.getRetryable());
    }

    @Test
    void asyncAdapterShouldPersistTaskTokenBeforePollingAndComplete() {
        ImageGenerationJob job = pendingJob();
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobs.findStatusById(5L)).thenReturn(Optional.of(ImageGenerationJobStatus.RUNNING));
        when(inputs.findByJobIdOrderByInputIndex(5L)).thenReturn(List.of());
        when(models.findById(2L)).thenReturn(Optional.of(AiModel.builder().apiKey("key").build()));
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        ImageGenerationProviderAdapter async = new ImageGenerationProviderAdapter() {
            public boolean supports(ModelProtocol protocol, ModelCapability capability) { return true; }
            public List<GeneratedProviderImage> generate(ImageGenerationJob ignored,
                    List<ImageGenerationJobInput> ignoredInputs, String ignoredSecret) { throw new UnsupportedOperationException(); }
            public ProviderSubmission submit(ImageGenerationJob ignored, List<ImageGenerationJobInput> ignoredInputs,
                    String ignoredSecret) { return ProviderSubmission.accepted("task-123"); }
            public ProviderPollResult poll(ImageGenerationJob pollingJob, String ignoredSecret) {
                assertEquals("task-123", pollingJob.getProviderTaskToken());
                return ProviderPollResult.succeeded(List.of(GeneratedProviderImage.inline(png, "image/png", null),
                        GeneratedProviderImage.inline(png, "image/png", null)));
            }
        };
        ImageOutputStorage storage = new ImageOutputStorage();
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        var executor = new ImageGenerationJobExecutor(jobs, inputs, records, models, credentials,
                new ImageAdapterRegistry(List.of(async)), storage, mock(ImageRemoteDownloader.class),
                mock(ImageGenerationUsageRecorder.class), metrics());

        ImageGenerationJob completed = executor.execute(5L);

        assertEquals(ImageGenerationJobStatus.SUCCEEDED, completed.getStatus());
        assertEquals("task-123", completed.getProviderTaskToken());
        assertEquals("SUCCEEDED", completed.getProviderStatus());
        verify(jobs, atLeast(1)).save(argThat(saved -> "task-123".equals(saved.getProviderTaskToken())));
    }

    @Test
    void fewerProviderOutputsShouldCompleteAsPartialSuccess() {
        ImageGenerationJob job = pendingJob();
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inputs.findByJobIdOrderByInputIndex(5L)).thenReturn(List.of());
        when(models.findById(2L)).thenReturn(Optional.of(AiModel.builder().apiKey("key").build()));
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        ImageGenerationProviderAdapter partial = new MockImageAdapter() {
            @Override public List<GeneratedProviderImage> generate(ImageGenerationJob ignored,
                    List<ImageGenerationJobInput> ignoredInputs, String ignoredSecret) {
                return List.of(GeneratedProviderImage.inline(png, "image/png", null));
            }
        };
        ImageOutputStorage storage = new ImageOutputStorage();
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        var executor = new ImageGenerationJobExecutor(jobs, inputs, records, models, credentials,
                new ImageAdapterRegistry(List.of(partial)), storage, mock(ImageRemoteDownloader.class),
                mock(ImageGenerationUsageRecorder.class), metrics());

        ImageGenerationJob completed = executor.execute(5L);

        assertEquals(ImageGenerationJobStatus.PARTIALLY_SUCCEEDED, completed.getStatus());
        assertEquals(1, completed.getSuccessCount());
        assertEquals(1, completed.getFailureCount());
        assertTrue(completed.getRetryable());
    }

    @Test
    void cancellationRequestShouldCancelProviderPollingAndFinalizeJob() {
        ImageGenerationJob job = pendingJob();
        job.setStatus(ImageGenerationJobStatus.RUNNING);
        job.setWorkerId("worker-a");
        job.setProviderTaskToken("task-123");
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        when(jobs.findStatusById(5L)).thenReturn(Optional.of(ImageGenerationJobStatus.CANCEL_REQUESTED));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inputs.findByJobIdOrderByInputIndex(5L)).thenReturn(List.of());
        when(models.findById(2L)).thenReturn(Optional.of(AiModel.builder().apiKey("key").build()));
        ImageGenerationProviderAdapter cancellable = new MockImageAdapter() {
            @Override public boolean cancel(ImageGenerationJob ignored, String ignoredSecret) { return true; }
        };
        var executor = new ImageGenerationJobExecutor(jobs, inputs, records, models, credentials,
                new ImageAdapterRegistry(List.of(cancellable)), mock(ImageOutputStorage.class),
                mock(ImageRemoteDownloader.class), mock(ImageGenerationUsageRecorder.class), metrics());

        ImageGenerationJob cancelled = executor.executeClaimed(5L, "worker-a");

        assertEquals(ImageGenerationJobStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCompletedAt());
        assertNull(cancelled.getWorkerId());
        verifyNoInteractions(records);
    }

    private ImageGenerationJob pendingJob() {
        return ImageGenerationJob.builder().id(5L).userId(7L).modelId(2L)
                .mode(ImageGenerationMode.TEXT_TO_IMAGE).prompt("test").targetCount(2)
                .provider("mock").protocol(ModelProtocol.OPENAI_IMAGE)
                .remoteModelName("mock-image").apiUrl("https://mock.invalid")
                .capability(ModelCapability.TEXT_TO_IMAGE).status(ImageGenerationJobStatus.PENDING).build();
    }

    private ImageGenerationMetrics metrics() {
        ImageGenerationMetrics metrics = mock(ImageGenerationMetrics.class);
        lenient().when(metrics.timeProviderCall(any(), anyString(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(2)).get());
        return metrics;
    }
}
