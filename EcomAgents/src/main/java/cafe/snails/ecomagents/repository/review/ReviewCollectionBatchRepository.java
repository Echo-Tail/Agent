package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewCollectionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewCollectionBatchRepository extends JpaRepository<ReviewCollectionBatch, Long> {
    Optional<ReviewCollectionBatch> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ReviewCollectionBatch> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<ReviewCollectionBatch> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);
    Optional<ReviewCollectionBatch> findByIdAndProjectId(Long id, Long projectId);
}
