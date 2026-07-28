package cafe.snails.ecomagents.dto.review;

import java.time.LocalDateTime;

/**
 * 评论采集任务相关的数据传输对象集合。
 */
public final class ReviewCollectionDtos {
    private ReviewCollectionDtos() {}

    /** 评论采集任务及其执行结果的响应。 */
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
