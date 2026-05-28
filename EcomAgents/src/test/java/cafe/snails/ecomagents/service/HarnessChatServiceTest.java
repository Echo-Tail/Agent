package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.dto.ToolAvailability;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link HarnessChatService} 单元测试。
 * <p>覆盖私有辅助方法（通过反射调用）和关键公共方法。</p>
 */
@ExtendWith(MockitoExtension.class)
class HarnessChatServiceTest {

    @Mock
    private HarnessAgentManager harnessAgentManager;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private Executor llmTaskExecutor;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AiModelRepository aiModelRepository;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private TokenUsageService tokenUsageService;
    @Mock
    private TokenCounter tokenCounter;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LlmConfig llmConfig;
    @Mock
    private AgentToolAvailabilityService toolAvailabilityService;

    private HarnessChatService harnessChatService;

    @BeforeEach
    void setUp() {
        harnessChatService = new HarnessChatService(
                harnessAgentManager, objectMapper, llmTaskExecutor, sessionMapper,
                agentRepository, aiModelRepository, knowledgeBaseService,
                tokenUsageService, tokenCounter, fileStorageService,
                sessionRepository, userRepository, llmConfig, toolAvailabilityService);
    }

    // ==================== requiresRealtimeLookup ====================

    @Test
    void requiresRealtimeLookup_weatherKeywords() throws Exception {
        assertTrue(invokeRequiresRealtimeLookup("今天天气怎么样"));
        assertTrue(invokeRequiresRealtimeLookup("北京气温多少"));
        assertTrue(invokeRequiresRealtimeLookup("明天会下雨吗"));
    }

    @Test
    void requiresRealtimeLookup_newsKeywords() throws Exception {
        assertTrue(invokeRequiresRealtimeLookup("最新新闻"));
        assertTrue(invokeRequiresRealtimeLookup("有什么实时消息"));
    }

    @Test
    void requiresRealtimeLookup_priceAndFinance() throws Exception {
        assertTrue(invokeRequiresRealtimeLookup("苹果股价"));
        assertTrue(invokeRequiresRealtimeLookup("美元汇率"));
    }

    @Test
    void requiresRealtimeLookup_englishKeywords() throws Exception {
        assertTrue(invokeRequiresRealtimeLookup("What is the weather today"));
        assertTrue(invokeRequiresRealtimeLookup("latest news about AI"));
        assertTrue(invokeRequiresRealtimeLookup("current price of Bitcoin"));
    }

    @Test
    void requiresRealtimeLookup_shouldReturnFalseForNormalQuery() throws Exception {
        assertFalse(invokeRequiresRealtimeLookup("Python 列表推导式怎么用"));
        assertFalse(invokeRequiresRealtimeLookup("如何优化数据库查询"));
    }

    @Test
    void requiresRealtimeLookup_shouldReturnFalseForEmpty() throws Exception {
        assertFalse(invokeRequiresRealtimeLookup(""));
        assertFalse(invokeRequiresRealtimeLookup(null));
    }

    // ==================== classifyTimeoutStage ====================

    @Test
    void classifyTimeoutStage_ragTimeout() throws Exception {
        assertEquals("rag_timeout", invokeClassifyTimeoutStage(new RuntimeException("Knowledge retrieval timeout")));
        assertEquals("rag_timeout", invokeClassifyTimeoutStage(new RuntimeException("retrieve_knowledge failed")));
    }

    @Test
    void classifyTimeoutStage_toolTimeout() throws Exception {
        assertEquals("tool_timeout", invokeClassifyTimeoutStage(new RuntimeException("tool execution timeout")));
        assertEquals("tool_timeout", invokeClassifyTimeoutStage(new RuntimeException("Tool call failed")));
    }

    @Test
    void classifyTimeoutStage_modelTimeout() throws Exception {
        assertEquals("model_timeout", invokeClassifyTimeoutStage(new RuntimeException("timeout")));
        assertEquals("model_timeout", invokeClassifyTimeoutStage(new RuntimeException("Request Timeout")));
    }

    @Test
    void classifyTimeoutStage_nullMessage() throws Exception {
        assertEquals("unknown", invokeClassifyTimeoutStage(new RuntimeException()));
    }

    @Test
    void classifyTimeoutStage_unknownError() throws Exception {
        assertEquals("unknown", invokeClassifyTimeoutStage(new RuntimeException("disk full")));
    }

    // ==================== truncate ====================

    @Test
    void truncate_nullInput() throws Exception {
        assertNull(invokeTruncate(null, 10));
    }

    @Test
    void truncate_shortString() throws Exception {
        assertEquals("hello", invokeTruncate("hello", 10));
    }

    @Test
    void truncate_longString() throws Exception {
        assertEquals("abcdefghij", invokeTruncate("abcdefghijklmn", 10));
    }

    @Test
    void truncate_exactLength() throws Exception {
        assertEquals("12345", invokeTruncate("12345", 5));
    }

    // ==================== buildErrorDiagnostic ====================

    @Test
    void buildErrorDiagnostic_shouldIncludeStageAndType() throws Exception {
        var e = new RuntimeException("timeout");
        String result = invokeBuildErrorDiagnostic(e, "模型响应超时");
        assertTrue(result.contains("stage=model_timeout"));
        assertTrue(result.contains("friendly=模型响应超时"));
        assertTrue(result.contains("type=RuntimeException"));
        assertTrue(result.contains("raw=timeout"));
    }

    // ==================== addRealtimeToolInstruction ====================

    @Test
    void addRealtimeToolInstruction_shouldAppendInstruction() throws Exception {
        String result = invokeAddRealtimeToolInstruction("用户问题");
        assertTrue(result.startsWith("用户问题"));
        assertTrue(result.contains("web_search"));
    }

    // ==================== addFileCapabilityInstruction ====================

    @Test
    void addFileCapabilityInstruction_shouldAppendInstruction() throws Exception {
        String result = invokeAddFileCapabilityInstruction("用户问题");
        assertTrue(result.startsWith("用户问题"));
        assertTrue(result.contains("<file"));
        assertTrue(result.contains("retrieve_knowledge"));
    }

    // ==================== resolveModel ====================

    @Test
    void resolveModel_shouldReturnModel() throws Exception {
        var agent = Agent.builder().id(1L).modelId(10L).build();
        var model = AiModel.builder().id(10L).name("gpt-4").build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(aiModelRepository.findById(10L)).thenReturn(Optional.of(model));

        AiModel result = invokeResolveModel(1L);
        assertEquals("gpt-4", result.getName());
    }

    @Test
    void resolveModel_shouldReturnNullWhenNoModelId() throws Exception {
        var agent = Agent.builder().id(1L).modelId(null).build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        assertNull(invokeResolveModel(1L));
    }

    @Test
    void resolveModel_shouldReturnNullWhenAgentNotFound() throws Exception {
        when(agentRepository.findById(999L)).thenReturn(Optional.empty());
        assertNull(invokeResolveModel(999L));
    }

    // ==================== resolveAgentName ====================

    @Test
    void resolveAgentName_shouldReturnName() throws Exception {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(
                Agent.builder().id(1L).name("测试Agent").build()));
        assertEquals("测试Agent", invokeResolveAgentName(1L));
    }

    @Test
    void resolveAgentName_shouldReturnNullForNullId() throws Exception {
        assertNull(invokeResolveAgentName(null));
    }

    @Test
    void resolveAgentName_shouldReturnNullWhenNotFound() throws Exception {
        when(agentRepository.findById(999L)).thenReturn(Optional.empty());
        assertNull(invokeResolveAgentName(999L));
    }

    // ==================== resolveUsername ====================

    @Test
    void resolveUsername_shouldReturnName() throws Exception {
        var user = User.builder().id(1L).username("张三").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertEquals("张三", invokeResolveUsername(1L));
    }

    @Test
    void resolveUsername_shouldReturnNullForNullId() throws Exception {
        assertNull(invokeResolveUsername(null));
    }

    @Test
    void resolveUsername_shouldReturnNullWhenNotFound() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertNull(invokeResolveUsername(999L));
    }

    // ==================== enrichWithKnowledge (GENERIC mode) ====================

    @Test
    void enrichWithKnowledge_genericMode_shouldInjectContext() throws Exception {
        var agent = Agent.builder()
                .id(1L).ragMode("GENERIC")
                .knowledgeBaseIds(List.of(10L))
                .build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(knowledgeBaseService.buildKnowledgeContext(List.of(10L), "问题"))
                .thenReturn("\n\n知识库内容");

        String result = invokeEnrichWithKnowledge(1L, "问题");
        assertTrue(result.contains("问题"));
        assertTrue(result.contains("知识库内容"));
    }

    @Test
    void enrichWithKnowledge_agenticMode_shouldNotInject() throws Exception {
        var agent = Agent.builder()
                .id(1L).ragMode("AGENTIC")
                .knowledgeBaseIds(List.of(10L))
                .build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        String result = invokeEnrichWithKnowledge(1L, "问题");
        assertEquals("问题", result);
    }

    @Test
    void enrichWithKnowledge_noAgent_shouldReturnOriginal() throws Exception {
        when(agentRepository.findById(999L)).thenReturn(Optional.empty());
        assertEquals("问题", invokeEnrichWithKnowledge(999L, "问题"));
    }

    @Test
    void enrichWithKnowledge_noKbIds_shouldReturnOriginal() throws Exception {
        var agent = Agent.builder().id(1L).ragMode("GENERIC").knowledgeBaseIds(null).build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        assertEquals("问题", invokeEnrichWithKnowledge(1L, "问题"));
    }

    // ==================== enrichWithHistory ====================

    @Test
    void enrichWithHistory_shouldInjectHistory() throws Exception {
        var session = Session.builder()
                .harnessSessionId("session-1")
                .messages(List.of(
                        SessionMessage.builder().role("user").content("你好").build(),
                        SessionMessage.builder().role("assistant").content("你好！有什么可以帮助的？").build()
                ))
                .build();
        when(sessionRepository.findByHarnessSessionIdWithMessages("session-1"))
                .thenReturn(Optional.of(session));

        String result = invokeEnrichWithHistory("session-1", "今天天气如何");
        assertTrue(result.contains("历史对话"));
        assertTrue(result.contains("你好"));
        assertTrue(result.contains("今天天气如何"));
    }

    @Test
    void enrichWithHistory_shouldReturnOriginalWhenNoSession() throws Exception {
        when(sessionRepository.findByHarnessSessionIdWithMessages("invalid"))
                .thenReturn(Optional.empty());
        assertEquals("问题", invokeEnrichWithHistory("invalid", "问题"));
    }

    @Test
    void enrichWithHistory_shouldReturnOriginalWhenNoMessages() throws Exception {
        var session = Session.builder().harnessSessionId("empty-session").messages(List.of()).build();
        when(sessionRepository.findByHarnessSessionIdWithMessages("empty-session"))
                .thenReturn(Optional.of(session));
        assertEquals("问题", invokeEnrichWithHistory("empty-session", "问题"));
    }

    @Test
    void enrichWithHistory_shouldHandleExceptionGracefully() throws Exception {
        when(sessionRepository.findByHarnessSessionIdWithMessages("error"))
                .thenThrow(new RuntimeException("DB error"));
        assertEquals("问题", invokeEnrichWithHistory("error", "问题"));
    }

    // ==================== simpleChat (mock scenario) ====================

    // ==================== simpleChat ====================

    @Test
    void simpleChat_agentNotFound_shouldReturnNull() {
        when(agentRepository.findById(1L)).thenReturn(Optional.empty());
        assertNull(harnessChatService.simpleChat(1L, "hello"));
    }

    @Test
    void simpleChat_ragModeGeneric_shouldCallAgent() throws Exception {
        var agent = Agent.builder().id(1L).ragMode("GENERIC").knowledgeBaseIds(null).build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        assertNull(harnessChatService.simpleChat(1L, "hello"));
        // Should not throw
    }

    // ==================== Reflection helpers ====================

    private boolean invokeRequiresRealtimeLookup(String content) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("requiresRealtimeLookup", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(harnessChatService, content);
    }

    private String invokeClassifyTimeoutStage(Exception e) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("classifyTimeoutStage", Exception.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, e);
    }

    private String invokeTruncate(String s, int maxLen) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("truncate", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, s, maxLen);
    }

    private String invokeBuildErrorDiagnostic(Exception e, String friendly) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("buildErrorDiagnostic", Exception.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, e, friendly);
    }

    private String invokeAddRealtimeToolInstruction(String content) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("addRealtimeToolInstruction", String.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, content);
    }

    private String invokeAddFileCapabilityInstruction(String content) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("addFileCapabilityInstruction", String.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, content);
    }

    private AiModel invokeResolveModel(Long agentId) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("resolveModel", Long.class);
        m.setAccessible(true);
        return (AiModel) m.invoke(harnessChatService, agentId);
    }

    private String invokeResolveAgentName(Long agentId) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("resolveAgentName", Long.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, agentId);
    }

    private String invokeResolveUsername(Long userId) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("resolveUsername", Long.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, userId);
    }

    private String invokeEnrichWithKnowledge(Long agentId, String content) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("enrichWithKnowledge", Long.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, agentId, content);
    }

    private String invokeEnrichWithHistory(String sessionId, String content) throws Exception {
        Method m = HarnessChatService.class.getDeclaredMethod("enrichWithHistory", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(harnessChatService, sessionId, content);
    }
}
