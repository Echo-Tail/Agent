package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ImprovementOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 评论改进机会数据访问层。
 */
public interface ImprovementOpportunityRepository extends JpaRepository<ImprovementOpportunity, Long> {
    /** 查询分析运行的改进机会并按优先级倒序排列。 */
    List<ImprovementOpportunity> findByAnalysisRunIdOrderByPriorityScoreDesc(Long analysisRunId);
    /** 按机会 ID 和分析运行 ID 查询改进机会。 */
    Optional<ImprovementOpportunity> findByIdAndAnalysisRunId(Long id, Long analysisRunId);
    /** 删除指定分析运行的全部改进机会。 */
    void deleteByAnalysisRunId(Long analysisRunId);
}
