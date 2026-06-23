package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageExpressionCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageExpressionCacheRepository extends JpaRepository<ImageExpressionCache, Long> {
    Optional<ImageExpressionCache> findByImageUrlHash(String imageUrlHash);
    boolean existsByImageUrlHash(String imageUrlHash);
}
