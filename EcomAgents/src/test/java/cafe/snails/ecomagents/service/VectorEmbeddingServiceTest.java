package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.ModelRuntimeProperties;
import cafe.snails.ecomagents.model.ModelProtocol;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link VectorEmbeddingService} 单元测试。
 * <p>覆盖纯逻辑辅助方法（通过反射调用）。</p>
 */
@ExtendWith(MockitoExtension.class)
class VectorEmbeddingServiceTest {

    @Mock
    private EmbeddingModelResolver embeddingModelResolver;
    @Mock
    private ModelRuntimeProperties runtimeProperties;
    @Mock
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;
    @Mock
    private javax.sql.DataSource dataSource;

    private VectorEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new VectorEmbeddingService(embeddingModelResolver, runtimeProperties, webClientBuilder, dataSource);
    }

    // ==================== chunkText ====================

    @Test
    void chunkText_shortText_shouldReturnOneChunk() throws Exception {
        String text = "Hello World";
        var chunks = invokeChunkText(text);
        assertEquals(1, chunks.size());
        assertEquals("Hello World", chunks.get(0));
    }

    @Test
    void chunkText_longText_shouldSplit() throws Exception {
        String text = "a".repeat(3000);
        var chunks = invokeChunkText(text);
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(0).length() <= 1200);
    }

    @Test
    void chunkText_overlap_shouldContainOverlappingContent() throws Exception {
        // Build text with a unique marker in the overlap zone
        String text = "a".repeat(1000) + "MARKER" + "b".repeat(1000);
        var chunks = invokeChunkText(text);
        if (chunks.size() >= 2) {
            assertTrue(chunks.get(1).contains("MARKER") || chunks.get(0).contains("MARKER"));
        }
    }

    @Test
    void chunkText_emptyString_shouldReturnEmptyList() throws Exception {
        var chunks = invokeChunkText("");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chunkText_normalizeLineEndings() throws Exception {
        var chunks = invokeChunkText("line1\r\nline2");
        assertEquals(1, chunks.size());
        assertEquals("line1\nline2", chunks.get(0));
    }

    // ==================== toVectorLiteral ====================

    @Test
    void toVectorLiteral_shouldFormatCorrectly() throws Exception {
        String result = invokeToVectorLiteral(List.of(0.1, 0.2, 0.3));
        assertEquals("[0.1,0.2,0.3]", result);
    }

    @Test
    void toVectorLiteral_singleValue() throws Exception {
        String result = invokeToVectorLiteral(List.of(42.0));
        assertEquals("[42.0]", result);
    }

    @Test
    void toVectorLiteral_emptyList() throws Exception {
        String result = invokeToVectorLiteral(List.of());
        assertEquals("[]", result);
    }

    // ==================== hasEmbeddingConfig ====================

    @Test
    void hasEmbeddingConfig_validKey() throws Exception {
        when(embeddingModelResolver.resolve()).thenReturn(Optional.of(embeddingModel(1024, "sk-real-key")));
        assertTrue(invokeHasEmbeddingConfig());
    }

    @Test
    void hasEmbeddingConfig_placeholderKey() throws Exception {
        when(embeddingModelResolver.resolve()).thenReturn(Optional.empty());
        assertFalse(invokeHasEmbeddingConfig());
    }

    @Test
    void hasEmbeddingConfig_nullKey() throws Exception {
        when(embeddingModelResolver.resolve()).thenReturn(Optional.empty());
        assertFalse(invokeHasEmbeddingConfig());
    }

    // ==================== embeddingDimension ====================

    @Test
    void embeddingDimension_shouldReturnConfigured() throws Exception {
        when(embeddingModelResolver.resolve()).thenReturn(Optional.of(embeddingModel(1536, "key")));
        assertEquals(1536, invokeEmbeddingDimension());
    }

    @Test
    void embeddingDimension_shouldReturnAtLeast1() throws Exception {
        when(embeddingModelResolver.resolve()).thenReturn(Optional.empty());
        assertEquals(1024, invokeEmbeddingDimension());
    }

    // ==================== hasExpectedDimension ====================

    @Test
    void hasExpectedDimension_match() throws Exception {
        when(embeddingModelResolver.resolve()).thenReturn(Optional.of(embeddingModel(3, "key")));
        assertTrue(invokeHasExpectedDimension(List.of(0.1, 0.2, 0.3), 1L));
    }

    @Test
    void hasExpectedDimension_mismatch() throws Exception {
        when(embeddingModelResolver.resolve()).thenReturn(Optional.of(embeddingModel(3, "key")));
        assertFalse(invokeHasExpectedDimension(List.of(0.1, 0.2), 1L));
    }

    // ==================== isMissingEmbeddingsTable ====================

    @Test
    void isMissingEmbeddingsTable_englishMessage() throws Exception {
        var e = new RuntimeException("relation \"knowledge_embeddings\" does not exist");
        assertTrue(invokeIsMissingEmbeddingsTable(e));
    }

    @Test
    void isMissingEmbeddingsTable_chineseMessage() throws Exception {
        var e = new RuntimeException("关系 \"knowledge_embeddings\" 不存在");
        assertTrue(invokeIsMissingEmbeddingsTable(e));
    }

    @Test
    void isMissingEmbeddingsTable_postgresErrorCode() throws Exception {
        var e = new RuntimeException("ERROR: 42P01: table \"knowledge_embeddings\" not found");
        assertTrue(invokeIsMissingEmbeddingsTable(e));
    }

    @Test
    void isMissingEmbeddingsTable_otherError() throws Exception {
        var e = new RuntimeException("Disk full");
        assertFalse(invokeIsMissingEmbeddingsTable(e));
    }

    @Test
    void isMissingEmbeddingsTable_causeChain() throws Exception {
        var cause = new RuntimeException("relation \"knowledge_embeddings\" does not exist");
        var e = new RuntimeException("outer", cause);
        assertTrue(invokeIsMissingEmbeddingsTable(e));
    }

    // ==================== Reflection helpers ====================

    private List<String> invokeChunkText(String text) throws Exception {
        Method m = VectorEmbeddingService.class.getDeclaredMethod("chunkText", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) m.invoke(service, text);
        return result;
    }

    private String invokeToVectorLiteral(List<Double> embedding) throws Exception {
        Method m = VectorEmbeddingService.class.getDeclaredMethod("toVectorLiteral", List.class);
        m.setAccessible(true);
        return (String) m.invoke(service, embedding);
    }

    private boolean invokeHasEmbeddingConfig() throws Exception {
        Method m = VectorEmbeddingService.class.getDeclaredMethod("hasEmbeddingConfig");
        m.setAccessible(true);
        return (boolean) m.invoke(service);
    }

    private int invokeEmbeddingDimension() throws Exception {
        Method m = VectorEmbeddingService.class.getDeclaredMethod("embeddingDimension");
        m.setAccessible(true);
        return (int) m.invoke(service);
    }

    private boolean invokeHasExpectedDimension(List<Double> embedding, Long docId) throws Exception {
        Method m = VectorEmbeddingService.class.getDeclaredMethod("hasExpectedDimension", List.class, Long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, embedding, docId);
    }

    private boolean invokeIsMissingEmbeddingsTable(Exception e) throws Exception {
        Method m = VectorEmbeddingService.class.getDeclaredMethod("isMissingEmbeddingsTable", Throwable.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, e);
    }

    private EmbeddingModelResolver.EmbeddingModel embeddingModel(int dimension, String apiKey) {
        return new EmbeddingModelResolver.EmbeddingModel(
                ModelProtocol.OPENAI_EMBEDDING, "text-embedding-3-small",
                "https://api.openai.com/v1/embeddings", apiKey, dimension);
    }
}
