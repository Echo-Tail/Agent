package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cafe.snails.ecomagents.model.ModelCapability;

import java.util.List;
import java.util.Optional;

/**
 * AI 模型配置数据访问层。
 */
public interface AiModelRepository extends JpaRepository<AiModel, Long> {
    /** 查找当前默认模型 */
    Optional<AiModel> findByIsDefaultTrue();

    /** 统计已启用的模型数量 */
    long countByEnabledTrue();

    /** 按类型和启用状态查询模型 */
    List<AiModel> findByModelTypeAndEnabled(String modelType, Boolean enabled);

    @Query("select distinct m from AiModel m, AiModelCapability c " +
            "where c.modelId = m.id and c.capability = :capability and m.enabled = true order by m.id")
    List<AiModel> findEnabledByCapability(@Param("capability") ModelCapability capability);
}
