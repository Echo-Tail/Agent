package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long>, JpaSpecificationExecutor<ProductReview> {
    Optional<ProductReview> findByProjectIdAndAsinAndExternalReviewId(Long projectId, String asin, String externalReviewId);
    Optional<ProductReview> findByProjectIdAndAsinAndContentHash(Long projectId, String asin, String contentHash);
    long countByProjectId(Long projectId);
    long countByProjectIdAndAsin(Long projectId, String asin);
    List<ProductReview> findByProjectIdOrderById(Long projectId);
}
