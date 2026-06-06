package cafe.snails.ecomagents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 推送服务，按 Long 类型的 key（groupId 或 userId）管理 SseEmitter 列表。
 * <p>统一替代了原有的 GroupSseService 和 PrivateSseService，两者逻辑完全相同。</p>
 */
@Service
@RequiredArgsConstructor
public class SseService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    /** SSE 事件数据 JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /** Long key → List<SseEmitter> */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByKey = new ConcurrentHashMap<>();

    /**
     * 为指定 key（群 ID / 用户 ID）创建一个 SSE 连接。
     */
    public SseEmitter createEmitter(Long key) {
        SseEmitter emitter = new SseEmitter(0L); // 无超时
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByKey.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE completed, key={}", key);
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE timeout, key={}", key);
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE error, key={}: {}", key, e.getMessage());
        });

        return emitter;
    }

    /**
     * 向指定 key 对应的所有在线客户端推送事件。
     */
    public void broadcast(Long key, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByKey.get(key);
        if (emitters == null || emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(data)));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }
}
