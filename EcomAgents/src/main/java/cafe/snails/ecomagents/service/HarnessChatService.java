package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.harness.SseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.core.agent.RuntimeContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HarnessAgent 聊天服务，封装 HarnessAgent.call() + SSE 事件推送。
 * <p>
 * 每次 chat 请求创建 per-request 的 HarnessAgent 实例（含 per-request Hook），
 * 在异步线程中执行 ReAct 循环，通过 SSE 推送步骤事件和最终结果。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HarnessChatService {

    private static final Logger log = LoggerFactory.getLogger(HarnessChatService.class);

    private final HarnessAgentManager harnessAgentManager;
    private final ObjectMapper objectMapper;
    private final Executor llmTaskExecutor;
    private final SessionMapper sessionMapper;

    /**
     * 启动 HarnessAgent 流式对话。
     *
     * @param agentId   Agent ID
     * @param sessionId 会话 ID（UUID 格式）
     * @param content   用户消息
     * @param userId    用户 ID
     * @return SseEmitter 立即返回，异步执行 ReAct 循环
     */
    public SseEmitter streamChat(Long agentId, String sessionId, String content, Long userId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        AtomicBoolean completed = new AtomicBoolean(false);

        llmTaskExecutor.execute(() -> {
            HarnessAgent agent = null;
            try {
                agent = harnessAgentManager.createChatAgent(agentId, emitter, userId);

                // 发送 reasoning 开始事件
                sendSse(emitter, completed, Map.of("type", SseEvent.TYPE_REASONING, "content", "开始思考..."));

                // 构造 RuntimeContext
                RuntimeContext ctx = RuntimeContext.builder()
                        .sessionId(sessionId)
                        .userId(String.valueOf(userId))
                        .build();

                // 构造用户消息
                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(List.of(TextBlock.builder().text(content).build()))
                        .build();

                // 执行 ReAct 循环（阻塞，但在异步线程中）
                Msg reply = agent.call(userMsg, ctx).block();

                if (reply != null && reply.getTextContent() != null && !reply.getTextContent().isBlank()) {
                    String fullText = reply.getTextContent();

                    // 保存消息到 DB
                    sessionMapper.saveMessage(sessionId, "user", content);
                    sessionMapper.saveMessage(sessionId, "assistant", fullText);

                    // 同步会话元数据到 DB
                    sessionMapper.syncSessionMetadata(agentId, sessionId, userId, content, fullText);

                    // 发送 done 事件
                    sendSse(emitter, completed, Map.of(
                            "type", SseEvent.TYPE_DONE,
                            "content", fullText
                    ));
                    if (completed.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                } else {
                    sendSseAndError(emitter, completed, "模型响应为空，请重试");
                }

            } catch (Exception e) {
                log.error("HarnessChat failed: agentId={}, sessionId={}, error={}",
                        agentId, sessionId, e.getMessage(), e);
                sendSseAndError(emitter, completed, e.getMessage());
            }
        });

        return emitter;
    }

    private void sendSse(SseEmitter emitter, AtomicBoolean completed, Map<String, Object> data) {
        if (completed.get()) return;
        try {
            emitter.send(SseEmitter.event().data(" " + objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.warn("SSE send failed: {}", e.getMessage());
        }
    }

    private void sendSseAndError(SseEmitter emitter, AtomicBoolean completed, String message) {
        if (completed.get()) return;
        try {
            sendSse(emitter, completed, Map.of("type", SseEvent.TYPE_ERROR, "message", message));
        } finally {
            if (completed.compareAndSet(false, true)) {
                emitter.complete();
            }
        }
    }
}
