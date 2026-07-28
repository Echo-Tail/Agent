package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ImageSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * 图片工作区会话数据访问层。
 */
public interface ImageSessionRepository extends JpaRepository<ImageSession, Long> {
    /** 查询用户未删除的会话并按更新时间倒序排列。 */
    List<ImageSession> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long userId);
    /** 按会话 ID 和用户 ID 查询未删除会话。 */
    Optional<ImageSession> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
