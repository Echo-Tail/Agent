package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.model.ImageSuperResolutionJob;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import cafe.snails.ecomagents.repository.ImageSuperResolutionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageSuperResolutionJobServiceTest {
    @Mock private ImageSuperResolutionJobRepository jobRepository;
    @Mock private ImageGenerationRecordRepository recordRepository;
    @Mock private ImageSuperResolutionService superResolutionService;
    @Mock private Executor executor;

    @TempDir Path tempDir;

    private ImageSuperResolutionJobService service;
    private ImageGenerationRecord source;

    @BeforeEach
    void setUp() {
        service = new ImageSuperResolutionJobService(jobRepository, recordRepository, superResolutionService, executor);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        source = ImageGenerationRecord.builder()
                .id(10L)
                .userId(7L)
                .mode("GENERATE")
                .prompt("product photo")
                .revisedPrompt("revised product photo")
                .quality("high")
                .resultPath("/uploads/generated/source.png")
                .width(1024)
                .height(1024)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void submitPersistsPendingJobAndSchedulesWork() {
        when(superResolutionService.validateSourceRecord(10L, 7L)).thenReturn(source);
        when(jobRepository.saveAndFlush(any(ImageSuperResolutionJob.class))).thenAnswer(invocation -> {
            ImageSuperResolutionJob job = invocation.getArgument(0);
            job.setId(100L);
            return job;
        });

        var response = service.submit(new ImageSuperResolutionJobService.CreateJobRequest(10L, 3, "IMAGE_GENERATION"), 7L);

        assertEquals(100L, response.id());
        assertEquals("PENDING", response.status());
        assertEquals(3, response.upscaleFactor());
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void submitRejectsUnsupportedFactor() {
        assertThrows(BusinessException.class,
                () -> service.submit(new ImageSuperResolutionJobService.CreateJobRequest(10L, 1, "IMAGE_GENERATION"), 7L));
        verifyNoInteractions(superResolutionService, executor);
    }

    @Test
    void submitUploadStoresTemporarySourceAndSchedulesWork() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        var file = new MockMultipartFile("file", "source.png", "image/png", bytes);
        when(superResolutionService.validateUploadedSource(bytes)).thenReturn(new int[]{800, 600});
        when(jobRepository.saveAndFlush(any(ImageSuperResolutionJob.class))).thenAnswer(invocation -> {
            ImageSuperResolutionJob job = invocation.getArgument(0);
            job.setId(101L);
            return job;
        });

        var response = service.submitUpload(file, 4, "SUPER_RESOLUTION_PAGE", 7L);

        assertEquals("UPLOAD", response.sourceType());
        assertEquals("SUPER_RESOLUTION_PAGE", response.origin());
        assertEquals(800, response.sourceWidth());
        ArgumentCaptor<ImageSuperResolutionJob> savedJob = ArgumentCaptor.forClass(ImageSuperResolutionJob.class);
        verify(jobRepository).saveAndFlush(savedJob.capture());
        assertTrue(savedJob.getValue().getSourceFingerprint().length() <= 64);
        assertTrue(Files.exists(tempDir.resolve(response.sourcePath().replaceFirst("^/?uploads/", ""))));
        verify(executor).execute(any(Runnable.class));
    }
    @Test
    void scheduledWorkCreatesSuperResolutionHistory() {
        when(superResolutionService.validateSourceRecord(10L, 7L)).thenReturn(source);
        when(jobRepository.saveAndFlush(any(ImageSuperResolutionJob.class))).thenAnswer(invocation -> {
            ImageSuperResolutionJob job = invocation.getArgument(0);
            if (job.getId() == null) job.setId(100L);
            return job;
        });
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        service.submit(new ImageSuperResolutionJobService.CreateJobRequest(10L, 2, "IMAGE_GENERATION"), 7L);
        verify(executor).execute(taskCaptor.capture());

        ImageSuperResolutionJob persistedJob = ImageSuperResolutionJob.builder()
                .id(100L).userId(7L).sourceRecordId(10L).sourceType("HISTORY").origin("IMAGE_GENERATION")
                .sourcePath("/uploads/generated/source.png").sourceWidth(1024).sourceHeight(1024)
                .upscaleFactor(2).status("PENDING").createdAt(LocalDateTime.now()).build();
        when(jobRepository.findById(100L)).thenReturn(Optional.of(persistedJob));
        when(recordRepository.findById(10L)).thenReturn(Optional.of(source));
        when(superResolutionService.upscale(any(), eq(7L))).thenReturn(
                new ImageSuperResolutionService.SuperResolutionResult(
                        source.getResultPathNormalized(), "https://example.test/output.png",
                        "/uploads/super-resolution/output.png", "base", 2, "png",
                        2048, 2048, 1200L));
        when(recordRepository.save(any(ImageGenerationRecord.class))).thenAnswer(invocation -> {
            ImageGenerationRecord record = invocation.getArgument(0);
            record.setId(20L);
            return record;
        });

        taskCaptor.getValue().run();

        ArgumentCaptor<ImageGenerationRecord> historyCaptor = ArgumentCaptor.forClass(ImageGenerationRecord.class);
        verify(recordRepository).save(historyCaptor.capture());
        ImageGenerationRecord history = historyCaptor.getValue();
        assertEquals("SUPER_RESOLUTION", history.getMode());
        assertEquals("product photo", history.getPrompt());
        assertEquals("2048x2048", history.getSize());
        assertEquals(10L, history.getSourceRecordId());
        assertEquals(2, history.getUpscaleFactor());
        assertEquals("SUCCEEDED", persistedJob.getStatus());
        assertEquals(20L, persistedJob.getResultRecordId());
    }
}