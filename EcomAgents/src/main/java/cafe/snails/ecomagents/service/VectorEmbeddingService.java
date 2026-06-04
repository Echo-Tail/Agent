package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 向量嵌入（Vector Embedding）服务，基于 pgvector 扩展。
 * <p>提供知识库文档的向量化索引与语义搜索：
 * <ul>
 *   <li>文本分块 → 调用 Embedding API → 存入 pgvector 索引</li>
 *   <li>支持余弦相似度搜索（{@code <=>} 算子）与 HNSW 索引加速</li>
 *   <li>自动初始化 vector 扩展和 knowledge_embeddings 表</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class VectorEmbeddingService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(VectorEmbeddingService.class);

    /** 文本分块大小（字符数） */
    private static final int CHUNK_SIZE = 1200;
    /** 相邻分块之间的重叠字符数，保持上下文连续性 */
    private static final int CHUNK_OVERLAP = 200;

    /** LLM/Embedding 配置。 */
    private final LlmConfig llmConfig;
    /** WebClient 构建器，用于调用 Embedding API。 */
    private final WebClient.Builder webClientBuilder;
    /** 数据源，用于初始化 pgvector 表和批量写入向量。 */
    private final DataSource dataSource;

    /** JPA EntityManager，用于执行向量检索原生 SQL。 */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 为文档重新构建向量索引。
     * <p>执行流程：
     * <ol>
     *   <li>若内容为空则删除已有索引并返回</li>
     *   <li>检查 Embedding API 和数据库表是否可用</li>
     *   <li>删除该文档的旧索引</li>
     *   <li>将文本分块并逐块调用 Embedding API</li>
     *   <li>检查向量维度是否与配置一致</li>
     *   <li>逐条写入 knowledge_embeddings 表</li>
     * </ol>
     * </p>
     *
     * @param kbId    知识库 ID
     * @param docId   文档 ID
     * @param content 文档全文内容（可为空，空时仅删除索引）
     */
    @Transactional
    public void reindexDocument(Long kbId, Long docId, String content) {
        if (content == null || content.isBlank()) {
            deleteEmbeddings(docId);
            return;
        }
        if (!hasEmbeddingConfig()) {
            log.info("Embedding API is not configured; skipped vector indexing for document {}", docId);
            return;
        }
        if (!ensureEmbeddingsTable()) {
            log.warn("knowledge_embeddings table is unavailable; skipped vector indexing for document {}", docId);
            return;
        }

        deleteEmbeddings(docId);
        int indexed = 0;
        for (String chunk : chunkText(content)) {
            List<Double> embedding = embed(chunk);
            if (embedding.isEmpty()) {
                continue;
            }
            if (!hasExpectedDimension(embedding, docId)) {
                return;
            }
            try {
                Query insert = entityManager.createNativeQuery("""
                        INSERT INTO knowledge_embeddings (document_id, kb_id, chunk_text, embedding)
                        VALUES (:docId, :kbId, :chunkText, CAST(:embedding AS vector))
                        """);
                insert.setParameter("docId", docId);
                insert.setParameter("kbId", kbId);
                insert.setParameter("chunkText", chunk);
                insert.setParameter("embedding", toVectorLiteral(embedding));
                insert.executeUpdate();
            } catch (RuntimeException e) {
                log.warn("Failed to store embedding chunk for document {}: {}", docId, e.getMessage());
                return;
            }
            indexed++;
        }
        log.info("Indexed {} embedding chunks for document {}", indexed, docId);
    }

    /**
     * 删除指定文档的所有向量索引。
     *
     * @param docId 文档 ID
     * @throws IllegalStateException 数据库异常时抛出（非表不存在的情况）
     */
    @Transactional
    public void deleteEmbeddings(Long docId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM knowledge_embeddings WHERE document_id = ?")) {
            statement.setLong(1, docId);
            int deleted = statement.executeUpdate();
            if (deleted > 0) {
                log.debug("Deleted {} embeddings for document {}", deleted, docId);
            }
        } catch (SQLException e) {
            if (isMissingEmbeddingsTable(e)) {
                log.debug("knowledge_embeddings table does not exist; skipped deleting embeddings for document {}", docId);
                return;
            }
            throw new IllegalStateException("Failed to delete embeddings for document " + docId, e);
        }
    }

    /**
     * 在指定知识库中执行语义相似度搜索。
     * <p>将查询文本向量化，使用 pgvector 的余弦距离（{@code <=>}）计算相似度，
     * 返回超过阈值的块文本，按相似度降序排列。</p>
     *
     * @param kbIds      知识库 ID 列表
     * @param queryText  查询文本
     * @param limit      最大返回结果数
     * @param threshold  相似度阈值（0~1，仅返回 >= 此值的结果）
     * @return 匹配的文本块列表；API/表不可用时返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> searchSimilar(List<Long> kbIds, String queryText, int limit, double threshold) {
        if (kbIds == null || kbIds.isEmpty() || queryText == null || queryText.isBlank()) {
            return List.of();
        }
        if (!hasEmbeddingConfig() || !ensureEmbeddingsTable()) {
            return List.of();
        }

        List<Double> queryEmbedding = embed(queryText);
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }
        if (!hasExpectedDimension(queryEmbedding, null)) {
            return List.of();
        }

        try {
            Query query = entityManager.createNativeQuery("""
                    SELECT chunk_text
                    FROM knowledge_embeddings
                    WHERE kb_id IN (:kbIds)
                      AND embedding IS NOT NULL
                      AND (1 - (embedding <=> CAST(:embedding AS vector))) >= :threshold
                    ORDER BY embedding <=> CAST(:embedding AS vector)
                    LIMIT :limit
                    """);
            query.setParameter("kbIds", kbIds);
            query.setParameter("embedding", toVectorLiteral(queryEmbedding));
            query.setParameter("threshold", threshold);
            query.setParameter("limit", Math.max(1, limit));
            return new ArrayList<>((List<String>) query.getResultList());
        } catch (RuntimeException e) {
            if (isMissingEmbeddingsTable(e)) {
                return List.of();
            }
            log.warn("Vector search failed; falling back to text search: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 删除指定知识库的所有向量索引。
     *
     * @param kbId 知识库 ID
     */
    @Transactional
    public void deleteByKbId(Long kbId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM knowledge_embeddings WHERE kb_id = ?")) {
            statement.setLong(1, kbId);
            int deleted = statement.executeUpdate();
            log.debug("Deleted {} embeddings for KB {}", deleted, kbId);
        } catch (SQLException e) {
            if (isMissingEmbeddingsTable(e)) {
                log.debug("knowledge_embeddings table does not exist; skipped deleting embeddings for KB {}", kbId);
                return;
            }
            throw new IllegalStateException("Failed to delete embeddings for KB " + kbId, e);
        }
    }

    /**
     * 确保 pgvector 扩展和 knowledge_embeddings 表已创建。
     * <p>自动执行：CREATE EXTENSION vector → CREATE TABLE IF NOT EXISTS → 创建 HNSW 索引。</p>
     *
     * @return 表可用时返回 true；SQL 异常时返回 false
     */
    private boolean ensureEmbeddingsTable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute(String.format("""
                    CREATE TABLE IF NOT EXISTS knowledge_embeddings (
                        id BIGSERIAL PRIMARY KEY,
                        document_id BIGINT NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
                        kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
                        chunk_text TEXT NOT NULL,
                        embedding VECTOR(%d),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """, embeddingDimension()));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_kb_id ON knowledge_embeddings(kb_id)");
            try {
                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_hnsw
                        ON knowledge_embeddings USING hnsw (embedding vector_cosine_ops)
                        """);
            } catch (SQLException e) {
                log.warn("Unable to initialize pgvector HNSW index: {}", e.getMessage());
            }
            return true;
        } catch (SQLException e) {
            log.warn("Unable to initialize pgvector schema: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将文档全文分割为固定大小的文本块。
     * <p>每块 {@value #CHUNK_SIZE} 字符，相邻块重叠 {@value #CHUNK_OVERLAP} 字符。</p>
     *
     * @param content 文档全文
     * @return 文本块列表
     */
    private List<String> chunkText(String content) {
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    /**
     * 调用 Embedding API 将文本向量化。
     * <p>使用 WebClient 发送 POST 请求到配置的 Embedding API URL，
     * 返回 float 向量。请求超时由 {@link LlmConfig#getReadTimeout()} 控制。</p>
     *
     * @param input 输入文本
     * @return 向量（浮点数列表）；API 不可用或失败时返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<Double> embed(String input) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", llmConfig.getEmbeddingModel());
            body.put("input", input);

            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(resolveEmbeddingApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveEmbeddingApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(Math.max(10, llmConfig.getReadTimeout())));

            if (response == null) {
                return List.of();
            }
            Object dataObj = response.get("data");
            if (!(dataObj instanceof List<?> data) || data.isEmpty()) {
                return List.of();
            }
            Object first = data.get(0);
            if (!(first instanceof Map<?, ?> firstMap)) {
                return List.of();
            }
            Object embeddingObj = firstMap.get("embedding");
            if (!(embeddingObj instanceof List<?> values)) {
                return List.of();
            }

            List<Double> embedding = new ArrayList<>(values.size());
            for (Object value : values) {
                if (value instanceof Number number) {
                    embedding.add(number.doubleValue());
                }
            }
            return embedding;
        } catch (RuntimeException e) {
            log.warn("Embedding request failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 Embedding API 的完整 URL。
     * <p>优先级：显式配置的 embeddingApiUrl > 从 chat API URL 推导 > OpenAI 默认。</p>
     *
     * @return Embedding API URL
     */
    private String resolveEmbeddingApiUrl() {
        String configured = llmConfig.getEmbeddingApiUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String apiUrl = llmConfig.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return "https://api.openai.com/v1/embeddings";
        }
        try {
            URI uri = URI.create(apiUrl);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String embeddingPath = path.endsWith("/chat/completions")
                    ? path.substring(0, path.length() - "/chat/completions".length()) + "/embeddings"
                    : "/v1/embeddings";
            int port = uri.getPort();
            String base = port > 0
                    ? uri.getScheme() + "://" + uri.getHost() + ":" + port
                    : uri.getScheme() + "://" + uri.getHost();
            return base + embeddingPath;
        } catch (RuntimeException e) {
            return "https://api.openai.com/v1/embeddings";
        }
    }

    /**
     * 解析 Embedding API 密钥。
     * <p>优先级：显式配置的 embeddingApiKey > chat API key。</p>
     *
     * @return API 密钥
     */
    private String resolveEmbeddingApiKey() {
        String embeddingApiKey = llmConfig.getEmbeddingApiKey();
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            return embeddingApiKey;
        }
        return llmConfig.getApiKey();
    }

    /** 检查 Embedding API 是否已配置（密钥非空且非占位符） */
    private boolean hasEmbeddingConfig() {
        String apiKey = resolveEmbeddingApiKey();
        return apiKey != null && !apiKey.isBlank() && !"sk-placeholder".equals(apiKey);
    }

    /** 将浮点数列表转换为 PostgreSQL vector 字面量（如 "[0.1,0.2,0.3]"） */
    private String toVectorLiteral(List<Double> embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (Double value : embedding) {
            joiner.add(Double.toString(value));
        }
        return joiner.toString();
    }

    /** 返回配置的向量维度 */
    private int embeddingDimension() {
        return Math.max(1, llmConfig.getEmbeddingDimension());
    }

    /**
     * 检查向量维度是否与配置一致。
     *
     * @param embedding 向量
     * @param docId     文档 ID（用于日志；为 null 时表示搜索场景）
     * @return 维度匹配时返回 true
     */
    private boolean hasExpectedDimension(List<Double> embedding, Long docId) {
        int expected = embeddingDimension();
        if (embedding.size() == expected) {
            return true;
        }
        if (docId == null) {
            log.warn("Embedding dimension mismatch during vector search: expected {}, got {}", expected, embedding.size());
        } else {
            log.warn("Embedding dimension mismatch for document {}: expected {}, got {}; skipped vector indexing",
                    docId, expected, embedding.size());
        }
        return false;
    }

    /**
     * 判断异常是否为 knowledge_embeddings 表不存在的错误。
     * <p>支持 PostgreSQL 的错误码（42P01）和中英文错误消息匹配。</p>
     *
     * @param e 异常
     * @return 表不存在时返回 true
     */
    private boolean isMissingEmbeddingsTable(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains("knowledge_embeddings")
                    && (message.contains("does not exist") || message.contains("不存在") || message.contains("42P01"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
