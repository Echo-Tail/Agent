package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 评论分析过程中单条数据的失败记录。
 */
@Entity
@Table(name = "review_analysis_failures")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewAnalysisFailure {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "analysis_run_id", nullable = false)
    private Long analysisRunId;
    @Column(name = "review_id", nullable = false)
    private Long reviewId;
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;
    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "last_attempt_at", nullable = false)
    private LocalDateTime lastAttemptAt;
}
