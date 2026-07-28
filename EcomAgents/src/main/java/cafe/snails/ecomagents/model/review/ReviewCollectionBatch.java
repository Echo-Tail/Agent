package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 评论数据的一次批量采集任务。
 */
@Entity
@Table(name = "review_collection_batches")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewCollectionBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "bright_data_record_id")
    private Long brightDataRecordId;
    @Column(name = "snapshot_id", length = 100)
    private String snapshotId;
    @Column(name = "dataset_id", nullable = false, length = 100)
    private String datasetId;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;
    @Column(name = "collected_count", nullable = false)
    private Integer collectedCount;
    @Column(name = "duplicate_count", nullable = false)
    private Integer duplicateCount;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void onCreate() {
        if (requestedCount == null) requestedCount = 0;
        if (collectedCount == null) collectedCount = 0;
        if (duplicateCount == null) duplicateCount = 0;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
