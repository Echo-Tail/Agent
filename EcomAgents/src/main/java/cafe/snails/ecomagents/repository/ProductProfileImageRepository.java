package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductProfileImageRepository extends JpaRepository<ProductProfileImage, Long> {
    List<ProductProfileImage> findByProfileId(Long profileId);
    void deleteByProfileId(Long profileId);
}
