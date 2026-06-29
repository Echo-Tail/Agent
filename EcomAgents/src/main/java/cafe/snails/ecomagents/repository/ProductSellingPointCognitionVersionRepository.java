package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductSellingPointCognitionVersionRepository extends JpaRepository<ProductSellingPointCognitionVersion, Long> {
    List<ProductSellingPointCognitionVersion> findByProfileIdOrderByVersionNumberDesc(Long profileId);
    Optional<ProductSellingPointCognitionVersion> findTopByProfileIdOrderByVersionNumberDesc(Long profileId);
    Optional<ProductSellingPointCognitionVersion> findTopByProfileIdAndStatusOrderByVersionNumberDesc(Long profileId, String status);
    int countByProfileId(Long profileId);
}