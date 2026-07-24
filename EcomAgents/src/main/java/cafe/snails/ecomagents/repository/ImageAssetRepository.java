package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {
    List<ImageAsset> findBySessionIdAndDeletedAtIsNullOrderByCreatedAt(Long sessionId);
    Optional<ImageAsset> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
    long countBySessionIdAndDeletedAtIsNull(Long sessionId);
}
