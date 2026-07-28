package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 图片会话素材数据访问层。
 */
public interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {
    /** 查询会话中未删除的素材并按创建时间排序。 */
    List<ImageAsset> findBySessionIdAndDeletedAtIsNullOrderByCreatedAt(Long sessionId);
    /** 按素材 ID 和用户 ID 查询未删除素材。 */
    Optional<ImageAsset> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
    /** 统计会话中未删除的素材数量。 */
    long countBySessionIdAndDeletedAtIsNull(Long sessionId);
}
