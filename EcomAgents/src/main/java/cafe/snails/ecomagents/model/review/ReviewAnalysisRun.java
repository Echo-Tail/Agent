package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_analysis_runs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewAnalysisRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "taxonomy_version", nullable = false, length = 32)
    private String taxonomyVersion;
    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;
    @Column(name = "role_prompt", nullable = false, columnDefinition = "TEXT")
    private String rolePrompt;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(name = "model_id")
    private Long modelId;
    @Column(name = "source_review_count", nullable = false)
    private Integer sourceReviewCount;
    @Column(name = "processed_review_count", nullable = false)
    private Integer processedReviewCount;
    @Column(name = "failed_review_count", nullable = false)
    private Integer failedReviewCount;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "confirmed_by")
    private Long confirmedBy;
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist void onCreate() {
        if (sourceReviewCount == null) sourceReviewCount = 0;
        if (processedReviewCount == null) processedReviewCount = 0;
        if (failedReviewCount == null) failedReviewCount = 0;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
