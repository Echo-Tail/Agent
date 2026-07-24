package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageGenerationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 图片生成记录数据访问层。
 */
public interface ImageGenerationRecordRepository extends JpaRepository<ImageGenerationRecord, Long>,
        JpaSpecificationExecutor<ImageGenerationRecord> {

    /** 查询指定用户的所有记录，按创建时间倒序 */
    List<ImageGenerationRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ImageGenerationRecord> findByJobIdOrderByOutputIndex(Long jobId);
}
