package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.service.HarnessChatService;
import cafe.snails.ecomagents.service.SessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 聊天控制器测试，验证 SSE 对话入口、会话解析和错误响应。
 */
class ChatControllerTest {

    @Mock
    private HarnessChatService harnessChatService;
    @Mock
    private SessionMapper sessionMapper;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(harnessChatService, sessionMapper);
    }

    @Test
    void streamChat_shouldReturnErrorEmitterForBlankContent() {
        var emitter = controller.streamChat(1L, Map.of("content", "   "), 7L);

        assertNotNull(emitter);
        verifyNoInteractions(sessionMapper, harnessChatService);
    }

    @Test
    void streamChat_shouldReturnErrorEmitterForInaccessibleSession() {
        when(sessionMapper.resolveHarnessSessionId(42L, 1L, 7L)).thenThrow(new IllegalArgumentException("no access"));

        var emitter = controller.streamChat(1L, Map.of("sessionId", 42L, "content", "hello"), 7L);

        assertNotNull(emitter);
        verifyNoInteractions(harnessChatService);
    }

    @Test
    void streamChat_shouldResolveHarnessSessionAndDelegate() {
        var emitter = new SseEmitter();
        when(sessionMapper.resolveHarnessSessionId(42L, 1L, 7L)).thenReturn("harness-session");
        when(harnessChatService.streamChat(1L, "harness-session", "hello", 7L)).thenReturn(emitter);

        var result = controller.streamChat(1L, Map.of("sessionId", "42", "content", "hello"), 7L);

        assertSame(emitter, result);
    }

    @Test
    void streamChat_shouldAllowMissingDbSessionId() {
        var emitter = new SseEmitter();
        when(sessionMapper.resolveHarnessSessionId(null, 1L, 7L)).thenReturn("new-session");
        when(harnessChatService.streamChat(1L, "new-session", "hello", 7L)).thenReturn(emitter);

        var result = controller.streamChat(1L, Map.of("content", "hello"), 7L);

        assertSame(emitter, result);
    }
}
