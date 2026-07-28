package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageSessionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 图片会话任务关联数据访问层。
 */
public interface ImageSessionJobRepository extends JpaRepository<ImageSessionJob, Long> {
    /** 按会话和幂等键查询任务关联。 */
    Optional<ImageSessionJob> findBySessionIdAndIdempotencyKey(Long sessionId, String idempotencyKey);
    /** 判断生成任务是否已关联到指定会话。 */
    boolean existsBySessionIdAndJobId(Long sessionId, Long jobId);
    /** 查询会话任务并按创建时间排序。 */
    List<ImageSessionJob> findBySessionIdOrderByCreatedAt(Long sessionId);
}
