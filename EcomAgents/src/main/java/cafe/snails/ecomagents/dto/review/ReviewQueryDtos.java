package cafe.snails.ecomagents.dto.review;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评论分析查询相关的数据传输对象集合。
 */
public final class ReviewQueryDtos {
    private ReviewQueryDtos() {}

    /** 商品原始评论的响应。 */
    public record ProductReviewResponse(
            Long id, String asin, BigDecimal rating, String title, String reviewText,
            LocalDateTime reviewDate, Boolean verifiedPurchase, Integer helpfulCount,
            String reviewerName, String sourceUrl, LocalDateTime collectedAt) {}

    /** 从单条评论中提取的洞察响应。 */
    public record InsightResponse(
            Long id, Long reviewId, String asin, BigDecimal rating, String reviewText,
            String userProblem, String usageScenario, String productModule, String severity,
            String sentiment, String evidenceQuote, String actionType, String improvementAction,
            Integer returnRisk, Integer conversionRisk, BigDecimal confidence,
            Boolean manuallyEdited, LocalDateTime updatedAt) {}

    /** 人工修订评论洞察内容的请求。 */
    public record UpdateInsightRequest(
            @NotBlank @Size(max = 5000) String userProblem,
            @NotBlank String usageScenario,
            @NotBlank String productModule,
            @NotBlank String severity,
            @NotBlank String sentiment,
            @NotBlank @Size(max = 5000) String evidenceQuote,
            @NotBlank String actionType,
            @NotBlank @Size(max = 10000) String improvementAction,
            @NotNull @Min(1) @Max(5) Integer returnRisk,
            @NotNull @Min(1) @Max(5) Integer conversionRisk) {}

    /** 单个分析维度及其统计数量。 */
    public record DimensionCount(String key, long count) {}

    /** 评论分析看板的聚合统计响应。 */
    public record DashboardResponse(
            Long runId, int reviewCount, int insightCount, int opportunityCount,
            long manuallyEditedInsightCount, BigDecimal averageRating,
            List<DimensionCount> ratings, List<DimensionCount> severities,
            List<DimensionCount> scenarios, List<DimensionCount> modules,
            List<DimensionCount> actionTypes, Map<String, Integer> productReviewCounts) {}
}
