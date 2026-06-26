package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageExpressionCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageExpressionCacheRepository extends JpaRepository<ImageExpressionCache, Long> {
    /** 查询某张图片的所有分析记录，按时间倒序 */
    List<ImageExpressionCache> findAllByImageUrlHashOrderByCreatedAtDesc(String imageUrlHash);

    /** 按图片 + 提示词查询（预留，prompt 参数化后使用） */
    Optional<ImageExpressionCache> findTopByImageUrlHashAndPromptHashOrderByCreatedAtDesc(
            String imageUrlHash, String promptHash);
}
