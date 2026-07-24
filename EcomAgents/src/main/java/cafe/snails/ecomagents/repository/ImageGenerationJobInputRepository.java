package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageGenerationJobInput;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImageGenerationJobInputRepository extends JpaRepository<ImageGenerationJobInput, Long> {
    List<ImageGenerationJobInput> findByJobIdOrderByInputIndex(Long jobId);
}
