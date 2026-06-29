package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductVisualStrategyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVisualStrategyVersionRepository extends JpaRepository<ProductVisualStrategyVersion, Long> {
    List<ProductVisualStrategyVersion> findByProfileIdOrderByVersionNumberDesc(Long profileId);
    List<ProductVisualStrategyVersion> findByCognitionVersionIdOrderByVersionNumberDesc(Long cognitionVersionId);
    Optional<ProductVisualStrategyVersion> findTopByProfileIdOrderByVersionNumberDesc(Long profileId);
    Optional<ProductVisualStrategyVersion> findTopByProfileIdAndStatusOrderByVersionNumberDesc(Long profileId, String status);
    int countByProfileId(Long profileId);
}