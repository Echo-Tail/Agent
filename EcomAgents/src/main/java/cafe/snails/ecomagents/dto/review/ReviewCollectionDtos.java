package cafe.snails.ecomagents.dto.review;

import java.time.LocalDateTime;

public final class ReviewCollectionDtos {
    private ReviewCollectionDtos() {}

    public record CollectionResponse(
            Long id,
            Long projectId,
            String snapshotId,
            String datasetId,
            String status,
            Integer requestedCount,
            Integer collectedCount,
            Integer duplicateCount,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime createdAt) {}
}
