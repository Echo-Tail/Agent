package cafe.snails.ecomagents.dto.review;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 评论改进机会相关的数据传输对象集合。
 */
public final class ReviewOpportunityDtos {
    private ReviewOpportunityDtos() {}

    /** 更新改进机会实施成本的请求。 */
    public record UpdateEffortRequest(
            @NotNull @DecimalMin("1") @DecimalMax("100") BigDecimal implementationEffort) {}

    /** 从评论分析中识别出的产品改进机会响应。 */
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
