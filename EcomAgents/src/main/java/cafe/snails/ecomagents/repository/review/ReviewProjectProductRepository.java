package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewProjectProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 评论分析项目商品数据访问层。
 */
public interface ReviewProjectProductRepository extends JpaRepository<ReviewProjectProduct, Long> {
    /** 查询项目商品并按主键排序。 */
    List<ReviewProjectProduct> findByProjectIdOrderById(Long projectId);
    /** 判断项目是否已包含指定 ASIN。 */
    boolean existsByProjectIdAndAsin(Long projectId, String asin);
    /** 删除项目关联的全部商品。 */
    void deleteByProjectId(Long projectId);
}
