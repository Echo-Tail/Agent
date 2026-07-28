package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.PromptLibrary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 提示词库数据访问层。
 */
public interface PromptLibraryRepository extends JpaRepository<PromptLibrary, Long> {

    /** 按分类、创建者和关键词分页检索提示词。 */
    @Query(value = "SELECT * FROM prompt_library p WHERE " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:createdBy IS NULL OR p.created_by = :createdBy) AND " +
           "(:excludeUser IS NULL OR p.created_by != :excludeUser) AND " +
           "(:keyword IS NULL OR p.prompt ILIKE CONCAT('%', CAST(:keyword AS text), '%') OR p.tags ILIKE CONCAT('%', CAST(:keyword AS text), '%')) " +
           "ORDER BY p.created_at DESC",
           countQuery = "SELECT COUNT(*) FROM prompt_library p WHERE " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:createdBy IS NULL OR p.created_by = :createdBy) AND " +
           "(:excludeUser IS NULL OR p.created_by != :excludeUser) AND " +
           "(:keyword IS NULL OR p.prompt ILIKE CONCAT('%', CAST(:keyword AS text), '%') OR p.tags ILIKE CONCAT('%', CAST(:keyword AS text), '%'))",
           nativeQuery = true)
    Page<PromptLibrary> search(@Param("category") String category,
                               @Param("createdBy") Long createdBy,
                               @Param("excludeUser") Long excludeUser,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
