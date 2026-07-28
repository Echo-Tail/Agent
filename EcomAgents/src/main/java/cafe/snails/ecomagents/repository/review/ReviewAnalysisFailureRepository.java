package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewAnalysisFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * 评论分析失败记录数据访问层。
 */
public interface ReviewAnalysisFailureRepository extends JpaRepository<ReviewAnalysisFailure, Long> {
    /** 查询分析运行的失败记录并按评论 ID 排序。 */
    List<ReviewAnalysisFailure> findByAnalysisRunIdOrderByReviewId(Long analysisRunId);
    /** 按分析运行和评论 ID 查询失败记录。 */
    Optional<ReviewAnalysisFailure> findByAnalysisRunIdAndReviewId(Long analysisRunId, Long reviewId);
    /** 统计分析运行的失败记录数量。 */
    long countByAnalysisRunId(Long analysisRunId);
    /** 删除指定评论在分析运行中的失败记录。 */
    @Transactional
    void deleteByAnalysisRunIdAndReviewId(Long analysisRunId, Long reviewId);
}
