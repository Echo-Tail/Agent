package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageSuperResolutionJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ImageSuperResolutionJobRepository extends JpaRepository<ImageSuperResolutionJob, Long> {
    List<ImageSuperResolutionJob> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<ImageSuperResolutionJob> findTop50ByUserIdAndOriginOrderByCreatedAtDesc(Long userId, String origin);
    List<ImageSuperResolutionJob> findByStatusIn(Collection<String> statuses);
    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);
    boolean existsByUserIdAndSourcePathAndStatusIn(Long userId, String sourcePath, Collection<String> statuses);
    List<ImageSuperResolutionJob> findBySourceTypeAndStatusInAndCompletedAtBefore(
            String sourceType, Collection<String> statuses, LocalDateTime completedBefore);
}