package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewAnalysisProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewAnalysisProjectRepository extends JpaRepository<ReviewAnalysisProject, Long> {
    List<ReviewAnalysisProject> findByCreatedByOrderByUpdatedAtDesc(Long userId);
    Optional<ReviewAnalysisProject> findByIdAndCreatedBy(Long id, Long userId);
}
