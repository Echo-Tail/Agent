package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.config.RagProperties;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.KnowledgeAuditLogRepository;
import cafe.snails.ecomagents.repository.KnowledgeBaseRepository;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
import cafe.snails.ecomagents.service.rag.KnowledgeUnitParserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link KnowledgeBaseService} 的单元测试，使用 Mockito 模拟 Repository 层。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseRepository kbRepository;
    @Mock
    private KnowledgeDocumentRepository docRepository;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private KnowledgeAuditLogRepository auditLogRepository;
    @Mock
    private WorkspaceInitService workspaceInitService;
    @Mock
    private LocalKnowledgeIndexService localKnowledgeIndexService;
    @Mock
    private RagProperties ragProperties;

    private KnowledgeBaseService service;
    private KnowledgeBase sampleKb;
    private KnowledgeUnitParserService knowledgeUnitParserService;

    @BeforeEach
    void setUp() {
        lenient().when(ragProperties.getSearchLimit()).thenReturn(5);
        lenient().when(ragProperties.getSimilarityThreshold()).thenReturn(0.15);
        lenient().when(ragProperties.getMaxContextChars()).thenReturn(4000);
        knowledgeUnitParserService = new KnowledgeUnitParserService(new ObjectMapper());
        service = new KnowledgeBaseService(kbRepository, docRepository, auditLogRepository, agentRepository, workspaceInitService, localKnowledgeIndexService, ragProperties, knowledgeUnitParserService);
        sampleKb = KnowledgeBase.builder()
                .id(1L).name("电商运营手册").description("运营规范")
                .createdAt(LocalDate.of(2024, 1, 1)).createdBy(1L).build();
    }

    @Test
    void listKnowledgeBases_shouldReturnAll() {
        when(kbRepository.findAll()).thenReturn(List.of(sampleKb));
        ApiResponse<List<KnowledgeBase>> result = service.listKnowledgeBases();
        assertEquals(1, result.getData().size());
    }

    @Test
    void getKnowledgeBase_existing_shouldReturn() {
        when(kbRepository.findById(1L)).thenReturn(Optional.of(sampleKb));
        ApiResponse<KnowledgeBase> result = service.getKnowledgeBase(1L);
        assertEquals(200, result.getCode());
        assertEquals("电商运营手册", result.getData().getName());
    }

    @Test
    void getKnowledgeBase_notFound_shouldReturn404() {
        when(kbRepository.findById(99L)).thenReturn(Optional.empty());
        ApiResponse<KnowledgeBase> result = service.getKnowledgeBase(99L);
        assertEquals(404, result.getCode());
    }

    @Test
    void createKnowledgeBase_shouldSetDefaults() {
        KnowledgeBase input = KnowledgeBase.builder().name("New KB").build();
        when(kbRepository.save(any())).thenAnswer(i -> {
            KnowledgeBase kb = i.getArgument(0);
            kb.setId(2L);
            return kb;
        });
        ApiResponse<KnowledgeBase> result = service.createKnowledgeBase(input, 1L);
        assertEquals(200, result.getCode());
        assertEquals("New KB", result.getData().getName());
        assertNotNull(result.getData().getCreatedAt());
        assertEquals(1L, result.getData().getCreatedBy());
    }

    @Test
    void updateKnowledgeBase_shouldUpdateFields() {
        when(kbRepository.findById(1L)).thenReturn(Optional.of(sampleKb));
        when(kbRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        KnowledgeBase updates = KnowledgeBase.builder().name("Updated KB").description("New desc").build();
        ApiResponse<KnowledgeBase> result = service.updateKnowledgeBase(1L, updates);
        assertEquals("Updated KB", result.getData().getName());
        assertEquals("New desc", result.getData().getDescription());
    }

    @Test
    void deleteKnowledgeBase_shouldDeleteDocsAndKb() {
        when(kbRepository.existsById(1L)).thenReturn(true);
        when(agentRepository.findByKnowledgeBaseId(1L)).thenReturn(List.of());
        when(docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(1L)).thenReturn(List.of());
        ApiResponse<Void> result = service.deleteKnowledgeBase(1L);
        assertEquals(200, result.getCode());
        verify(localKnowledgeIndexService).evict(1L);
        verify(kbRepository).deleteById(1L);
    }

    @Test
    void deleteKnowledgeBase_shouldUnbindAgentsBeforeDelete() {
        Agent agent = Agent.builder()
                .id(2L)
                .name("Agent")
                .knowledgeBaseIds(List.of(1L, 3L))
                .build();
        when(kbRepository.existsById(1L)).thenReturn(true);
        when(agentRepository.findByKnowledgeBaseId(1L)).thenReturn(List.of(agent));
        when(agentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(1L)).thenReturn(List.of());

        ApiResponse<Void> result = service.deleteKnowledgeBase(1L);

        assertEquals(200, result.getCode());
        assertEquals(List.of(3L), agent.getKnowledgeBaseIds());
        assertDoesNotThrow(() -> agent.getKnowledgeBaseIds().clear());
        verify(agentRepository).save(agent);
        verify(workspaceInitService).updateKnowledgeMd(2L, null);
        verify(localKnowledgeIndexService).evict(1L);
        verify(kbRepository).deleteById(1L);
    }

    @Test
    void deleteDocument_shouldRebuildKnowledgeIndex() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(7L)
                .knowledgeBaseId(1L)
                .fileName("policy.md")
                .content("shipping policy")
                .charCount(15)
                .uploadedAt(LocalDateTime.now())
                .uploadedBy(1L)
                .build();
        when(kbRepository.existsById(1L)).thenReturn(true);
        when(docRepository.findById(7L)).thenReturn(Optional.of(doc));

        ApiResponse<Void> result = service.deleteDocument(1L, 7L, 1L, "admin", null);

        assertEquals(200, result.getCode());
        verify(docRepository).deleteById(7L);
        verify(localKnowledgeIndexService).rebuildAsync(1L);
    }

    @Test
    void search_withKeyword_shouldReturnResults() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(1L).knowledgeBaseId(1L).fileName("test.md")
                .content("退换货政策内容").charCount(100)
                .uploadedAt(LocalDateTime.now()).uploadedBy(1L).build();
        when(docRepository.searchByKeyword("退换货")).thenReturn(List.of(doc));
        ApiResponse<List<KnowledgeDocument>> result = service.search("退换货");
        assertEquals(1, result.getData().size());
    }

    @Test
    void search_withBlankKeyword_shouldReturnEmpty() {
        ApiResponse<List<KnowledgeDocument>> result = service.search("  ");
        assertEquals(0, result.getData().size());
        verify(docRepository, never()).searchByKeyword(any());
    }

    @Test
    void searchInKbs_withValidParams_shouldSearch() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(1L).knowledgeBaseId(1L).fileName("policy.md")
                .content("物流时效标准内容").charCount(150)
                .uploadedAt(LocalDateTime.now()).uploadedBy(1L).build();
        when(docRepository.searchByKeywordAndKbIds("物流", List.of(1L))).thenReturn(List.of(doc));
        ApiResponse<List<KnowledgeDocument>> result = service.searchInKbs("物流", List.of(1L));
        assertEquals(1, result.getData().size());
    }

    @Test
    void buildKnowledgeContext_withDocs_shouldFormatSnippet() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(1L).knowledgeBaseId(1L).fileName("policy.md")
                .content("退换货政策内容").charCount(100)
                .uploadedAt(LocalDateTime.now()).uploadedBy(1L).build();
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), "退换货", 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of(), false, false, 1, 12, 0));
        when(docRepository.searchByKeywordAndKbIds("退换货", List.of(1L))).thenReturn(List.of(doc));
        String context = service.buildKnowledgeContext(List.of(1L), "退换货");
        assertTrue(context.contains("policy.md"));
        assertTrue(context.contains("退换货政策内容"));
    }

    @Test
    void buildKnowledgeContext_withVectorChunks_shouldPreferVectorSearch() {
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), "shipping", 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of("shipping policy chunk"), false, false, 1, 8, 21));

        String context = service.buildKnowledgeContext(List.of(1L), "shipping");

        assertTrue(context.contains("shipping policy chunk"));
        assertTrue(context.contains("Knowledge retrieval status: vector_search"));
    }

    @Test
    void buildKnowledgeContext_vectorTimeout_shouldFallbackToTextSearch() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(1L).knowledgeBaseId(1L).fileName("policy.md")
                .content("shipping policy fallback").charCount(100)
                .uploadedAt(LocalDateTime.now()).uploadedBy(1L).build();
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), "shipping", 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of(), true, true, 1, 8000, 0));
        when(docRepository.searchByKeywordAndKbIds("shipping", List.of(1L))).thenReturn(List.of(doc));

        String context = service.buildKnowledgeContext(List.of(1L), "shipping");

        assertTrue(context.contains("Knowledge retrieval status: text_search_fallback"));
        assertTrue(context.contains("shipping policy fallback"));
    }

    @Test
    void buildKnowledgeContext_textFallback_shouldSnippetAroundMatchedChineseTerm() {
        String prefix = "无关开头".repeat(300);
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(1L).knowledgeBaseId(1L).fileName("products.json")
                .content(prefix + "目标产品支持无线充电并包含磁吸支架。" + "无关结尾".repeat(100))
                .charCount(3000)
                .uploadedAt(LocalDateTime.now()).uploadedBy(1L).build();
        String query = "请根据知识库说明目标产品是否支持无线充电和磁吸支架";
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), query, 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of(), true, false, 1, 750, 0));
        when(docRepository.searchByKeywordAndKbIds(query, List.of(1L))).thenReturn(List.of());
        when(docRepository.findByKnowledgeBaseIdIn(List.of(1L))).thenReturn(List.of(doc));

        String context = service.buildKnowledgeContext(List.of(1L), query);

        assertTrue(context.contains("products.json"));
        assertTrue(context.contains("支持无线充电"));
        assertTrue(context.contains("磁吸支架"));
        assertFalse(context.contains(prefix));
    }

    @Test
    void buildKnowledgeContext_jsonFallback_shouldReturnCompleteMatchedObject() {
        String json = """
                [
                  {
                    "sku": "A-100",
                    "name": "普通支架",
                    "features": ["铝合金"]
                  },
                  {
                    "sku": "B-200",
                    "name": "磁吸无线充电支架",
                    "features": ["无线充电", "磁吸支架", "折叠收纳"],
                    "description": "适合车载和桌面使用"
                  }
                ]
                """;
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(1L)
                .knowledgeBaseId(1L)
                .fileName("products.json")
                .fileType("json")
                .content(json)
                .charCount(json.length())
                .uploadedAt(LocalDateTime.now())
                .uploadedBy(1L)
                .build();
        String query = "B-200 是否支持无线充电和磁吸支架";
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), query, 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of(), true, false, 1, 750, 0));
        when(docRepository.searchByKeywordAndKbIds(query, List.of(1L))).thenReturn(List.of());
        when(docRepository.findByKnowledgeBaseIdIn(List.of(1L))).thenReturn(List.of(doc));

        String context = service.buildKnowledgeContext(List.of(1L), query);

        assertTrue(context.contains("products.json @ $[1]"));
        assertTrue(context.contains("\"sku\" : \"B-200\""));
        assertTrue(context.contains("\"无线充电\""));
        assertTrue(context.contains("\"磁吸支架\""));
        assertFalse(context.contains("\"sku\" : \"A-100\""));
    }

    @Test
    void buildKnowledgeContext_vectorTimeoutWithoutTextMatch_shouldReturnTimeoutStatus() {
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), "missing", 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of(), true, true, 1, 8000, 0));
        when(docRepository.searchByKeywordAndKbIds("missing", List.of(1L))).thenReturn(List.of());
        when(docRepository.findByKnowledgeBaseIdIn(List.of(1L))).thenReturn(List.of());

        String context = service.buildKnowledgeContext(List.of(1L), "missing");

        assertTrue(context.contains("Knowledge retrieval status: vector_timeout_no_fallback"));
    }

    @Test
    void buildKnowledgeContext_shouldRespectConfiguredContextBudget() {
        when(ragProperties.getMaxContextChars()).thenReturn(600);
        String longChunk = "x".repeat(2000);
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), "shipping", 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of(longChunk, longChunk), false, false, 1, 8, 4000));

        String context = service.buildKnowledgeContext(List.of(1L), "shipping");

        assertTrue(context.length() <= 600);
        assertTrue(context.contains("[knowledge context truncated]"));
    }

    @Test
    void buildKnowledgeContext_emptyKbIds_shouldReturnEmpty() {
        assertEquals("", service.buildKnowledgeContext(List.of(), "test"));
        assertEquals("", service.buildKnowledgeContext(null, "test"));
    }

    @Test
    void buildKnowledgeContext_noResults_shouldReturnEmpty() {
        when(localKnowledgeIndexService.searchSimilarDetailed(List.of(1L), "nonexistent", 5, 0.15))
                .thenReturn(new LocalKnowledgeIndexService.KnowledgeSearchResult(List.of(), false, false, 1, 3, 0));
        when(docRepository.searchByKeywordAndKbIds("nonexistent", List.of(1L))).thenReturn(List.of());
        when(docRepository.findByKnowledgeBaseIdIn(List.of(1L))).thenReturn(List.of());
        assertEquals("", service.buildKnowledgeContext(List.of(1L), "nonexistent"));
    }
}
