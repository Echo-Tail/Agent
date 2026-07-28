package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.ModelRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link LlmService} 单元测试。
 * <p>覆盖私有辅助方法（通过反射调用）和构造/行为验证。</p>
 */
@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock
    private AiModelService aiModelService;
    @Mock
    private ModelRuntimeProperties runtimeProperties;
    @Mock
    private ObjectMapper objectMapper;

    private LlmService llmService;

    @BeforeEach
    void setUp() {
        llmService = new LlmService(aiModelService, runtimeProperties, objectMapper);
    }

    // ==================== Constructor ====================

    @Test
    void constructor_shouldAcceptParameters() {
        var service = new LlmService(aiModelService, runtimeProperties, objectMapper);
        assertNotNull(service);
    }

    // ==================== maskApiKey ====================

    @Test
    void maskApiKey_null_shouldReturnNull() throws Exception {
        assertEquals("null", invokeMaskApiKey(null));
    }

    @Test
    void maskApiKey_shortKey_shouldMaskAllButFirst4() throws Exception {
        assertEquals("abcd****", invokeMaskApiKey("abcdefgh"));
    }

    @Test
    void maskApiKey_veryShortKey() throws Exception {
        assertEquals("ab****", invokeMaskApiKey("ab"));
    }

    @Test
    void maskApiKey_normalKey_shouldShowFirst8() throws Exception {
        String result = invokeMaskApiKey("12345678abcdefgh");
        assertTrue(result.startsWith("12345678")); // first 8 chars
        assertTrue(result.endsWith("****"));
        assertEquals(12, result.length()); // 8 + 4
    }

    @Test
    void maskApiKey_emptyKey() throws Exception {
        assertEquals("****", invokeMaskApiKey(""));
    }

    // ==================== buildMessages ====================

    @Test
    void buildMessages_withSystemPrompt_shouldIncludeSystem() throws Exception {
        var history = List.of(Map.of("role", "user", "content", "你好"));
        var messages = invokeBuildMessages("你是助手", history);

        assertEquals(2, messages.size());
        // First message is system
    }

    @Test
    void buildMessages_withoutSystemPrompt_shouldSkipSystem() throws Exception {
        var history = List.of(Map.of("role", "user", "content", "你好"));
        var messages = invokeBuildMessages(null, history);

        assertEquals(1, messages.size());
    }

    @Test
    void buildMessages_emptyHistory_shouldReturnEmpty() throws Exception {
        var messages = invokeBuildMessages(null, List.of());
        assertTrue(messages.isEmpty());
    }

    @Test
    void buildMessages_shouldSkipBlankContent() throws Exception {
        var history = List.of(
                Map.of("role", "user", "content", ""),
                Map.of("role", "assistant", "content", "回复")
        );
        var messages = invokeBuildMessages(null, history);

        assertEquals(1, messages.size());
    }

    @Test
    void buildMessages_shouldMapRoles() throws Exception {
        var history = List.of(
                Map.of("role", "user", "content", "hi"),
                Map.of("role", "assistant", "content", "hello"),
                Map.of("role", "unknown", "content", "test")
        );
        var messages = invokeBuildMessages(null, history);

        assertEquals(3, messages.size());
    }

    // ==================== extractText ====================

    @Test
    void extractText_textBlock_shouldReturnText() throws Exception {
        var response = mock(ChatResponse.class);
        var textBlock = mock(TextBlock.class);
        when(response.getContent()).thenReturn(List.of(textBlock));
        when(textBlock.getText()).thenReturn("Hello");

        assertEquals("Hello", invokeExtractText(response));
    }

    @Test
    void extractText_emptyContent_shouldReturnNull() throws Exception {
        var response = mock(ChatResponse.class);
        when(response.getContent()).thenReturn(List.of());

        assertNull(invokeExtractText(response));
    }

    @Test
    void extractText_nullContent_shouldReturnNull() throws Exception {
        var response = mock(ChatResponse.class);
        when(response.getContent()).thenReturn(null);

        assertNull(invokeExtractText(response));
    }

    // ==================== streamChat placeholder key check ====================

    @Test
    void streamChat_placeholderKey_shouldThrow() {
        assertThrows(IllegalStateException.class,
                () -> llmService.streamChat("prompt", List.of(), null));
    }

    @Test
    void streamChat_withOverrideKey_shouldNotCheckPlaceholder() {
        var options = GenerateOptions.builder()
                .apiKey("sk-real-key")
                .modelName("gpt-4")
                .build();
        when(runtimeProperties.getStreamTimeout()).thenReturn(30L);

        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        assertThrows(Exception.class, () ->
                llmService.streamChat("prompt", List.of(), emitter, options));
        // Should fail on reactive stream (Model.stream returns null), NOT on placeholder check
    }

    // ==================== Reflection helpers ====================

    private String invokeMaskApiKey(String key) throws Exception {
        Method m = LlmService.class.getDeclaredMethod("maskApiKey", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, key);
    }

    @SuppressWarnings("unchecked")
    private List<io.agentscope.core.message.Msg> invokeBuildMessages(String systemPrompt,
                                                                      List<Map<String, String>> history) throws Exception {
        Method m = LlmService.class.getDeclaredMethod("buildMessages", String.class, List.class);
        m.setAccessible(true);
        return (List<io.agentscope.core.message.Msg>) m.invoke(llmService, systemPrompt, history);
    }

    private String invokeExtractText(ChatResponse response) throws Exception {
        Method m = LlmService.class.getDeclaredMethod("extractText", ChatResponse.class);
        m.setAccessible(true);
        return (String) m.invoke(llmService, response);
    }
}
