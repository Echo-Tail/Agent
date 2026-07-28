package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 从评论洞察中聚合得到的产品改进机会。
 */
@Entity
@Table(name = "improvement_opportunities")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ImprovementOpportunity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "analysis_run_id", nullable = false)
    private Long analysisRunId;
    @Column(nullable = false, length = 300)
    private String title;
    @Column(name = "usage_scenario", nullable = false, length = 50)
    private String usageScenario;
    @Column(name = "product_module", nullable = false, length = 50)
    private String productModule;
    @Column(nullable = false, length = 20)
    private String severity;
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;
    @Column(name = "recommended_action", nullable = false, columnDefinition = "TEXT")
    private String recommendedAction;
    @Column(name = "insight_count", nullable = false)
    private Integer insightCount;
    @Column(name = "affected_review_ratio", nullable = false, precision = 6, scale = 5)
    private BigDecimal affectedReviewRatio;
    @Column(name = "customer_impact", nullable = false, precision = 5, scale = 2)
    private BigDecimal customerImpact;
    @Column(name = "business_impact", nullable = false, precision = 5, scale = 2)
    private BigDecimal businessImpact;
    @Column(name = "implementation_effort", nullable = false, precision = 5, scale = 2)
    private BigDecimal implementationEffort;
    @Column(name = "priority_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal priorityScore;
    @Column(columnDefinition = "TEXT")
    private String rationale;
    @Column(name = "manually_edited", nullable = false)
    private Boolean manuallyEdited;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        var now = LocalDateTime.now();
        if (insightCount == null) insightCount = 0;
        if (affectedReviewRatio == null) affectedReviewRatio = BigDecimal.ZERO;
        if (customerImpact == null) customerImpact = BigDecimal.ZERO;
        if (businessImpact == null) businessImpact = BigDecimal.ZERO;
        if (implementationEffort == null) implementationEffort = BigDecimal.valueOf(40);
        if (priorityScore == null) priorityScore = BigDecimal.ZERO;
        if (manuallyEdited == null) manuallyEdited = false;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
