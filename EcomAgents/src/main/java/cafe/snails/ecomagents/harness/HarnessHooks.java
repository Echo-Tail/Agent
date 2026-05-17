package cafe.snails.ecomagents.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AgentScope Hook 实现，将 HarnessAgent 的执行事件转为 SSE 推送。
 * <p>
 * 事件映射：
 * <ul>
 *   <li>PreReasoningEvent → type=reasoning（LLM 开始推理）</li>
 *   <li>ReasoningChunkEvent → type=token（逐 token 流式输出）</li>
 *   <li>PreActingEvent → type=tool_call（工具调用开始）</li>
 *   <li>PostActingEvent → type=tool_result（工具调用完成）</li>
 *   <li>ErrorEvent → type=error（错误发生）</li>
 * </ul>
 * </p>
 */
public class HarnessHooks implements Hook {

    private static final Logger log = LoggerFactory.getLogger(HarnessHooks.class);

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final long agentId;

    public HarnessHooks(SseEmitter emitter, ObjectMapper objectMapper, long agentId) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.agentId = agentId;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PreReasoningEvent) {
                sendSse(Map.of("type", SseEvent.TYPE_REASONING, "content", "思考中..."));

            } else if (event instanceof ReasoningChunkEvent e) {
                var chunk = e.getIncrementalChunk();
                if (chunk != null) {
                    String text = chunk.getTextContent();
                    if (text != null && !text.isEmpty()) {
                        sendSse(Map.of("type", SseEvent.TYPE_TOKEN, "content", text));
                    }
                }

            } else if (event instanceof PreActingEvent e) {
                String toolName = e.getToolUse() != null ? e.getToolUse().getName() : "unknown";
                sendSse(Map.of(
                        "type", SseEvent.TYPE_TOOL_CALL,
                        "tool", toolName,
                        "status", "running"
                ));

            } else if (event instanceof PostActingEvent e) {
                String toolName = e.getToolUse() != null ? e.getToolUse().getName() : "unknown";
                String summary = extractResultSummary(e);
                sendSse(Map.of(
                        "type", SseEvent.TYPE_TOOL_RESULT,
                        "tool", toolName,
                        "status", "done",
                        "summary", summary
                ));

            } else if (event instanceof ErrorEvent e) {
                String msg = e.getError() != null ? e.getError().getMessage() : "未知错误";
                log.warn("HarnessAgent error for agent {}: {}", agentId, msg);
                sendSse(Map.of("type", SseEvent.TYPE_ERROR, "message", msg));
            }
        } catch (Exception ex) {
            // SSE emitter 关闭是预期行为，不抛异常
            if (!(ex instanceof IOException)) {
                log.warn("Hook SSE send failed for agent {}: {}", agentId, ex.getMessage());
            }
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        // 优先级高于默认 Hook，确保事件被及时推送
        return 100;
    }

    private void sendSse(Map<String, Object> data) throws IOException {
        emitter.send(SseEmitter.event()
                .data(" " + objectMapper.writeValueAsString(data)));
    }

    private String extractResultSummary(PostActingEvent e) {
        if (e.getToolResult() == null || e.getToolResult().getOutput() == null) {
            return "";
        }
        return e.getToolResult().getOutput().stream()
                .filter(block -> block instanceof TextBlock)
                .map(block -> ((TextBlock) block).getText())
                .findFirst()
                .orElse("")
                .trim();
    }
}
