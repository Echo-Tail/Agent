package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewInsightAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 评论洞察审核记录数据访问层。
 */
public interface ReviewInsightAuditRepository extends JpaRepository<ReviewInsightAudit, Long> {
    /** 批量查询指定洞察的审核记录。 */
    List<ReviewInsightAudit> findByInsightIdIn(Collection<Long> insightIds);
    /** 按洞察 ID 和审核人查询审核记录。 */
    Optional<ReviewInsightAudit> findByInsightIdAndReviewedBy(Long insightId, Long reviewedBy);
}
