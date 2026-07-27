package cafe.snails.ecomagents.repository.review;

import cafe.snails.ecomagents.model.review.ReviewProjectProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewProjectProductRepository extends JpaRepository<ReviewProjectProduct, Long> {
    List<ReviewProjectProduct> findByProjectIdOrderById(Long projectId);
    boolean existsByProjectIdAndAsin(Long projectId, String asin);
    void deleteByProjectId(Long projectId);
}
