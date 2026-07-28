package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 商品档案数据访问层。
 */
public interface ProductProfileRepository extends JpaRepository<ProductProfile, Long>, JpaSpecificationExecutor<ProductProfile> {
    /** 判断商品名称是否已存在。 */
    boolean existsByProductName(String productName);
    /** 判断 SKU 是否已存在。 */
    boolean existsBySku(String sku);
    /** 判断型号是否已存在。 */
    boolean existsByModelNumber(String modelNumber);
    /** 按 SKU 查询商品档案。 */
    Optional<ProductProfile> findBySku(String sku);
    /** 按型号查询商品档案。 */
    Optional<ProductProfile> findByModelNumber(String modelNumber);
}
