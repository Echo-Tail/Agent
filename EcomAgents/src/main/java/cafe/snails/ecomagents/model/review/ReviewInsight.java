package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_insights")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewInsight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "analysis_run_id", nullable = false)
    private Long analysisRunId;
    @Column(name = "review_id", nullable = false)
    private Long reviewId;
    @Column(name = "user_problem", nullable = false, columnDefinition = "TEXT")
    private String userProblem;
    @Column(name = "usage_scenario", nullable = false, length = 50)
    private String usageScenario;
    @Column(name = "product_module", nullable = false, length = 50)
    private String productModule;
    @Column(nullable = false, length = 20)
    private String severity;
    @Column(nullable = false, length = 20)
    private String sentiment;
    @Column(name = "evidence_quote", nullable = false, columnDefinition = "TEXT")
    private String evidenceQuote;
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;
    @Column(name = "improvement_action", nullable = false, columnDefinition = "TEXT")
    private String improvementAction;
    @Column(name = "return_risk", nullable = false)
    private Integer returnRisk;
    @Column(name = "conversion_risk", nullable = false)
    private Integer conversionRisk;
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;
    @Column(name = "manually_edited", nullable = false)
    private Boolean manuallyEdited;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        var now = LocalDateTime.now();
        if (manuallyEdited == null) manuallyEdited = false;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
