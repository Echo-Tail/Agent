package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductProfileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 商品档案版本数据访问层。
 */
public interface ProductProfileVersionRepository extends JpaRepository<ProductProfileVersion, Long> {
    /** 查询商品档案版本并按版本号倒序排列。 */
    List<ProductProfileVersion> findByProfileIdOrderByVersionNumberDesc(Long profileId);
    /** 查询商品档案的最新版本。 */
    Optional<ProductProfileVersion> findTopByProfileIdOrderByVersionNumberDesc(Long profileId);
    /** 统计商品档案的版本数量。 */
    int countByProfileId(Long profileId);
}
