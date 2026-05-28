package cafe.snails.ecomagents.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link HarnessHooks} 的单元测试，覆盖所有事件类型和工具方法。
 */
@ExtendWith(MockitoExtension.class)
class HarnessHooksTest {

    @Mock
    private SseEmitter emitter;
    @Mock
    private ObjectMapper objectMapper;

    private final long agentId = 42L;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final StringBuilder partialContent = new StringBuilder();
    private HarnessHooks hooks;

    @BeforeEach
    void setUp() throws Exception {
        hooks = new HarnessHooks(emitter, objectMapper, agentId, completed, partialContent);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void preReasoning_shouldSendEvent() throws Exception {
        hooks.onEvent(mock(PreReasoningEvent.class)).block();
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void reasoningChunk_withNullChunk_shouldNotSend() throws Exception {
        var event = mock(ReasoningChunkEvent.class);
        when(event.getIncrementalChunk()).thenReturn(null);
        hooks.onEvent(event).block();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void preActing_shouldSendEvent() throws Exception {
        hooks.onEvent(mock(PreActingEvent.class)).block();
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void postActing_shouldSendEvent() throws Exception {
        hooks.onEvent(mock(PostActingEvent.class)).block();
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void errorEvent_shouldSendAndComplete() throws Exception {
        var event = mock(ErrorEvent.class);
        when(event.getError()).thenReturn(new RuntimeException("timeout"));
        hooks.onEvent(event).block();
        assertTrue(completed.get());
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void errorEvent_withNullError_shouldUseDefaultMessage() throws Exception {
        var event = mock(ErrorEvent.class);
        when(event.getError()).thenReturn(null);
        hooks.onEvent(event).block();
        assertTrue(completed.get());
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void errorEvent_shouldOnlyCompleteOnce() throws Exception {
        var event = mock(ErrorEvent.class);
        when(event.getError()).thenReturn(new RuntimeException("e"));
        hooks.onEvent(event).block();
        hooks.onEvent(event).block();
        verify(emitter, times(1)).complete();
    }

    @Test
    void priority_shouldBe100() {
        assertEquals(100, hooks.priority());
    }

    // ==================== friendlyError ====================

    @Test
    void friendlyError_nullInput() {
        assertEquals("模型响应异常，请重试", HarnessHooks.friendlyError(null));
    }

    @Test
    void friendlyError_eof() {
        assertEquals("模型响应连接中断，请重试", HarnessHooks.friendlyError("EOF"));
        assertEquals("模型响应连接中断，请重试", HarnessHooks.friendlyError("transport error"));
        assertEquals("模型响应连接中断，请重试", HarnessHooks.friendlyError("SSE/NDJSON parse"));
    }

    @Test
    void friendlyError_createProcess() {
        assertEquals("本地命令工具不可用，请启用网页搜索工具或检查 Windows Shell 配置",
                HarnessHooks.friendlyError("CreateProcess error=2"));
        assertEquals("本地命令工具不可用，请启用网页搜索工具或检查 Windows Shell 配置",
                HarnessHooks.friendlyError("Cannot run program \"sh\""));
    }

    @Test
    void friendlyError_timeout() {
        assertEquals("模型响应超时，请重试", HarnessHooks.friendlyError("timeout"));
        assertEquals("模型响应超时，请重试", HarnessHooks.friendlyError("Timeout"));
    }

    @Test
    void friendlyError_auth() {
        assertEquals("模型 API 认证失败，请检查 API Key", HarnessHooks.friendlyError("401"));
        assertEquals("模型 API 认证失败，请检查 API Key", HarnessHooks.friendlyError("Unauthorized"));
        assertEquals("模型 API 认证失败，请检查 API Key", HarnessHooks.friendlyError("Authentication failed"));
    }

    @Test
    void friendlyError_quota() {
        assertEquals("模型 API 额度不足，请检查账户余额", HarnessHooks.friendlyError("402"));
        assertEquals("模型 API 额度不足，请检查账户余额", HarnessHooks.friendlyError("Insufficient quota"));
        assertEquals("模型 API 额度不足，请检查账户余额", HarnessHooks.friendlyError("quota exhausted"));
    }

    @Test
    void friendlyError_rateLimit() {
        assertEquals("模型请求频率过高，请稍后重试", HarnessHooks.friendlyError("429"));
        assertEquals("模型请求频率过高，请稍后重试", HarnessHooks.friendlyError("Rate limit exceeded"));
    }

    @Test
    void friendlyError_retriesExhausted() {
        assertEquals("模型响应异常，请重试", HarnessHooks.friendlyError("Retries exhausted"));
    }

    @Test
    void friendlyError_longMessage() {
        assertEquals("对话服务异常，请稍后重试", HarnessHooks.friendlyError("a".repeat(81)));
    }

    @Test
    void friendlyError_shortUnknown() {
        assertEquals("custom error", HarnessHooks.friendlyError("custom error"));
    }

    @Test
    void friendlyError_empty() {
        assertEquals("", HarnessHooks.friendlyError(""));
    }
}
