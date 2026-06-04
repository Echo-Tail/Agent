package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.GalleryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 画廊作品数据访问层。
 */
public interface GalleryItemRepository extends JpaRepository<GalleryItem, Long> {

    /** 查询已发布状态的画廊作品，按创建时间倒序 */
    Page<GalleryItem> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 查询指定用户发布的所有作品 */
    List<GalleryItem> findByUserId(Long userId);

    /** 查询指定用户发布的指定状态的作品 */
    List<GalleryItem> findByUserIdAndStatus(Long userId, String status);

    /** 检查某个记录是否已被发布（防止重复发布） */
    boolean existsByRecordIdAndStatus(Long recordId, String status);

    /** 查询用户尚未发布到画廊的 recordId 列表 */
    @Query("SELECT gi.recordId FROM GalleryItem gi WHERE gi.userId = :userId AND gi.status = :status")
    List<Long> findPublishedRecordIdsByUserId(@Param("userId") Long userId, @Param("status") String status);

    /** 查询画廊作品详情（含关联信息） */
    Optional<GalleryItem> findByIdAndStatus(Long id, String status);

    /** 查询某个记录的非特定状态的行（用于取消发布后重发时复用旧行） */
    List<GalleryItem> findByRecordIdAndUserIdAndStatusNot(Long recordId, Long userId, String status);
}
