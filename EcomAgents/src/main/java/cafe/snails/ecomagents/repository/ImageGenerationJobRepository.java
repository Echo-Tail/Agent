package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface ImageGenerationJobRepository extends JpaRepository<ImageGenerationJob, Long> {
    interface DurationAggregate {
        Double getAverageMs();
        Double getP95Ms();
    }

    interface ProviderDurationAggregate {
        String getProvider();
        Double getAverageMs();
        Double getP95Ms();
    }

    long countByStatus(cafe.snails.ecomagents.model.ImageGenerationJobStatus status);

    @Query("select coalesce(j.provider, 'unknown'), j.status, count(j) "
            + "from ImageGenerationJob j group by j.provider, j.status")
    List<Object[]> countByProviderAndStatus();

    @Query(value = """
            select coalesce(avg(extract(epoch from (completed_at - started_at)) * 1000), 0) as "averageMs",
                   coalesce(percentile_cont(0.95) within group
                     (order by extract(epoch from (completed_at - started_at)) * 1000), 0) as "p95Ms"
            from image_generation_jobs
            where started_at is not null and completed_at is not null and completed_at >= started_at
            """, nativeQuery = true)
    DurationAggregate aggregateCompletedDurations();

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

    List<ImageGenerationJob> findTop20ByStatusOrderByCompletedAtDesc(
            cafe.snails.ecomagents.model.ImageGenerationJobStatus status);

    Optional<ImageGenerationJob> findByIdAndUserId(Long id, Long userId);

    @Query("select j.status from ImageGenerationJob j where j.id = :id")
    Optional<cafe.snails.ecomagents.model.ImageGenerationJobStatus> findStatusById(Long id);
}
