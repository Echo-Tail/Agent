package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AiModelCapability;
import cafe.snails.ecomagents.model.ModelCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * AI 模型能力配置数据访问层。
 */
public interface AiModelCapabilityRepository extends JpaRepository<AiModelCapability, Long> {
    /** 按模型 ID 查询全部能力配置。 */
    List<AiModelCapability> findByModelIdOrderById(Long modelId);
    /** 按模型 ID 和能力类型查询配置。 */
    Optional<AiModelCapability> findByModelIdAndCapability(Long modelId, ModelCapability capability);
    /** 删除指定模型的全部能力配置。 */
    void deleteByModelId(Long modelId);
}
