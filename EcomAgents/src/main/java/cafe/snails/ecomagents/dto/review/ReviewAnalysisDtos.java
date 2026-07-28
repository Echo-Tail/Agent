package cafe.snails.ecomagents.dto.review;

import java.time.LocalDateTime;

public final class ReviewAnalysisDtos {
    private ReviewAnalysisDtos() {}

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
