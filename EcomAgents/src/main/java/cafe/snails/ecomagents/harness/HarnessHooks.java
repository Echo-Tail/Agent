package cafe.snails.ecomagents.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean completed;
    private final StringBuilder partialContent;

    public HarnessHooks(SseEmitter emitter, ObjectMapper objectMapper, long agentId,
                        AtomicBoolean completed, StringBuilder partialContent) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.agentId = agentId;
        this.completed = completed;
        this.partialContent = partialContent;
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
                        if (partialContent != null) partialContent.append(text);
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
                if (!completed.compareAndSet(false, true)) return Mono.just(event);
                String msg = e.getError() != null ? e.getError().getMessage() : "未知错误";
                log.warn("HarnessAgent error for agent {}: {}", agentId, msg);
                try {
                    sendSse(Map.of("type", SseEvent.TYPE_ERROR, "message", friendlyError(msg)));
                } catch (Exception ignored) {
                    // emitter may already be in error state
                }
                emitter.complete();
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

    /**
     * 将 SDK 原始错误消息映射为用户友好的中文消息。
     */
    public static String friendlyError(String msg) {
        if (msg == null) return "模型响应异常，请重试";
        if (msg.contains("EOF") || msg.contains("transport error") || msg.contains("SSE/NDJSON")) {
            return "模型响应异常，连接中断，请重试";
        }
        if (msg.contains("timeout") || msg.contains("Timeout")) {
            return "模型响应超时，请重试";
        }
        if (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("Authentication")) {
            return "模型 API 认证失败，请检查 API Key";
        }
        if (msg.contains("402") || msg.contains("Insufficient") || msg.contains("quota")) {
            return "模型 API 额度不足，请检查账户余额";
        }
        if (msg.contains("429") || msg.contains("Rate limit") || msg.contains("rate limit")) {
            return "模型请求频率过高，请稍后重试";
        }
        if (msg.contains("Retries exhausted")) {
            return "模型响应异常，请重试";
        }
        // Fallback: truncate long raw messages
        if (msg.length() > 80) {
            return "对话服务异常，请稍后重试";
        }
        return msg;
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
