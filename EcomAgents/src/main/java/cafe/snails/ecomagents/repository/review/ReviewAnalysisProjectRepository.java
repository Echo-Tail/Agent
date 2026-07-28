package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewAnalysisProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 评论分析项目数据访问层。
 */
public interface ReviewAnalysisProjectRepository extends JpaRepository<ReviewAnalysisProject, Long> {
    /** 查询用户创建的项目并按更新时间倒序排列。 */
    List<ReviewAnalysisProject> findByCreatedByOrderByUpdatedAtDesc(Long userId);
    /** 按项目 ID 和创建者查询项目。 */
    Optional<ReviewAnalysisProject> findByIdAndCreatedBy(Long id, Long userId);
}
