package cafe.snails.ecomagents.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 向量嵌入服务 — 管理知识库文档的向量化存储和检索。
 * <p>使用 PostgreSQL pgvector 扩展存储文档嵌入向量，支持余弦相似度检索。</p>
 */
@Service
public class VectorEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(VectorEmbeddingService.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 为指定知识库和文档重建向量索引。
     * <p>先删除该文档的旧嵌入，再重新分块、嵌入、存储。</p>
     */
    @Transactional
    public void reindexDocument(Long kbId, Long docId, String content) {
        // 1. Delete old embeddings for this document
        deleteEmbeddings(docId);

        // 2. Chunk and embed
        // TODO: integrate with embedding API (e.g., DashScope text-embedding-v3)
        // For now, store the document text for future indexing
        log.info("Queued for embedding: kbId={}, docId={}, chars={}", kbId, docId, content != null ? content.length() : 0);
    }

    /**
     * 删除指定文档的所有嵌入向量。
     */
    @Transactional
    public void deleteEmbeddings(Long docId) {
        Query query = entityManager.createNativeQuery(
                "DELETE FROM knowledge_embeddings WHERE document_id = :docId");
        query.setParameter("docId", docId);
        int deleted = query.executeUpdate();
        if (deleted > 0) {
            log.debug("Deleted {} embeddings for document {}", deleted, docId);
        }
    }

    /**
     * 向量检索：在指定知识库中搜索与 query 最相似的文档片段。
     *
     * @param kbIds  知识库 ID 列表
     * @param query  嵌入查询向量（TODO: receive embedding vector instead of raw text）
     * @param limit  最大返回结果数
     * @param threshold 相似度阈值
     * @return 匹配的文档片段文本列表
     */
    @SuppressWarnings("unchecked")
    public List<String> searchSimilar(List<Long> kbIds, String query, int limit, double threshold) {
        // TODO: implement when embedding API is integrated
        // This would use a native pgvector query like:
        // SELECT chunk_text FROM knowledge_embeddings
        // WHERE kb_id IN (:kbIds)
        // ORDER BY embedding <=> :queryEmbedding
        // LIMIT :limit
        return List.of();
    }

    /**
     * 删除指定知识库的所有嵌入向量。
     */
    @Transactional
    public void deleteByKbId(Long kbId) {
        Query query = entityManager.createNativeQuery(
                "DELETE FROM knowledge_embeddings WHERE kb_id = :kbId");
        query.setParameter("kbId", kbId);
        int deleted = query.executeUpdate();
        log.debug("Deleted {} embeddings for KB {}", deleted, kbId);
    }
}
