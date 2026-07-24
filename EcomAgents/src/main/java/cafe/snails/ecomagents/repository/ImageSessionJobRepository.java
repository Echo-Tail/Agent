package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageSessionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ImageSessionJobRepository extends JpaRepository<ImageSessionJob, Long> {
    Optional<ImageSessionJob> findBySessionIdAndIdempotencyKey(Long sessionId, String idempotencyKey);
    boolean existsBySessionIdAndJobId(Long sessionId, Long jobId);
    List<ImageSessionJob> findBySessionIdOrderByCreatedAt(Long sessionId);
}
