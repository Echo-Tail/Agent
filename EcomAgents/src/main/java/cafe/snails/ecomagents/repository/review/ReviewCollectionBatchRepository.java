package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewCollectionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 评论采集批次数据访问层。
 */
public interface ReviewCollectionBatchRepository extends JpaRepository<ReviewCollectionBatch, Long> {
    /** 查询项目最近创建的采集批次。 */
    Optional<ReviewCollectionBatch> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);
    /** 查询项目采集批次并按创建时间倒序排列。 */
    List<ReviewCollectionBatch> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    /** 按项目和幂等键查询采集批次。 */
    Optional<ReviewCollectionBatch> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);
    /** 按批次 ID 和项目 ID 查询采集批次。 */
    Optional<ReviewCollectionBatch> findByIdAndProjectId(Long id, Long projectId);
}
