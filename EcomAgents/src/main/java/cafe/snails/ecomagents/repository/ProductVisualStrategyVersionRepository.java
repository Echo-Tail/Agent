package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductVisualStrategyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 商品视觉策略版本数据访问层。
 */
public interface ProductVisualStrategyVersionRepository extends JpaRepository<ProductVisualStrategyVersion, Long> {
    /** 查询商品档案的视觉策略版本。 */
    List<ProductVisualStrategyVersion> findByProfileIdOrderByVersionNumberDesc(Long profileId);
    /** 查询认知版本关联的视觉策略版本。 */
    List<ProductVisualStrategyVersion> findByCognitionVersionIdOrderByVersionNumberDesc(Long cognitionVersionId);
    /** 查询商品档案最新的视觉策略版本。 */
    Optional<ProductVisualStrategyVersion> findTopByProfileIdOrderByVersionNumberDesc(Long profileId);
    /** 查询商品档案指定状态下最新的视觉策略版本。 */
    Optional<ProductVisualStrategyVersion> findTopByProfileIdAndStatusOrderByVersionNumberDesc(Long profileId, String status);
    /** 统计商品档案的视觉策略版本数量。 */
    int countByProfileId(Long profileId);
}
