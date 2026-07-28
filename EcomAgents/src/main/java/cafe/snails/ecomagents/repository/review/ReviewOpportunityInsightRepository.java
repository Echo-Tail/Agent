package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewOpportunityInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 改进机会与评论洞察关联数据访问层。
 */
public interface ReviewOpportunityInsightRepository extends JpaRepository<ReviewOpportunityInsight, Long> {
    /** 查询指定改进机会关联的洞察记录。 */
    List<ReviewOpportunityInsight> findByOpportunityId(Long opportunityId);
    /** 判断改进机会是否已关联指定洞察。 */
    boolean existsByOpportunityIdAndInsightId(Long opportunityId, Long insightId);
}
