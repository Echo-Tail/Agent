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
import org.springframework.transaction.annotation.Propagation;
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

@Service
@RequiredArgsConstructor
public class VectorEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(VectorEmbeddingService.class);
    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;

    private final LlmConfig llmConfig;
    private final WebClient.Builder webClientBuilder;
    private final DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    private boolean ensureEmbeddingsTable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS knowledge_embeddings (
                        id BIGSERIAL PRIMARY KEY,
                        document_id BIGINT NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
                        kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
                        chunk_text TEXT NOT NULL,
                        embedding VECTOR(1536),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
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

    private String resolveEmbeddingApiKey() {
        String embeddingApiKey = llmConfig.getEmbeddingApiKey();
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            return embeddingApiKey;
        }
        return llmConfig.getApiKey();
    }

    private boolean hasEmbeddingConfig() {
        String apiKey = resolveEmbeddingApiKey();
        return apiKey != null && !apiKey.isBlank() && !"sk-placeholder".equals(apiKey);
    }

    private String toVectorLiteral(List<Double> embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (Double value : embedding) {
            joiner.add(Double.toString(value));
        }
        return joiner.toString();
    }

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
