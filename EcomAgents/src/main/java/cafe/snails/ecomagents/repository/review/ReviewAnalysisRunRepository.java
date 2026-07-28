package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewAnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 评论分析运行记录数据访问层。
 */
public interface ReviewAnalysisRunRepository extends JpaRepository<ReviewAnalysisRun, Long> {
    /** 查询项目分析记录并按版本号倒序排列。 */
    List<ReviewAnalysisRun> findByProjectIdOrderByVersionNumberDesc(Long projectId);
    /** 查询项目最新的分析记录。 */
    Optional<ReviewAnalysisRun> findTopByProjectIdOrderByVersionNumberDesc(Long projectId);
    /** 按项目和幂等键查询分析记录。 */
    Optional<ReviewAnalysisRun> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);
    /** 按分析记录 ID 和项目 ID 查询记录。 */
    Optional<ReviewAnalysisRun> findByIdAndProjectId(Long id, Long projectId);
    /** 统计项目的分析运行次数。 */
    long countByProjectId(Long projectId);
}
