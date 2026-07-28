package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AmazonImageResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 亚马逊商品图片生成结果数据访问层。
 */
public interface AmazonImageResultRepository extends JpaRepository<AmazonImageResult, Long> {
    /** 按任务 ID 查询并按图片序号排序。 */
    List<AmazonImageResult> findByTaskIdOrderByImageIndexAsc(Long taskId);
    /** 删除指定任务的全部图片结果。 */
    void deleteByTaskId(Long taskId);
}
