package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductProfileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductProfileVersionRepository extends JpaRepository<ProductProfileVersion, Long> {
    List<ProductProfileVersion> findByProfileIdOrderByVersionNumberDesc(Long profileId);
    Optional<ProductProfileVersion> findTopByProfileIdOrderByVersionNumberDesc(Long profileId);
    int countByProfileId(Long profileId);
}
