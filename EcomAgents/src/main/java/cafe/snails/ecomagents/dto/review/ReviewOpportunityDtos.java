package cafe.snails.ecomagents.dto.review;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class ReviewOpportunityDtos {
    private ReviewOpportunityDtos() {}

    public record UpdateEffortRequest(
            @NotNull @DecimalMin("1") @DecimalMax("100") BigDecimal implementationEffort) {}

    public record OpportunityResponse(
            Long id,
            Long analysisRunId,
            String title,
            String usageScenario,
            String productModule,
            String severity,
            String actionType,
            String recommendedAction,
            Integer insightCount,
            BigDecimal affectedReviewRatio,
            BigDecimal customerImpact,
            BigDecimal businessImpact,
            BigDecimal implementationEffort,
            BigDecimal priorityScore,
            String rationale,
            Boolean manuallyEdited) {}
}
