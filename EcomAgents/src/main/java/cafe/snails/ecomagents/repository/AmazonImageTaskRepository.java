package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AmazonImageTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 亚马逊商品图片生成任务数据访问层。
 */
public interface AmazonImageTaskRepository extends JpaRepository<AmazonImageTask, Long>,
        JpaSpecificationExecutor<AmazonImageTask> {
}
