package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.model.SessionMessage;
import cafe.snails.ecomagents.service.AgentService;
import cafe.snails.ecomagents.service.AiModelService;
import cafe.snails.ecomagents.service.KnowledgeBaseService;
import cafe.snails.ecomagents.service.LlmService;
import cafe.snails.ecomagents.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.GenerateOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * AI 对话控制器，提供 SSE 流式对话接口。
 * <p>支持按 Agent 关联的知识库构建 RAG 上下文、按 Agent 分配的模型动态切换 LLM 参数，
 * </p>
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_HISTORY = 20;

    private final SessionService sessionService;
    private final AgentService agentService;
    private final LlmService llmService;
    private final AiModelService aiModelService;
    private final Executor llmTaskExecutor;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseService kbService;

    /**
     * SSE 流式对话入口（Agent 模式）。
     */
    @PostMapping(value = "/{agentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @PathVariable("agentId") Long agentId,
            @RequestBody Map<String, Object> body) {

        Long sessionId = body.get("sessionId") != null
                ? Long.valueOf(body.get("sessionId").toString()) : null;
        String content = (String) body.get("content");

        if (sessionId == null || content == null || content.isBlank()) {
            log.warn("streamChat rejected: missing sessionId or content (agentId={})", agentId);
            return errorEmitter("Missing sessionId or content");
        }

        log.info("streamChat request: agentId={}, sessionId={}, contentLen={}",
                agentId, sessionId, content.length());

        SseEmitter emitter = new SseEmitter(120_000L);

        llmTaskExecutor.execute(() -> {
            try {
                var agentResult = agentService.getAgent(agentId);
                Agent agent = agentResult.getCode() == 200 ? agentResult.getData() : null;

                sessionService.addMessage(sessionId, "user", content);

                List<Map<String, String>> history = buildHistory(sessionId);

                String systemPrompt = agent != null ? agent.getSystemPrompt() : null;
                if (agent != null && agent.getKnowledgeBaseIds() != null
                        && !agent.getKnowledgeBaseIds().isEmpty()) {
                    String kbContext = kbService.buildKnowledgeContext(
                            agent.getKnowledgeBaseIds(), content);
                    if (!kbContext.isEmpty()) {
                        systemPrompt = (systemPrompt != null ? systemPrompt : "")
                                + "\n\n你拥有以下知识库内容可供参考:\n" + kbContext;
                    }
                }

                if (agent == null) {
                    sendSseError(emitter, "Agent 不存在");
                    return;
                }
                if (agent.getModelId() == null) {
                    sendSseError(emitter, "Agent 未配置模型，请先选择一个模型");
                    return;
                }

                GenerateOptions modelOptions = aiModelService.buildModelOptions(agent.getModelId());
                if (modelOptions == null) {
                    sendSseError(emitter, "Agent 关联的模型未找到，请在模型管理中检查");
                    return;
                }
                if (modelOptions.getApiKey() == null || modelOptions.getApiKey().isBlank()) {
                    sendSseError(emitter, "模型未配置 API Key，请在模型管理中设置");
                    return;
                }

                String reply = llmService.streamChat(systemPrompt, history, emitter, modelOptions);

                if (reply != null && !reply.isBlank()) {
                    sessionService.addMessage(sessionId, "assistant", reply);
                    tryAutoGenerateTitle(sessionId, content, reply, modelOptions);
                    sendDoneEvent(emitter, reply);
                } else {
                    sendSseError(emitter, "模型响应超时或返回为空，请重试");
                }

            } catch (Exception e) {
                log.error("Chat stream error: agentId={}, sessionId={}, error={}",
                        agentId, sessionId, e.getMessage(), e);
                sendSseError(emitter, e.getMessage());
            }
        });

        return emitter;
    }

    /** 如果会话尚未有标题，则自动用 LLM 生成标题 */
    private void tryAutoGenerateTitle(Long sessionId, String userMessage, String aiReply,
                                      io.agentscope.core.model.GenerateOptions modelOptions) {
        try {
            var sessionResult = sessionService.getSession(sessionId);
            if (sessionResult.getCode() != 200 || sessionResult.getData() == null) return;
            Session session = sessionResult.getData();
            // Only generate title if it's still the default
            if (!"新对话".equals(session.getTitle())) return;

            String prompt = "根据以下对话内容，生成一个30字以内的简短标题：\n"
                    + "用户：" + truncate(userMessage, 100) + "\n"
                    + "AI：" + truncate(aiReply, 200);
            List<Map<String, String>> history = List.of(Map.of("role", "user", "content", prompt));

            String generated = llmService.syncChat("你是一个标题生成助手。只输出标题本身，不要多余内容。", history, modelOptions);
            if (generated != null && !generated.isBlank()) {
                String title = generated.replaceAll("[\"「」]", "").trim();
                if (title.length() > 30) title = title.substring(0, 30);
                sessionService.updateSession(sessionId, title, null);
                log.info("Auto-generated title for session {}: {}", sessionId, title);
            }
        } catch (Exception e) {
            log.warn("Failed to auto-generate title for session {}: {}", sessionId, e.getMessage());
        }
    }

    /** 截断字符串 */
    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    /** 从 Session 加载聊天历史 */
    private List<Map<String, String>> buildHistory(Long sessionId) {
        List<Map<String, String>> history = new ArrayList<>();
        var sessionResult = sessionService.getSessionWithMessages(sessionId);
        if (sessionResult.getCode() == 200) {
            Session session = sessionResult.getData();
            if (session != null) {
                List<SessionMessage> msgs = session.getMessages();
                if (msgs != null) {
                    int start = Math.max(0, msgs.size() - MAX_HISTORY);
                    for (int i = start; i < msgs.size(); i++) {
                        SessionMessage msg = msgs.get(i);
                        if (msg.getContent() != null && !msg.getContent().isBlank()) {
                            history.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                        }
                    }
                }
            }
        }
        return history;
    }

    /** 发送完成事件并结束流 */
    private void sendDoneEvent(SseEmitter emitter, String fullText) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "done");
            event.put("content", fullText);
            emitter.send(SseEmitter.event().data(" " + objectMapper.writeValueAsString(event)));
            emitter.complete();
        } catch (Exception e) {
            log.warn("sendDoneEvent failed: {}", e.getMessage());
        }
    }

    /** 返回错误 SseEmitter */
    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "error");
            event.put("message", message);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
            emitter.complete();
        } catch (Exception ignored) {}
        return emitter;
    }

    /** 向 SseEmitter 发送错误事件并结束 */
    private void sendSseError(SseEmitter emitter, String message) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "error");
            event.put("message", message);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (Exception ignored) {}
        emitter.complete();
    }
}
