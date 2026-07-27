package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewOpportunityServiceTest {
    @Mock ReviewAnalysisProjectRepository projectRepository;
    @Mock ReviewAnalysisRunRepository runRepository;
    @Mock ReviewInsightRepository insightRepository;
    @Mock ProductReviewRepository reviewRepository;
    @Mock ImprovementOpportunityRepository opportunityRepository;
    @Mock ReviewOpportunityInsightRepository linkRepository;
    @Mock ReviewOpportunityClusterer clusterer;
    ReviewOpportunityService service;

    @BeforeEach
    void setUp() {
        service = new ReviewOpportunityService(projectRepository, runRepository, insightRepository,
                reviewRepository, opportunityRepository, linkRepository, clusterer,
                new ReviewOpportunityScorer());
        lenient().when(opportunityRepository.save(any())).thenAnswer(invocation -> {
            ImprovementOpportunity opportunity = invocation.getArgument(0);
            if (opportunity.getId() == null) opportunity.setId(51L);
            return opportunity;
        });
    }

    @Test
    void generate_shouldCreateScoredOpportunityAndEvidenceLinks() {
        var run = ReviewAnalysisRun.builder().id(41L).projectId(11L).modelId(5L).build();
        var insight = ReviewInsight.builder().id(31L).analysisRunId(41L).reviewId(21L)
                .userProblem("CarPlay disconnects").usageScenario("daily_commute")
                .productModule("carplay").severity("major").actionType("firmware")
                .improvementAction("Improve reconnect").returnRisk(4).conversionRisk(4)
                .confidence(BigDecimal.valueOf(.9)).build();
        var review = ProductReview.builder().id(21L).projectId(11L).rating(BigDecimal.valueOf(2))
                .verifiedPurchase(true).helpfulCount(5).build();
        when(runRepository.findById(41L)).thenReturn(Optional.of(run));
        when(insightRepository.findByAnalysisRunId(41L)).thenReturn(List.of(insight));
        when(reviewRepository.findAllById(any())).thenReturn(List.of(review));
        when(reviewRepository.countByProjectId(11L)).thenReturn(10L);
        when(clusterer.cluster(run, List.of(insight))).thenReturn(List.of(
                new ReviewOpportunityClusterer.Cluster("CarPlay disconnects", List.of(31L),
                        "Improve reconnect", "Frequent connection failure")));

        var result = service.generate(41L);

        assertEquals(1, result.size());
        assertEquals("major", result.get(0).severity());
        assertTrue(result.get(0).priorityScore().compareTo(BigDecimal.ZERO) > 0);
        verify(linkRepository).saveAll(argThat(values -> values.iterator().hasNext()));
    }

    @Test
    void updateEffort_shouldRecalculatePriorityAndMarkManualEdit() {
        var project = ReviewAnalysisProject.builder().id(11L).createdBy(3L).build();
        var opportunity = ImprovementOpportunity.builder().id(51L).analysisRunId(41L)
                .customerImpact(BigDecimal.valueOf(80)).businessImpact(BigDecimal.valueOf(70))
                .implementationEffort(BigDecimal.valueOf(40)).priorityScore(BigDecimal.valueOf(140))
                .build();
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project));
        when(runRepository.findByIdAndProjectId(41L, 11L)).thenReturn(Optional.of(
                ReviewAnalysisRun.builder().id(41L).projectId(11L).build()));
        when(opportunityRepository.findByIdAndAnalysisRunId(51L, 41L)).thenReturn(Optional.of(opportunity));

        var result = service.updateEffort(11L, 41L, 51L, BigDecimal.valueOf(100), 3L);

        assertEquals(new BigDecimal("56.00"), result.priorityScore());
        assertTrue(result.manuallyEdited());
    }
}
