package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface ImageGenerationJobRepository extends JpaRepository<ImageGenerationJob, Long> {
    long countByStatus(cafe.snails.ecomagents.model.ImageGenerationJobStatus status);
    List<ImageGenerationJob> findTop20ByStatusOrderByCompletedAtDesc(
            cafe.snails.ecomagents.model.ImageGenerationJobStatus status);

    Optional<ImageGenerationJob> findByIdAndUserId(Long id, Long userId);

    @Query("select j.status from ImageGenerationJob j where j.id = :id")
    Optional<cafe.snails.ecomagents.model.ImageGenerationJobStatus> findStatusById(Long id);
}
