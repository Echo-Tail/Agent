package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;

/**
 * 产品改进机会与评论洞察之间的关联记录。
 */
@Entity
@Table(name = "review_opportunity_insights")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewOpportunityInsight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;
    @Column(name = "insight_id", nullable = false)
    private Long insightId;
}
