package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewAnalysisFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public interface ReviewAnalysisFailureRepository extends JpaRepository<ReviewAnalysisFailure, Long> {
    List<ReviewAnalysisFailure> findByAnalysisRunIdOrderByReviewId(Long analysisRunId);
    Optional<ReviewAnalysisFailure> findByAnalysisRunIdAndReviewId(Long analysisRunId, Long reviewId);
    long countByAnalysisRunId(Long analysisRunId);
    @Transactional
    void deleteByAnalysisRunIdAndReviewId(Long analysisRunId, Long reviewId);
}
