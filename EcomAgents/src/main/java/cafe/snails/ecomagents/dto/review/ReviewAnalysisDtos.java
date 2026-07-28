package cafe.snails.ecomagents.dto.review;

import java.time.LocalDateTime;

/**
 * 评论分析任务相关的数据传输对象集合。
 */
public final class ReviewAnalysisDtos {
    private ReviewAnalysisDtos() {}

    /** 评论分析运行记录的响应。 */
    public record AnalysisRunResponse(
            Long id,
            Long projectId,
            Integer versionNumber,
            String status,
            String taxonomyVersion,
            String promptVersion,
            Long modelId,
            Integer sourceReviewCount,
            Integer processedReviewCount,
            Integer failedReviewCount,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt) {}
}
