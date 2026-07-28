package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

/**
 * 评论洞察数据访问层。
 */
public interface ReviewInsightRepository extends JpaRepository<ReviewInsight, Long>, JpaSpecificationExecutor<ReviewInsight> {
    /** 查询指定分析运行产生的全部洞察。 */
    List<ReviewInsight> findByAnalysisRunId(Long analysisRunId);
    /** 查询指定评论关联的全部洞察。 */
    List<ReviewInsight> findByReviewId(Long reviewId);
}
