package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.PublicAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PublicAssetRepository extends JpaRepository<PublicAsset, Long> {

    Page<PublicAsset> findBySpaceIdOrderByCreatedAtDesc(Long spaceId, Pageable pageable);

    Page<PublicAsset> findBySpaceIdIsNullOrderByCreatedAtDesc(Pageable pageable);

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

    Optional<PublicAsset> findByContentHash(String contentHash);
}
