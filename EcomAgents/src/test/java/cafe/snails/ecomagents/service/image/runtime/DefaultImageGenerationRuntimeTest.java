package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import cafe.snails.ecomagents.service.*;
import cafe.snails.ecomagents.service.image.runtime.command.TextToImageCommand;
import cafe.snails.ecomagents.service.image.runtime.storage.ImageInputSnapshotStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultImageGenerationRuntimeTest {
    @Mock ImageGenerationJobRepository jobs;
    @Mock ImageGenerationJobInputRepository inputs;
    @Mock ImageGenerationRecordRepository records;
    @Mock ModelCapabilityResolver resolver;
    @Mock ImageInputSnapshotStorage storage;
    DefaultImageGenerationRuntime runtime;

    @BeforeEach void setUp() {
        runtime = new DefaultImageGenerationRuntime(jobs, inputs, records, resolver, storage);
    }

    @Test
    void submitShouldPersistResolvedNonSensitiveSnapshot() {
        when(resolver.resolve(8L, ModelCapability.TEXT_TO_IMAGE)).thenReturn(
                new ResolvedModelCapability(8L, ModelCapability.TEXT_TO_IMAGE, ModelProtocol.BAILIAN_IMAGE,
                        "qwen", "wanx-v1", "https://maas.example/v1", 3L, "must-not-persist", null));
        when(jobs.save(any())).thenAnswer(invocation -> {
            ImageGenerationJob job = invocation.getArgument(0);
            job.setId(99L);
            return job;
        });

        ImageGenerationJob job = runtime.submit(new TextToImageCommand(7L, 8L, "  mountain  ", null, 2, null));

        assertEquals(99L, job.getId());
        assertEquals(ImageGenerationJobStatus.PENDING, job.getStatus());
        assertEquals(ModelProtocol.BAILIAN_IMAGE, job.getProtocol());
        assertEquals("wanx-v1", job.getRemoteModelName());
        assertEquals(3L, job.getCredentialId());
        assertEquals("mountain", job.getPrompt());
        verifyNoInteractions(storage, inputs);
    }

    @Test
    void getShouldNotRevealAnotherUsersJob() {
        when(jobs.findByIdAndUserId(10L, 7L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> runtime.get(10L, 7L));
    }

    @Test
    void cancelPendingShouldBecomeCancelledWithoutWorkerRoundTrip() {
        ImageGenerationJob job = ImageGenerationJob.builder().id(10L).userId(7L)
                .status(ImageGenerationJobStatus.PENDING).build();
        when(jobs.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(job));
        when(jobs.save(job)).thenReturn(job);
        assertEquals(ImageGenerationJobStatus.CANCELLED, runtime.cancel(10L, 7L).getStatus());
    }
}
