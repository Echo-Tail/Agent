package cafe.snails.ecomagents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE 服务测试，验证私聊和群聊 SSE 连接创建与空接收方容错。
 */
class SseServiceTest {

    @Test
    void privateSseService_shouldCreateEmitterAndIgnoreMissingRecipients() {
        var service = new PrivateSseService(new ObjectMapper());

        var emitter = service.createEmitter(1L);
        service.sendToUser(1L, "message", Map.of("content", "hello"));
        service.sendToUser(2L, "message", Map.of("content", "missing"));

        assertNotNull(emitter);
    }

    @Test
    void groupSseService_shouldCreateEmitterAndIgnoreMissingGroups() {
        var service = new GroupSseService(new ObjectMapper());

        var emitter = service.createEmitter(1L);
        service.broadcast(1L, "message", Map.of("content", "hello"));
        service.broadcast(2L, "message", Map.of("content", "missing"));

        assertNotNull(emitter);
    }
}
