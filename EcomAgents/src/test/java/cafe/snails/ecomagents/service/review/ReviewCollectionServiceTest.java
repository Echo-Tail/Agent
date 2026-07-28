package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewCollectionServiceTest {
    @Mock ReviewAnalysisProjectRepository projectRepository;
    @Mock ReviewProjectProductRepository productRepository;
    @Mock ReviewCollectionBatchRepository batchRepository;
    @Mock ProductReviewRepository reviewRepository;
    @Mock cafe.snails.ecomagents.service.BrightDataService brightDataService;
    ReviewCollectionService service;
    ReviewNormalizationService normalizationService;

    @BeforeEach
    void setUp() {
        normalizationService = new ReviewNormalizationService(new ObjectMapper());
        service = new ReviewCollectionService(projectRepository, productRepository, batchRepository,
                reviewRepository, brightDataService, normalizationService);
        lenient().when(batchRepository.save(any())).thenAnswer(invocation -> {
            ReviewCollectionBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(21L);
            return batch;
        });
        lenient().when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void start_shouldTriggerReviewDatasetAndReturnRunningBatch() {
        var project = project("draft");
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project));
        when(batchRepository.findByProjectIdAndIdempotencyKey(11L, "key-1")).thenReturn(Optional.empty());
        when(productRepository.findByProjectIdOrderById(11L)).thenReturn(List.of(product("B0AAAA1111", 300)));
        when(brightDataService.trigger(any(), eq(3L))).thenReturn(ApiResponse.success(
                BrightDataTriggerResponse.builder().snapshotId("s_123").datasetId(
                        ReviewCollectionService.AMAZON_REVIEWS_DATASET_ID).recordId(8L).build()));

        var response = service.start(11L, "key-1", 3L);

        assertEquals("running", response.status());
        assertEquals("s_123", response.snapshotId());
        assertEquals("collecting", project.getStatus());
        ArgumentCaptor<BrightDataTriggerRequest> request = ArgumentCaptor.forClass(BrightDataTriggerRequest.class);
        verify(brightDataService).trigger(request.capture(), eq(3L));
        assertEquals(ReviewCollectionService.AMAZON_REVIEWS_DATASET_ID, request.getValue().getDatasetId());
        assertEquals(300, request.getValue().getLimitPerInput());
    }

    @Test
    void start_shouldReturnExistingBatchForSameIdempotencyKey() {
        var existing = batch("running");
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project("collecting")));
        when(batchRepository.findByProjectIdAndIdempotencyKey(11L, "key-1")).thenReturn(Optional.of(existing));

        var response = service.start(11L, "key-1", 3L);

        assertEquals(21L, response.id());
        verifyNoInteractions(brightDataService);
    }

    @Test
    void progress_shouldDownloadNormalizeAndPersistReadySnapshot() {
        var project = project("collecting");
        var batch = batch("running");
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project));
        when(batchRepository.findByIdAndProjectId(21L, 11L)).thenReturn(Optional.of(batch));
        when(brightDataService.getProgress("s_123")).thenReturn(ApiResponse.success(
                BrightDataSnapshotStatus.builder().snapshotId("s_123").status("ready").build()));
        when(brightDataService.downloadSnapshot("s_123", "json")).thenReturn(ApiResponse.success(List.of(
                Map.of("asin", "B0AAAA1111", "review_id", "R1", "rating", 2,
                        "review_text", "CarPlay disconnects.", "verified_purchase", true))));
        when(productRepository.findByProjectIdOrderById(11L)).thenReturn(List.of(product("B0AAAA1111", 100)));
        when(reviewRepository.countByProjectIdAndAsin(11L, "B0AAAA1111")).thenReturn(0L);
        when(reviewRepository.findByProjectIdAndAsinAndExternalReviewId(11L, "B0AAAA1111", "R1"))
                .thenReturn(Optional.empty());
        when(reviewRepository.findByProjectIdAndAsinAndContentHash(eq(11L), eq("B0AAAA1111"), anyString()))
                .thenReturn(Optional.empty());

        var response = service.progress(11L, 21L, 3L);

        assertEquals("success", response.status());
        assertEquals(1, response.collectedCount());
        assertEquals("collected", project.getStatus());
        verify(reviewRepository).saveAll(argThat(values -> values.iterator().hasNext()));
    }

    @Test
    void progress_shouldMarkPartialWhenSomeRowsAreInvalid() {
        var project = project("collecting");
        var batch = batch("running");
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project));
        when(batchRepository.findByIdAndProjectId(21L, 11L)).thenReturn(Optional.of(batch));
        when(brightDataService.getProgress("s_123")).thenReturn(ApiResponse.success(
                BrightDataSnapshotStatus.builder().snapshotId("s_123").status("ready").build()));
        when(brightDataService.downloadSnapshot("s_123", "json")).thenReturn(ApiResponse.success(List.of(
                Map.of("asin", "B0AAAA1111", "review_text", "Screen freezes."),
                Map.of("error", "not found"))));
        when(productRepository.findByProjectIdOrderById(11L)).thenReturn(List.of(product("B0AAAA1111", 100)));
        when(reviewRepository.countByProjectIdAndAsin(11L, "B0AAAA1111")).thenReturn(0L);
        when(reviewRepository.findByProjectIdAndAsinAndContentHash(eq(11L), eq("B0AAAA1111"), anyString()))
                .thenReturn(Optional.empty());

        var response = service.progress(11L, 21L, 3L);

        assertEquals("partial", response.status());
        assertEquals(1, response.collectedCount());
        assertNotNull(response.errorMessage());
        assertEquals("collected", project.getStatus());
    }

    private ReviewAnalysisProject project(String status) {
        return ReviewAnalysisProject.builder().id(11L).profileId(7L).createdBy(3L)
                .name("Reviews").status(status).build();
    }

    private ReviewProjectProduct product(String asin, int limit) {
        return ReviewProjectProduct.builder().id(31L).projectId(11L).asin(asin)
                .role("own").reviewLimit(limit).build();
    }

    private ReviewCollectionBatch batch(String status) {
        return ReviewCollectionBatch.builder().id(21L).projectId(11L).snapshotId("s_123")
                .datasetId(ReviewCollectionService.AMAZON_REVIEWS_DATASET_ID)
                .idempotencyKey("key-1").status(status).requestedCount(100).collectedCount(0).build();
    }
}
