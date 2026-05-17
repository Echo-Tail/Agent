package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * AI 模型配置数据访问层。
 */
public interface AiModelRepository extends JpaRepository<AiModel, Long> {
    /** 查找当前默认模型 */
    Optional<AiModel> findByIsDefaultTrue();

    /** 统计已启用的模型数量 */
    long countByEnabledTrue();
}
