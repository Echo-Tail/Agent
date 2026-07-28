package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

/**
 * 商品评论数据访问层。
 */
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long>, JpaSpecificationExecutor<ProductReview> {
    /** 按项目、ASIN 和外部评论 ID 查询评论。 */
    Optional<ProductReview> findByProjectIdAndAsinAndExternalReviewId(Long projectId, String asin, String externalReviewId);
    /** 按项目、ASIN 和内容哈希查询评论。 */
    Optional<ProductReview> findByProjectIdAndAsinAndContentHash(Long projectId, String asin, String contentHash);
    /** 统计项目采集的评论数量。 */
    long countByProjectId(Long projectId);
    /** 统计项目内指定 ASIN 的评论数量。 */
    long countByProjectIdAndAsin(Long projectId, String asin);
    /** 查询项目评论并按主键排序。 */
    List<ProductReview> findByProjectIdOrderById(Long projectId);
}
