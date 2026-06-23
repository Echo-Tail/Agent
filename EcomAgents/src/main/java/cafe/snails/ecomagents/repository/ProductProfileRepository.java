package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductProfileRepository extends JpaRepository<ProductProfile, Long>, JpaSpecificationExecutor<ProductProfile> {
    boolean existsByProductName(String productName);
    boolean existsBySku(String sku);
    boolean existsByModelNumber(String modelNumber);
    Optional<ProductProfile> findBySku(String sku);
    Optional<ProductProfile> findByModelNumber(String modelNumber);
}
