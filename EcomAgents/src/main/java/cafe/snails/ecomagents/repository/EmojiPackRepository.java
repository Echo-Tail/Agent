package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.EmojiPack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 表情包仓库，支持按分类和创建时间排序查询。
 */
public interface EmojiPackRepository extends JpaRepository<EmojiPack, Long> {
    /** 按分类查询表情包 */
    List<EmojiPack> findByCategoryOrderByCreatedAtAsc(String category);
    /** 查询所有表情包 */
    List<EmojiPack> findAllByOrderByCreatedAtAsc();
}
