package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

/**
 * 图片生成任务数据访问层。
 */
public interface ImageGenerationJobRepository extends JpaRepository<ImageGenerationJob, Long> {
    /** 全部供应商的任务耗时聚合投影。 */
    interface DurationAggregate {
        Double getAverageMs();
        Double getP95Ms();
    }

    /** 按供应商分组的任务耗时聚合投影。 */
    interface ProviderDurationAggregate {
        String getProvider();
        Double getAverageMs();
        Double getP95Ms();
    }

    /** 按任务状态统计数量。 */
    long countByStatus(cafe.snails.ecomagents.model.ImageGenerationJobStatus status);

    /** 按供应商和任务状态分组统计数量。 */
    @Query("select coalesce(j.provider, 'unknown'), j.status, count(j) "
            + "from ImageGenerationJob j group by j.provider, j.status")
    List<Object[]> countByProviderAndStatus();

    /** 聚合已完成任务的平均耗时和 P95 耗时。 */
    @Query(value = """
            select coalesce(avg(extract(epoch from (completed_at - started_at)) * 1000), 0) as "averageMs",
                   coalesce(percentile_cont(0.95) within group
                     (order by extract(epoch from (completed_at - started_at)) * 1000), 0) as "p95Ms"
            from image_generation_jobs
            where started_at is not null and completed_at is not null and completed_at >= started_at
            """, nativeQuery = true)
    DurationAggregate aggregateCompletedDurations();

    /** 按供应商聚合已完成任务的平均耗时和 P95 耗时。 */
    @Query(value = """
            select lower(coalesce(provider, 'unknown')) as provider,
                   coalesce(avg(extract(epoch from (completed_at - started_at)) * 1000), 0) as "averageMs",
                   coalesce(percentile_cont(0.95) within group
                     (order by extract(epoch from (completed_at - started_at)) * 1000), 0) as "p95Ms"
            from image_generation_jobs
            where started_at is not null and completed_at is not null and completed_at >= started_at
            group by lower(coalesce(provider, 'unknown'))
            """, nativeQuery = true)
    List<ProviderDurationAggregate> aggregateCompletedDurationsByProvider();

    /** 查询指定状态下最近完成的二十个任务。 */
    List<ImageGenerationJob> findTop20ByStatusOrderByCompletedAtDesc(
            cafe.snails.ecomagents.model.ImageGenerationJobStatus status);

    /** 按任务 ID 和用户 ID 查询任务。 */
    Optional<ImageGenerationJob> findByIdAndUserId(Long id, Long userId);

    /** 仅查询指定任务的状态。 */
    @Query("select j.status from ImageGenerationJob j where j.id = :id")
    Optional<cafe.snails.ecomagents.model.ImageGenerationJobStatus> findStatusById(Long id);
}
