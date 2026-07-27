package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ImprovementOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ImprovementOpportunityRepository extends JpaRepository<ImprovementOpportunity, Long> {
    List<ImprovementOpportunity> findByAnalysisRunIdOrderByPriorityScoreDesc(Long analysisRunId);
    Optional<ImprovementOpportunity> findByIdAndAnalysisRunId(Long id, Long analysisRunId);
    void deleteByAnalysisRunId(Long analysisRunId);
}
