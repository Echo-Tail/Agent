package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_insight_audits")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewInsightAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "insight_id", nullable = false)
    private Long insightId;
    @Column(name = "reviewed_by", nullable = false)
    private Long reviewedBy;
    @Column(name = "evidence_valid", nullable = false)
    private Boolean evidenceValid;
    @Column(name = "module_accepted", nullable = false)
    private Boolean moduleAccepted;
    @Column(name = "severity_accepted", nullable = false)
    private Boolean severityAccepted;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
