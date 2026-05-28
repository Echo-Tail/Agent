package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link HarnessAgentManager} 单元测试。
 * <p>覆盖 Agent 创建 / 模型解析 / URL 提取 / 工具注册逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class HarnessAgentManagerTest {

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AiModelRepository aiModelRepository;
    @Mock
    private ToolConfigRepository toolConfigRepository;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private WorkspaceConfig workspaceConfig;
    @Mock
    private LlmConfig llmConfig;
    @Mock
    private ObjectMapper objectMapper;

    private HarnessAgentManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(workspaceConfig.getRoot()).thenReturn(System.getProperty("java.io.tmpdir") + "/agent-test");
        lenient().when(llmConfig.getApiKey()).thenReturn("sk-global-key");
        lenient().when(llmConfig.getModel()).thenReturn("gpt-4");
        manager = new HarnessAgentManager(agentRepository, aiModelRepository, toolConfigRepository,
                knowledgeBaseService, workspaceConfig, llmConfig, objectMapper);
    }

    // ==================== createSimpleAgent ====================

    @Test
    void createSimpleAgent_shouldSucceed() {
        var agent = Agent.builder().id(1L).name("测试Agent").modelId(10L).build();
        var model = AiModel.builder().id(10L).enabled(true).apiKey("sk-model-key")
                .modelName("gpt-4").apiUrl("https://api.openai.com/v1/chat/completions").build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(aiModelRepository.findById(10L)).thenReturn(Optional.of(model));

        var harnessAgent = manager.createSimpleAgent(1L);

        assertNotNull(harnessAgent);
        assertEquals("测试Agent", harnessAgent.getName());
    }

    @Test
    void createSimpleAgent_agentNotFound_shouldThrow() {
        when(agentRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> manager.createSimpleAgent(999L));
    }

    @Test
    void createSimpleAgent_modelDisabled_shouldThrow() {
        var agent = Agent.builder().id(1L).modelId(10L).build();
        var model = AiModel.builder().id(10L).enabled(false).build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(aiModelRepository.findById(10L)).thenReturn(Optional.of(model));

        assertThrows(IllegalArgumentException.class, () -> manager.createSimpleAgent(1L));
    }

    @Test
    void createSimpleAgent_modelNotFound_shouldThrow() {
        var agent = Agent.builder().id(1L).modelId(999L).build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(aiModelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> manager.createSimpleAgent(1L));
    }

    // ==================== createChatAgent ====================

    @Test
    void createChatAgent_shouldSucceed() {
        var agent = Agent.builder().id(1L).name("ChatAgent").modelId(10L).build();
        var model = AiModel.builder().id(10L).enabled(true)
                .modelName("gpt-4").apiUrl("https://api.openai.com/v1/chat/completions").build();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(aiModelRepository.findById(10L)).thenReturn(Optional.of(model));

        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        var completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        var sb = new StringBuilder();
        var harnessAgent = manager.createChatAgent(1L, emitter, 100L, completed, sb);

        assertNotNull(harnessAgent);
    }

    // ==================== resolveApiKey ====================

    @Test
    void resolveApiKey_shouldUseModelKey() throws Exception {
        var agent = Agent.builder().id(1L).modelId(10L).build();
        var model = AiModel.builder().id(10L).apiKey("sk-model-key").build();
        when(aiModelRepository.findById(10L)).thenReturn(Optional.of(model));

        assertEquals("sk-model-key", invokeResolveApiKey(agent));
    }

    @Test
    void resolveApiKey_shouldFallbackToGlobal() throws Exception {
        var agent = Agent.builder().id(1L).modelId(null).build();
        assertEquals("sk-global-key", invokeResolveApiKey(agent));
    }

    // ==================== resolveModelName ====================

    @Test
    void resolveModelName_shouldUseModelName() throws Exception {
        var agent = Agent.builder().id(1L).modelId(10L).build();
        var model = AiModel.builder().id(10L).modelName("gpt-4-turbo").build();
        when(aiModelRepository.findById(10L)).thenReturn(Optional.of(model));

        assertEquals("gpt-4-turbo", invokeResolveModelName(agent));
    }

    @Test
    void resolveModelName_shouldFallbackToGlobal() throws Exception {
        var agent = Agent.builder().id(1L).modelId(null).build();
        assertEquals("gpt-4", invokeResolveModelName(agent));
    }

    // ==================== extractBaseUrl ====================

    @Test
    void extractBaseUrl_standardUrl() throws Exception {
        assertEquals("https://api.openai.com", invokeExtractBaseUrl("https://api.openai.com/v1/chat/completions"));
    }

    @Test
    void extractBaseUrl_withPort() throws Exception {
        assertEquals("http://localhost:8080", invokeExtractBaseUrl("http://localhost:8080/v1/chat/completions"));
    }

    @Test
    void extractBaseUrl_nullUrl_shouldReturnDefault() throws Exception {
        assertEquals("https://api.openai.com", invokeExtractBaseUrl(null));
    }

    // ==================== extractPath ====================

    @Test
    void extractPath_standardUrl() throws Exception {
        assertEquals("/v1/chat/completions", invokeExtractPath("https://api.openai.com/v1/chat/completions"));
    }

    @Test
    void extractPath_withQuery() throws Exception {
        assertEquals("/v1/chat/completions?model=gpt-4", invokeExtractPath("https://api.openai.com/v1/chat/completions?model=gpt-4"));
    }

    @Test
    void extractPath_nullUrl_shouldReturnDefault() throws Exception {
        assertEquals("/v1/chat/completions", invokeExtractPath(null));
    }

    // ==================== extractApiKey ====================

    @Test
    void extractApiKey_validJson() throws Exception {
        doReturn(java.util.Map.of("apiKey", "tavily-key"))
                .when(objectMapper).readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class));
        assertEquals("tavily-key", invokeExtractApiKey("{\"apiKey\":\"tavily-key\"}"));
    }

    @Test
    void extractApiKey_nullConfig() throws Exception {
        assertNull(invokeExtractApiKey(null));
    }

    @Test
    void extractApiKey_emptyConfig() throws Exception {
        assertNull(invokeExtractApiKey(""));
    }

    @Test
    void extractApiKey_invalidJson() throws Exception {
        doThrow(new com.fasterxml.jackson.core.JsonParseException(null, "parse error"))
                .when(objectMapper).readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class));
        assertNull(invokeExtractApiKey("invalid"));
    }

    // ==================== findModel ====================

    @Test
    void findModel_shouldReturnModel() throws Exception {
        var agent = Agent.builder().id(1L).modelId(10L).build();
        var model = AiModel.builder().id(10L).build();
        when(aiModelRepository.findById(10L)).thenReturn(Optional.of(model));

        assertNotNull(invokeFindModel(agent));
    }

    @Test
    void findModel_nullModelId_shouldReturnNull() throws Exception {
        var agent = Agent.builder().id(1L).modelId(null).build();
        assertNull(invokeFindModel(agent));
    }

    @Test
    void findModel_notFound_shouldReturnNull() throws Exception {
        var agent = Agent.builder().id(1L).modelId(999L).build();
        when(aiModelRepository.findById(999L)).thenReturn(Optional.empty());
        assertNull(invokeFindModel(agent));
    }

    // ==================== Reflection helpers ====================

    private String invokeResolveApiKey(Agent agent) throws Exception {
        Method m = HarnessAgentManager.class.getDeclaredMethod("resolveApiKey", Agent.class);
        m.setAccessible(true);
        return (String) m.invoke(manager, agent);
    }

    private String invokeResolveModelName(Agent agent) throws Exception {
        Method m = HarnessAgentManager.class.getDeclaredMethod("resolveModelName", Agent.class);
        m.setAccessible(true);
        return (String) m.invoke(manager, agent);
    }

    private String invokeExtractBaseUrl(String url) throws Exception {
        Method m = HarnessAgentManager.class.getDeclaredMethod("extractBaseUrl", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, url);
    }

    private String invokeExtractPath(String url) throws Exception {
        Method m = HarnessAgentManager.class.getDeclaredMethod("extractPath", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, url);
    }

    private String invokeExtractApiKey(String configJson) throws Exception {
        Method m = HarnessAgentManager.class.getDeclaredMethod("extractApiKey", String.class);
        m.setAccessible(true);
        return (String) m.invoke(manager, configJson);
    }

    private AiModel invokeFindModel(Agent agent) throws Exception {
        Method m = HarnessAgentManager.class.getDeclaredMethod("findModel", Agent.class);
        m.setAccessible(true);
        return (AiModel) m.invoke(manager, agent);
    }
}
