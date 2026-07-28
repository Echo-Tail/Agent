package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProductProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 商品档案图片数据访问层。
 */
public interface ProductProfileImageRepository extends JpaRepository<ProductProfileImage, Long> {
    /** 查询指定商品档案的全部图片。 */
    List<ProductProfileImage> findByProfileId(Long profileId);
    /** 删除指定商品档案的全部图片。 */
    void deleteByProfileId(Long profileId);
}
