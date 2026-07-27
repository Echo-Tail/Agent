package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.review.ReviewQueryDtos.UpdateInsightRequest;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewReadServiceTest {
    @Mock ReviewProjectService projectService;
    @Mock ProductReviewRepository reviewRepository;
    @Mock ReviewAnalysisRunRepository runRepository;
    @Mock ReviewInsightRepository insightRepository;
    @Mock ImprovementOpportunityRepository opportunityRepository;
    @InjectMocks ReviewReadService service;

    @Test
    void updateInsight_shouldValidateEvidenceAndMarkManualEdit() {
        var run = ReviewAnalysisRun.builder().id(5L).projectId(2L).status("draft").build();
        var insight = ReviewInsight.builder().id(8L).analysisRunId(5L).reviewId(9L)
                .confidence(BigDecimal.valueOf(.8)).manuallyEdited(false).build();
        var review = ProductReview.builder().id(9L).reviewText("Bluetooth calls have a loud echo.").build();
        when(runRepository.findByIdAndProjectId(5L, 2L)).thenReturn(Optional.of(run));
        when(insightRepository.findById(8L)).thenReturn(Optional.of(insight));
        when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateInsight(2L, 5L, 8L, request("loud echo"), 3L);

        assertTrue(result.manuallyEdited());
        assertEquals("bluetooth_call", result.usageScenario());
        assertEquals("loud echo", result.evidenceQuote());
        verify(projectService).requireOwned(2L, 3L);
    }

    @Test
    void updateInsight_shouldRejectInventedEvidence() {
        var run = ReviewAnalysisRun.builder().id(5L).projectId(2L).status("draft").build();
        var insight = ReviewInsight.builder().id(8L).analysisRunId(5L).reviewId(9L).build();
        when(runRepository.findByIdAndProjectId(5L, 2L)).thenReturn(Optional.of(run));
        when(insightRepository.findById(8L)).thenReturn(Optional.of(insight));
        when(reviewRepository.findById(9L)).thenReturn(Optional.of(
                ProductReview.builder().id(9L).reviewText("No microphone issue").build()));

        assertThrows(BusinessException.class,
                () -> service.updateInsight(2L, 5L, 8L, request("loud echo"), 3L));
        verify(insightRepository, never()).save(any());
    }

    @Test
    void confirm_shouldFreezeDraftAndBeIdempotent() {
        var run = ReviewAnalysisRun.builder().id(5L).projectId(2L).status("draft").build();
        when(runRepository.findByIdAndProjectId(5L, 2L)).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var confirmed = service.confirm(2L, 5L, 3L);
        var repeated = service.confirm(2L, 5L, 3L);

        assertEquals("confirmed", confirmed.getStatus());
        assertEquals(3L, confirmed.getConfirmedBy());
        assertNotNull(confirmed.getConfirmedAt());
        assertSame(confirmed, repeated);
        verify(runRepository, times(1)).save(run);
    }

    @Test
    void dashboard_shouldAggregateCoreDimensions() {
        var run = ReviewAnalysisRun.builder().id(5L).projectId(2L).status("draft").build();
        when(runRepository.findByIdAndProjectId(5L, 2L)).thenReturn(Optional.of(run));
        when(reviewRepository.findByProjectIdOrderById(2L)).thenReturn(List.of(
                ProductReview.builder().asin("OWN").rating(BigDecimal.valueOf(5)).build(),
                ProductReview.builder().asin("OWN").rating(BigDecimal.valueOf(1)).build()));
        when(insightRepository.findByAnalysisRunId(5L)).thenReturn(List.of(
                ReviewInsight.builder().severity("major").usageScenario("bluetooth_call")
                        .productModule("bluetooth_wifi").actionType("firmware").manuallyEdited(true).build()));
        when(opportunityRepository.findByAnalysisRunIdOrderByPriorityScoreDesc(5L))
                .thenReturn(List.of(ImprovementOpportunity.builder().id(1L).build()));

        var result = service.dashboard(2L, 5L, 3L);

        assertEquals(2, result.reviewCount());
        assertEquals(new BigDecimal("3.00"), result.averageRating());
        assertEquals(1, result.opportunityCount());
        assertEquals(1, result.manuallyEditedInsightCount());
        assertEquals(2, result.productReviewCounts().get("OWN"));
    }

    private UpdateInsightRequest request(String quote) {
        return new UpdateInsightRequest("Callers hear echo", "bluetooth_call", "bluetooth_wifi",
                "major", "negative", quote, "firmware", "Tune echo cancellation", 4, 4);
    }
}
