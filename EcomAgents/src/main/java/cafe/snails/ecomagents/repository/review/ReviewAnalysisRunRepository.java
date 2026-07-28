package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewAnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewAnalysisRunRepository extends JpaRepository<ReviewAnalysisRun, Long> {
    List<ReviewAnalysisRun> findByProjectIdOrderByVersionNumberDesc(Long projectId);
    Optional<ReviewAnalysisRun> findTopByProjectIdOrderByVersionNumberDesc(Long projectId);
    Optional<ReviewAnalysisRun> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);
    Optional<ReviewAnalysisRun> findByIdAndProjectId(Long id, Long projectId);
    long countByProjectId(Long projectId);
}
