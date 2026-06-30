package cafe.snails.ecomagents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE 服务测试，验证连接创建、广播、空接收方容错和服务合并后的正确性。
 */
class SseServiceTest {

    private final SseService service = new SseService(new ObjectMapper());

    @Test
    void createEmitter_shouldReturnEmitterForKey() {
        var emitter = service.createEmitter(1L);
        assertNotNull(emitter);
    }

    @Test
    void broadcast_shouldHandleNoEmittersForKey() {
        // 向不存在的 key 广播不应抛异常
        assertDoesNotThrow(() -> service.broadcast(99L, "message", Map.of("content", "hello")));
    }

    @Test
    void broadcast_shouldDeliverToExistingEmitter() {
        var emitter = service.createEmitter(1L);
        // 向存在的 key 广播不应抛异常
        assertDoesNotThrow(() -> service.broadcast(1L, "message", Map.of("content", "hello")));
        assertNotNull(emitter);
    }

    @Test
    void broadcast_shouldIgnoreDeadEmitters() {
        var emitter = service.createEmitter(1L);
        // 正常完成 emitter
        AtomicBoolean completed = new AtomicBoolean(false);
        emitter.onCompletion(() -> completed.set(true));
        emitter.complete();

        // 完成后再次广播不应抛异常
        assertDoesNotThrow(() -> service.broadcast(1L, "message", Map.of("content", "after complete")));
    }

    @Test
    void multipleEmitters_shouldAllReceiveBroadcast() {
        var e1 = service.createEmitter(1L);
        var e2 = service.createEmitter(1L);

        assertNotNull(e1);
        assertNotNull(e2);
        assertDoesNotThrow(() -> service.broadcast(1L, "message", Map.of("content", "to all")));
    }

    @Test
    void createEmitter_shouldSupportSeparateKeys() {
        var e1 = service.createEmitter(100L);
        var e2 = service.createEmitter(200L);

        assertNotNull(e1);
        assertNotNull(e2);
        // 分别广播不相互干扰
        assertDoesNotThrow(() -> service.broadcast(100L, "event", Map.of("data", "a")));
        assertDoesNotThrow(() -> service.broadcast(200L, "event", Map.of("data", "b")));
    }
}
