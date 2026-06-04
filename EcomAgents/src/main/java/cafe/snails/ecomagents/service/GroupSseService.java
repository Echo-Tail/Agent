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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 群聊 SSE 推送服务。
 * <p>每个群维护一个 SseEmitter 列表，群内新消息/文件/成员变更通过 SSE 广播给所有在线成员。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupSseService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(GroupSseService.class);
    /** SSE 事件数据 JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /** groupId → List<SseEmitter> */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> groupEmitters = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 为群创建一个 SSE 连接。
     */
    public SseEmitter createEmitter(Long groupId) {
        SseEmitter emitter = new SseEmitter(0L); // 无超时
        CopyOnWriteArrayList<SseEmitter> emitters = groupEmitters.computeIfAbsent(groupId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE completed, groupId={}", groupId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE timeout, groupId={}", groupId);
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE error, groupId={}: {}", groupId, e.getMessage());
        });

        return emitter;
    }

    /**
     * 向群内所有在线成员推送事件。
     */
    public void broadcast(Long groupId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = groupEmitters.get(groupId);
        if (emitters == null || emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(data)));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }
}
