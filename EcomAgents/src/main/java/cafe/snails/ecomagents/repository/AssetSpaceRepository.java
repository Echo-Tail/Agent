package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AssetSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetSpaceRepository extends JpaRepository<AssetSpace, Long> {
    Optional<AssetSpace> findByName(String name);
    boolean existsByName(String name);
}
