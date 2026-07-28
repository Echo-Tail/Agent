package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 商品卖点认知版本数据访问层。
 */
public interface ProductSellingPointCognitionVersionRepository extends JpaRepository<ProductSellingPointCognitionVersion, Long> {
    /** 查询商品档案的认知版本并按版本号倒序排列。 */
    List<ProductSellingPointCognitionVersion> findByProfileIdOrderByVersionNumberDesc(Long profileId);
    /** 查询商品档案最新的认知版本。 */
    Optional<ProductSellingPointCognitionVersion> findTopByProfileIdOrderByVersionNumberDesc(Long profileId);
    /** 查询商品档案指定状态下最新的认知版本。 */
    Optional<ProductSellingPointCognitionVersion> findTopByProfileIdAndStatusOrderByVersionNumberDesc(Long profileId, String status);
    /** 统计商品档案的认知版本数量。 */
    int countByProfileId(Long profileId);
}
