package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ImageSessionRepository extends JpaRepository<ImageSession, Long> {
    List<ImageSession> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long userId);
    Optional<ImageSession> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
