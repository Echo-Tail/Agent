package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AmazonImageTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AmazonImageTaskRepository extends JpaRepository<AmazonImageTask, Long>,
        JpaSpecificationExecutor<AmazonImageTask> {
}
