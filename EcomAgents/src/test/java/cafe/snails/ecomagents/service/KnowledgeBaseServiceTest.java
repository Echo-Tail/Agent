package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.KnowledgeAuditLogRepository;
import cafe.snails.ecomagents.repository.KnowledgeBaseRepository;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
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
    private VectorEmbeddingService vectorEmbeddingService;
    @Mock
    private LlmConfig llmConfig;

    private KnowledgeBaseService service;
    private KnowledgeBase sampleKb;

    @BeforeEach
    void setUp() {
        lenient().when(llmConfig.getRagSearchLimit()).thenReturn(5);
        lenient().when(llmConfig.getRagSimilarityThreshold()).thenReturn(0.15);
        service = new KnowledgeBaseService(kbRepository, docRepository, auditLogRepository, agentRepository, workspaceInitService, vectorEmbeddingService, llmConfig);
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
        verify(kbRepository).deleteById(1L);
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
        when(vectorEmbeddingService.searchSimilar(List.of(1L), "退换货", 5, 0.15)).thenReturn(List.of());
        when(docRepository.searchByKeywordAndKbIds("退换货", List.of(1L))).thenReturn(List.of(doc));
        String context = service.buildKnowledgeContext(List.of(1L), "退换货");
        assertTrue(context.contains("policy.md"));
        assertTrue(context.contains("退换货政策内容"));
    }

    @Test
    void buildKnowledgeContext_withVectorChunks_shouldPreferVectorSearch() {
        when(vectorEmbeddingService.searchSimilar(List.of(1L), "shipping", 5, 0.15))
                .thenReturn(List.of("shipping policy chunk"));

        String context = service.buildKnowledgeContext(List.of(1L), "shipping");

        assertTrue(context.contains("shipping policy chunk"));
        verify(docRepository, never()).searchByKeywordAndKbIds(any(), any());
    }

    @Test
    void buildKnowledgeContext_emptyKbIds_shouldReturnEmpty() {
        assertEquals("", service.buildKnowledgeContext(List.of(), "test"));
        assertEquals("", service.buildKnowledgeContext(null, "test"));
    }

    @Test
    void buildKnowledgeContext_noResults_shouldReturnEmpty() {
        when(vectorEmbeddingService.searchSimilar(List.of(1L), "nonexistent", 5, 0.15)).thenReturn(List.of());
        when(docRepository.searchByKeywordAndKbIds("nonexistent", List.of(1L))).thenReturn(List.of());
        when(docRepository.findByKnowledgeBaseIdIn(List.of(1L))).thenReturn(List.of());
        assertEquals("", service.buildKnowledgeContext(List.of(1L), "nonexistent"));
    }
}
