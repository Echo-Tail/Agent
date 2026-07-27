package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewOpportunityInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewOpportunityInsightRepository extends JpaRepository<ReviewOpportunityInsight, Long> {
    List<ReviewOpportunityInsight> findByOpportunityId(Long opportunityId);
    boolean existsByOpportunityIdAndInsightId(Long opportunityId, Long insightId);
}
