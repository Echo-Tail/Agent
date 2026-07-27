package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface ReviewInsightRepository extends JpaRepository<ReviewInsight, Long>, JpaSpecificationExecutor<ReviewInsight> {
    List<ReviewInsight> findByAnalysisRunId(Long analysisRunId);
    List<ReviewInsight> findByReviewId(Long reviewId);
}
