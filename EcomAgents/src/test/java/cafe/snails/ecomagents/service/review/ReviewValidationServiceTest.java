package cafe.snails.ecomagents.service.review;

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
class ReviewValidationServiceTest {
    @Mock ReviewProjectService projectService;
    @Mock ReviewAnalysisRunRepository runRepository;
    @Mock ReviewInsightRepository insightRepository;
    @Mock ProductReviewRepository reviewRepository;
    @Mock ReviewInsightAuditRepository auditRepository;
    @Mock ImprovementOpportunityRepository opportunityRepository;
    @Mock ReviewOpportunityInsightRepository linkRepository;
    @InjectMocks ReviewValidationService service;

    @Test
    void report_shouldPassWhenAuditThresholdsAndTraceabilityMeetGate() {
        when(runRepository.findByIdAndProjectId(5L, 2L)).thenReturn(Optional.of(
                ReviewAnalysisRun.builder().id(5L).projectId(2L).build()));
        var insight = ReviewInsight.builder().id(10L).analysisRunId(5L).reviewId(20L)
                .evidenceQuote("echo").productModule("bluetooth_wifi").severity("major").build();
        when(insightRepository.findByAnalysisRunId(5L)).thenReturn(List.of(insight));
        when(reviewRepository.findAllById(any())).thenReturn(List.of(
                ProductReview.builder().id(20L).asin("B0AAAA1111").reviewText("loud echo").build()));
        when(auditRepository.findByInsightIdIn(any())).thenReturn(List.of(
                ReviewInsightAudit.builder().insightId(10L).reviewedBy(3L)
                        .evidenceValid(true).moduleAccepted(true).severityAccepted(true).build()));
        when(reviewRepository.findByProjectIdOrderById(2L)).thenReturn(List.of(
                ProductReview.builder().asin("B0AAAA1111").externalReviewId("R1").contentHash("H1").build()));
        when(opportunityRepository.findByAnalysisRunIdOrderByPriorityScoreDesc(5L)).thenReturn(List.of(
                ImprovementOpportunity.builder().id(30L).priorityScore(BigDecimal.TEN).build()));
        when(linkRepository.findByOpportunityId(30L)).thenReturn(List.of(
                ReviewOpportunityInsight.builder().opportunityId(30L).insightId(10L).build()));

        var result = service.report(2L, 5L, 3L);

        assertTrue(result.releaseReady());
        assertEquals(new BigDecimal("1.0000"), result.evidenceValidityRate());
        assertEquals(1, result.traceableTopOpportunities());
    }
}
