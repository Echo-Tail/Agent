package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
import cafe.snails.ecomagents.service.rag.KnowledgeUnitParserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link LocalKnowledgeIndexService} 单元测试。
 * 覆盖 KnowledgeSearchResult 构造、searchSimilarDetailed 边界条件。
 */
@ExtendWith(MockitoExtension.class)
class LocalKnowledgeIndexServiceTest {

    @Mock
    private KnowledgeDocumentRepository docRepository;
    @Mock
    private LlmConfig llmConfig;

    private LocalKnowledgeIndexService service;

    @BeforeEach
    void setUp() {
        lenient().when(llmConfig.getRagRetrievalTimeout()).thenReturn(8L);
        lenient().when(llmConfig.getEmbeddingApiUrl()).thenReturn("http://localhost:11434");
        lenient().when(llmConfig.getEmbeddingModel()).thenReturn("bge-m3:latest");
        lenient().when(llmConfig.getEmbeddingDimension()).thenReturn(1024);
        lenient().when(llmConfig.getReadTimeout()).thenReturn(30L);

        service = new LocalKnowledgeIndexService(
                docRepository, llmConfig,
                new KnowledgeUnitParserService(new ObjectMapper()));
    }

    // ==================== KnowledgeSearchResult Tests ====================

    @Test
    void searchResult_shouldConstructWith6Args() {
        var result = new LocalKnowledgeIndexService.KnowledgeSearchResult(
                List.of("chunk1"), false, false, 1, 100L, 10);
        assertEquals(1, result.chunks().size());
        assertEquals("chunk1", result.chunks().get(0));
        assertFalse(result.degraded());
        assertFalse(result.timedOut());
        assertEquals(1, result.searchedKbCount());
        assertEquals(100L, result.elapsedMillis());
        assertEquals(10, result.returnedChars());
        // Default sources and scores should be empty
        assertTrue(result.sources().isEmpty());
        assertTrue(result.scores().isEmpty());
    }

    @Test
    void searchResult_shouldConstructWith8Args() {
        var result = new LocalKnowledgeIndexService.KnowledgeSearchResult(
                List.of("chunk1"), List.of("vector"), List.of(0.95),
                false, false, 1, 100L, 10);
        assertEquals(1, result.chunks().size());
        assertEquals("vector", result.sources().get(0));
        assertEquals(0.95, result.scores().get(0), 0.001);
    }

    // ==================== searchSimilarDetailed edge cases ====================

    @Test
    void searchSimilarDetailed_shouldReturnEmptyForNullKbIds() {
        var result = service.searchSimilarDetailed(null, "query", 5, 0.15);
        assertTrue(result.chunks().isEmpty());
        assertEquals(0, result.searchedKbCount());
    }

    @Test
    void searchSimilarDetailed_shouldReturnEmptyForEmptyKbIds() {
        var result = service.searchSimilarDetailed(List.of(), "query", 5, 0.15);
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void searchSimilarDetailed_shouldReturnEmptyForBlankQuery() {
        var result = service.searchSimilarDetailed(List.of(1L), "", 5, 0.15);
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void searchSimilarDetailed_shouldReturnEmptyForNullQuery() {
        var result = service.searchSimilarDetailed(List.of(1L), null, 5, 0.15);
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void searchSimilarDetailed_shouldHandleNullKbId() {
        var result = service.searchSimilarDetailed(Arrays.asList((Long) null), "query", 5, 0.15);
        assertTrue(result.chunks().isEmpty());
        assertEquals(0, result.searchedKbCount());
    }

    @Test
    void searchSimilarDetailed_shouldBeDegradedForUnavailableIndex() {
        // No KBs indexed yet, so index will be unavailable
        var result = service.searchSimilarDetailed(List.of(999L), "query", 5, 0.15);
        assertTrue(result.degraded());
        assertTrue(result.chunks().isEmpty());
    }

    // ==================== toDocument tests ====================

    @Test
    void knowledgeSearchResult_sourcesAndScoresAlignWithChunks() {
        var result = new LocalKnowledgeIndexService.KnowledgeSearchResult(
                List.of("a", "b"), List.of("vector", "sparse"), List.of(0.9, 0.5),
                false, false, 2, 50L, 4);
        assertEquals(2, result.chunks().size());
        assertEquals(2, result.sources().size());
        assertEquals(2, result.scores().size());
        assertEquals("vector", result.sources().get(0));
        assertEquals(0.5, result.scores().get(1), 0.001);
    }

    @Test
    void emptySearchResult_shouldHaveZeroCounts() {
        var result = new LocalKnowledgeIndexService.KnowledgeSearchResult(
                List.of(), false, false, 0, 0, 0);
        assertTrue(result.chunks().isEmpty());
        assertEquals(0, result.returnedChars());
    }
}
