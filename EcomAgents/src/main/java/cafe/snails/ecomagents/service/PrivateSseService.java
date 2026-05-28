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
 * 私聊 SSE 推送服务，按 userId 索引 SseEmitter。
 */
@Service
@RequiredArgsConstructor
public class PrivateSseService {

    private static final Logger log = LoggerFactory.getLogger(PrivateSseService.class);
    private final ObjectMapper objectMapper;

    /** userId → List<SseEmitter> */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("Private SSE completed, userId={}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("Private SSE timeout, userId={}", userId);
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("Private SSE error, userId={}: {}", userId, e.getMessage());
        });

        return emitter;
    }

    /** 向指定用户推送事件 */
    public void sendToUser(Long userId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);
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
