package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.model.review.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReviewOpportunityScorerTest {
    private final ReviewOpportunityScorer scorer = new ReviewOpportunityScorer();

    @Test
    void score_shouldCombineSeverityFrequencyEvidenceAndBusinessRisk() {
        var review = ProductReview.builder().id(1L).rating(BigDecimal.valueOf(2))
                .verifiedPurchase(true).helpfulCount(10).build();
        var insight = ReviewInsight.builder().reviewId(1L).severity("major")
                .returnRisk(4).conversionRisk(5).confidence(BigDecimal.valueOf(.9)).build();

        var score = scorer.score(List.of(insight), Map.of(1L, review), 10, "firmware");

        assertEquals(new BigDecimal("0.10000"), score.affectedReviewRatio());
        assertEquals(new BigDecimal("40"), score.implementationEffort());
        assertTrue(score.customerImpact().compareTo(BigDecimal.valueOf(50)) > 0);
        assertTrue(score.businessImpact().compareTo(BigDecimal.valueOf(70)) > 0);
        assertTrue(score.priorityScore().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void priority_shouldDecreaseWhenImplementationEffortIncreases() {
        BigDecimal lowEffort = scorer.priority(BigDecimal.valueOf(80), BigDecimal.valueOf(70), BigDecimal.valueOf(20));
        BigDecimal highEffort = scorer.priority(BigDecimal.valueOf(80), BigDecimal.valueOf(70), BigDecimal.valueOf(100));

        assertTrue(lowEffort.compareTo(highEffort) > 0);
    }
}
