package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 知识库数据访问层。
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    /** 按创建者查找知识库，按创建时间降序排列 */
    List<KnowledgeBase> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
}
