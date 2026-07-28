package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageGenerationJobInput;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 图片生成任务输入数据访问层。
 */
public interface ImageGenerationJobInputRepository extends JpaRepository<ImageGenerationJobInput, Long> {
    /** 按任务 ID 查询并按输入序号排序。 */
    List<ImageGenerationJobInput> findByJobIdOrderByInputIndex(Long jobId);
}
