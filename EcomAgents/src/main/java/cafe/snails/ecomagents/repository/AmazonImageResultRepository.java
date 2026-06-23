package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AmazonImageResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmazonImageResultRepository extends JpaRepository<AmazonImageResult, Long> {
    List<AmazonImageResult> findByTaskIdOrderByImageIndexAsc(Long taskId);
    void deleteByTaskId(Long taskId);
}
