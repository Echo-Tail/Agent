package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 知识库文档数据访问层。
 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    /** 按知识库 ID 查找文档，按上传时间降序 */
    List<KnowledgeDocument> findByKnowledgeBaseIdOrderByUploadedAtDesc(Long knowledgeBaseId);

    /** 统计指定知识库中的文档数量 */
    long countByKnowledgeBaseId(Long knowledgeBaseId);

    /** 按关键字搜索所有文档内容（不区分大小写） */
    @Query(value = "SELECT d FROM KnowledgeDocument d WHERE LOWER(d.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<KnowledgeDocument> searchByKeyword(@Param("keyword") String keyword);

    /** 在指定知识库范围内按关键字搜索文档内容 */
    @Query(value = "SELECT d FROM KnowledgeDocument d WHERE d.knowledgeBaseId IN :kbIds AND LOWER(d.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<KnowledgeDocument> searchByKeywordAndKbIds(@Param("keyword") String keyword, @Param("kbIds") List<Long> kbIds);
}
