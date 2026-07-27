package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewInsightAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewInsightAuditRepository extends JpaRepository<ReviewInsightAudit, Long> {
    List<ReviewInsightAudit> findByInsightIdIn(Collection<Long> insightIds);
    Optional<ReviewInsightAudit> findByInsightIdAndReviewedBy(Long insightId, Long reviewedBy);
}
