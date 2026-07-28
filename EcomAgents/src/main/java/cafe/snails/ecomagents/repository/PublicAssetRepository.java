package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.PublicAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 公共素材数据访问层。
 */
public interface PublicAssetRepository extends JpaRepository<PublicAsset, Long> {

    /** 分页查询指定素材空间的素材。 */
    Page<PublicAsset> findBySpaceIdOrderByCreatedAtDesc(Long spaceId, Pageable pageable);

    /** 分页查询未归属素材空间的素材。 */
    Page<PublicAsset> findBySpaceIdIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** 按空间、关键词、上传者和日期范围分页检索素材。 */
    @Query(value = "SELECT * FROM public_assets a WHERE " +
           "(:spaceId IS NULL OR a.space_id = :spaceId) AND " +
           "(:keyword IS NULL OR a.file_name ILIKE CONCAT('%', CAST(:keyword AS text), '%')) AND " +
           "(:uploadedBy IS NULL OR a.uploaded_by = :uploadedBy) AND " +
           "(:startDate IS NULL OR a.created_at >= CAST(:startDate AS timestamp)) AND " +
           "(:endDate IS NULL OR a.created_at < CAST(:endDate AS timestamp) + INTERVAL '1 day') " +
           "ORDER BY a.created_at DESC",
           countQuery = "SELECT COUNT(*) FROM public_assets a WHERE " +
           "(:spaceId IS NULL OR a.space_id = :spaceId) AND " +
           "(:keyword IS NULL OR a.file_name ILIKE CONCAT('%', CAST(:keyword AS text), '%')) AND " +
           "(:uploadedBy IS NULL OR a.uploaded_by = :uploadedBy) AND " +
           "(:startDate IS NULL OR a.created_at >= CAST(:startDate AS timestamp)) AND " +
           "(:endDate IS NULL OR a.created_at < CAST(:endDate AS timestamp) + INTERVAL '1 day')",
           nativeQuery = true)
    Page<PublicAsset> search(@Param("spaceId") Long spaceId,
                             @Param("keyword") String keyword,
                             @Param("uploadedBy") Long uploadedBy,
                             @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate,
                             Pageable pageable);

    /** 按内容哈希查询素材。 */
    Optional<PublicAsset> findByContentHash(String contentHash);
}
