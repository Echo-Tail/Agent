package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageSuperResolutionJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 图片超分辨率任务数据访问层。
 */
public interface ImageSuperResolutionJobRepository extends JpaRepository<ImageSuperResolutionJob, Long> {
    /** 查询用户最近创建的五十个超分任务。 */
    List<ImageSuperResolutionJob> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    /** 按来源查询用户最近创建的五十个超分任务。 */
    List<ImageSuperResolutionJob> findTop50ByUserIdAndOriginOrderByCreatedAtDesc(Long userId, String origin);
    /** 查询处于任一指定状态的任务。 */
    List<ImageSuperResolutionJob> findByStatusIn(Collection<String> statuses);
    /** 统计用户处于任一指定状态的任务数量。 */
    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);
    /** 判断同一源文件是否存在指定状态的任务。 */
    boolean existsByUserIdAndSourcePathAndStatusIn(Long userId, String sourcePath, Collection<String> statuses);
    /** 查询指定来源类型且在截止时间前完成的任务。 */
    List<ImageSuperResolutionJob> findBySourceTypeAndStatusInAndCompletedAtBefore(
            String sourceType, Collection<String> statuses, LocalDateTime completedBefore);
}
