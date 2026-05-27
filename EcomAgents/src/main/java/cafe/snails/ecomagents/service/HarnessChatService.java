package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.dto.ToolAvailability;
import cafe.snails.ecomagents.harness.HarnessHooks;
import cafe.snails.ecomagents.harness.SseEvent;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.model.SessionMessage;
import cafe.snails.ecomagents.model.TokenUsageRecord;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.SessionRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HarnessAgent 聊天服务，封装 HarnessAgent.call() + SSE 事件推送。
 * <p>
 * 支持两种 RAG 模式：
 * <ul>
 *   <li>GENERIC — 自动检索知识库内容注入到 user message 前</li>
 *   <li>AGENTIC — Agent 通过 retrieve_knowledge 工具自主检索</li>
 * </ul>
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
    private final AgentRepository agentRepository;
    private final AiModelRepository aiModelRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TokenUsageService tokenUsageService;
    private final TokenCounter tokenCounter;
    private final FileStorageService fileStorageService;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final LlmConfig llmConfig;
    private final AgentToolAvailabilityService toolAvailabilityService;

    /**
     * 同步聊天，返回 Agent 回复文本（无 SSE 流式，用于群聊 @Agent 回复）。
     */
    public String simpleChat(Long agentId, String content) {
        try {
            HarnessAgent agent = harnessAgentManager.createSimpleAgent(agentId);
            String actualContent = enrichWithKnowledge(agentId, content);
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(actualContent).build()))
                    .build();
            Msg reply = agent.call(userMsg, RuntimeContext.builder()
                            .sessionId("simple-" + System.currentTimeMillis())
                            .userId("0")
                            .build())
                    .block(Duration.ofSeconds(llmConfig.getStreamTimeout()));
            if (reply != null && reply.getTextContent() != null && !reply.getTextContent().isBlank()) {
                return reply.getTextContent();
            }
        } catch (Exception e) {
            log.error("simpleChat failed: agentId={}, error={}", agentId, e.getMessage());
        }
        return null;
    }

    /**
     * 启动 HarnessAgent 流式对话。
     */
    public SseEmitter streamChat(Long agentId, String sessionId, String content, Long userId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        AtomicBoolean completed = new AtomicBoolean(false);
        StringBuilder partialContent = new StringBuilder();

        llmTaskExecutor.execute(() -> {
            HarnessAgent agent = null;
            try {
                ToolAvailability webSearch = toolAvailabilityService.getWebSearchAvailability(agentId);
                if (requiresRealtimeLookup(content) && !webSearch.isAvailable()) {
                    String message = webSearch.getMessage();
                    sessionMapper.saveMessage(sessionId, "user", content);
                    sessionMapper.saveMessage(sessionId, "assistant", message);
                    sessionMapper.syncSessionMetadata(agentId, sessionId, userId, content, message);
                    recordTokenUsage(resolveModel(agentId), agentId, userId, content, null, false, message);
                    sendSseAndError(emitter, completed, message);
                    return;
                }

                agent = harnessAgentManager.createChatAgent(agentId, emitter, userId, completed, partialContent);

                sendSse(emitter, completed, Map.of("type", SseEvent.TYPE_REASONING, "content", "开始思考..."));

                RuntimeContext ctx = RuntimeContext.builder()
                        .sessionId(sessionId)
                        .userId(String.valueOf(userId))
                        .build();

                // Build user message, inject knowledge context for GENERIC RAG mode
                String actualContent = enrichWithKnowledge(agentId, content);
                // Inject conversation history for session continuity
                actualContent = enrichWithHistory(sessionId, actualContent);
                // Inject file-sending capability instruction
                actualContent = addFileCapabilityInstruction(actualContent);
                if (requiresRealtimeLookup(content) && webSearch.isAvailable()) {
                    actualContent = addRealtimeToolInstruction(actualContent);
                    sendSse(emitter, completed, Map.of(
                            "type", SseEvent.TYPE_REASONING,
                            "content", "检测到实时信息查询，将使用 web_search 工具。"
                    ));
                }

                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(List.of(TextBlock.builder().text(actualContent).build()))
                        .build();

                Msg reply = agent.call(userMsg, ctx).block(Duration.ofSeconds(llmConfig.getStreamTimeout()));

                AiModel model = resolveModel(agentId);

                if (reply != null && reply.getTextContent() != null && !reply.getTextContent().isBlank()) {
                    String fullText = reply.getTextContent();

                    // Parse <file name="...">...</file> markers for downloadable files
                    Pattern filePattern = Pattern.compile("<file\\s+name=\"([^\"]+)\">([\\s\\S]*?)</file>", Pattern.DOTALL);
                    Matcher matcher = filePattern.matcher(fullText);
                    List<Map<String, Object>> fileEvents = new ArrayList<>();
                    StringBuilder cleanedBuffer = new StringBuilder();
                    Long assistantFileId = null;
                    String assistantFileName = null;
                    int lastEnd = 0;

                    while (matcher.find()) {
                        // Append text before this match
                        cleanedBuffer.append(fullText, lastEnd, matcher.start());
                        lastEnd = matcher.end();

                        String fileName = matcher.group(1);
                        String fileContent = matcher.group(2);

                        FileRecord fileRecord = fileStorageService.saveContentAsFile(fileContent, fileName, userId);
                        if (fileRecord != null) {
                            fileEvents.add(Map.of(
                                    "type", SseEvent.TYPE_FILE,
                                    "id", fileRecord.getId(),
                                    "name", fileRecord.getOriginalName(),
                                    "url", fileRecord.getUrl(),
                                    "size", fileRecord.getFileSize()
                            ));
                            if (assistantFileId == null) {
                                assistantFileId = fileRecord.getId();
                                assistantFileName = fileRecord.getOriginalName();
                            }
                        }
                    }
                    // Append remaining text after last file marker
                    cleanedBuffer.append(fullText.substring(lastEnd));
                    String cleanText = cleanedBuffer.toString().trim();

                    recordTokenUsage(model, agentId, userId, actualContent, fullText, true, null);

                    sessionMapper.saveMessage(sessionId, "user", content);
                    sessionMapper.saveMessage(sessionId, "assistant", cleanText, assistantFileId, assistantFileName);
                    sessionMapper.syncSessionMetadata(agentId, sessionId, userId, content, cleanText);

                    // Send file events before done
                    for (Map<String, Object> fileEvent : fileEvents) {
                        sendSse(emitter, completed, fileEvent);
                    }

                    sendSse(emitter, completed, Map.of(
                            "type", SseEvent.TYPE_DONE,
                            "content", cleanText
                    ));
                    if (completed.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                } else {
                    recordTokenUsage(model, agentId, userId, actualContent, null, false, "模型响应为空");
                    sendSseAndError(emitter, completed, "模型响应为空，请重试");
                }

            } catch (Exception e) {
                log.error("HarnessChat failed: agentId={}, sessionId={}, error={}",
                        agentId, sessionId, e.getMessage(), e);

                String partial = partialContent.toString();
                if (!partial.isEmpty()) {
                    sessionMapper.saveMessage(sessionId, "assistant", partial);
                }

                if (!completed.get()) {
                    String friendly = HarnessHooks.friendlyError(e.getMessage());
                    AiModel model = resolveModel(agentId);
                    recordTokenUsage(model, agentId, userId, content, partial, false, buildErrorDiagnostic(e, friendly));
                    sendSseAndError(emitter, completed, friendly);
                }
            }
        });

        return emitter;
    }

    /**
     * 为消息注入历史对话上下文，使 Agent 能感知之前的对话内容。
     * 加载当前 session 的已有消息列表，格式化为对话历史追加在用户消息之前。
     */
    private String enrichWithHistory(String harnessSessionId, String content) {
        try {
            Session session = sessionRepository.findByHarnessSessionIdWithMessages(harnessSessionId).orElse(null);
            if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
                return content;
            }

            // Build conversation history, excluding the current user message
            StringBuilder history = new StringBuilder("\n\n[历史对话]\n");
            for (SessionMessage msg : session.getMessages()) {
                String role = "user".equals(msg.getRole()) ? "用户" : "助手";
                history.append(role).append("：").append(msg.getContent()).append("\n");
            }
            history.append("\n[以上是历史对话内容，请基于这些上下文回答用户最新的问题]\n");

            log.debug("Injected {} history messages for session {}", session.getMessages().size(), harnessSessionId);
            return history.toString() + content;
        } catch (Exception e) {
            log.warn("Failed to load session history: {}", e.getMessage());
            return content;
        }
    }

    /**
     * 根据 Agent 的 RAG 模式，为消息注入知识库上下文。
     * GENERIC 模式：自动检索注入。AGENTIC 模式：不注入（Agent 通过工具自主检索）。
     */
    private String enrichWithKnowledge(Long agentId, String content) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) return content;

        // Only inject for GENERIC mode
        if (!"GENERIC".equals(agent.getRagMode())) return content;

        List<Long> kbIds = agent.getKnowledgeBaseIds();
        if (kbIds == null || kbIds.isEmpty()) return content;

        String knowledgeContext = knowledgeBaseService.buildKnowledgeContext(kbIds, content);
        if (knowledgeContext.isBlank()) return content;

        log.debug("Injected knowledge context for agent {} (GENERIC mode, {} KBs)", agentId, kbIds.size());
        return content + "\n\n" + knowledgeContext;
    }

    private boolean requiresRealtimeLookup(String content) {
        if (content == null || content.isBlank()) return false;
        String text = content.toLowerCase();
        return text.contains("天气")
                || text.contains("气温")
                || text.contains("下雨")
                || text.contains("新闻")
                || text.contains("最新")
                || text.contains("实时")
                || text.contains("股价")
                || text.contains("汇率")
                || text.contains("weather")
                || text.contains("temperature")
                || text.contains("news")
                || text.contains("latest")
                || text.contains("price");
    }

    private String addRealtimeToolInstruction(String content) {
        return content + "\n\n[系统工具指令]\n"
                + "这个用户问题需要实时或外部最新信息。必须调用 web_search 工具获取结果后再回答。"
                + "不要调用 execute、shell、本地命令或本地文件工具来查询互联网、天气、新闻、价格、汇率等实时信息。"
                + "如果 web_search 返回失败，请直接说明网页搜索失败和失败原因。";
    }

    /**
     * 为消息注入文件发送能力说明，指导 Agent 使用 &lt;file name=&quot;...&quot;&gt; 标记提供可下载文件。
     */
    private String addFileCapabilityInstruction(String content) {
        return content + "\n\n[系统指令]\n"
                + "当用户要求你生成可下载的文件（如 .md、.txt、.json、.csv、.xml、.html 等）时，"
                + "请将文件内容包裹在 <file name=\"filename.ext\"> 和 </file> 标签中。"
                + "例如：<file name=\"report.md\"># 标题\n\n内容</file>。"
                + "文件内容会被自动保存为一个独立的可下载文件，用户在对话中可以看到下载链接。"
                + "回复中的非文件内容正常输出即可，仅将需要下载的部分用 <file> 标签包裹。"
                + "注意：不要在回复中创建指向 workspace 路径或本地文件路径的 markdown 链接，"
                + "文件路径对用户不可访问。只描述文件名即可。"
                + "\n\n【知识库使用规范】\n"
                + "本 Agent 绑定了知识库，知识库内容通过 retrieve_knowledge 工具检索获取。"
                + "当用户询问知识库中的信息时：\n"
                + "1. 优先使用 retrieve_knowledge 工具检索相关信息\n"
                + "2. 如果检索结果不完整，尝试使用不同的关键词再次检索\n"
                + "3. 禁止使用文件读写工具（如 list_files、read_file、execute 等）直接访问工作区文件\n"
                + "4. 知识库文件存储在工作区目录中，但该路径对用户和 Agent 都不可直接访问\n"
                + "5. 如果多次检索仍无法获取完整信息，请如实告知用户当前检索到的内容";
    }

    // ===== SSE helpers =====

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

    // ===== Token usage =====

    private AiModel resolveModel(Long agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null || agent.getModelId() == null) return null;
        return aiModelRepository.findById(agent.getModelId()).orElse(null);
    }

    private void recordTokenUsage(AiModel model, Long agentId, Long userId,
                                  String inputText, String outputText, boolean success, String errorMessage) {
        try {
            String modelName = model != null ? model.getName() : "unknown";
            String modelType = model != null && model.getModelType() != null ? model.getModelType() : "TEXT";
            String apiModelName = model != null ? model.getModelName() : "unknown";

            int promptTokens = tokenCounter.count(apiModelName, inputText);
            int completionTokens = outputText != null ? tokenCounter.count(apiModelName, outputText) : 0;

            String resolvedAgentName = resolveAgentName(agentId);
            String resolvedUsername = resolveUsername(userId);

            TokenUsageRecord record = TokenUsageRecord.builder()
                    .modelId(model != null ? model.getId() : null)
                    .modelName(modelName)
                    .modelType(modelType)
                    .userId(userId)
                    .agentId(agentId)
                    .agentName(resolvedAgentName)
                    .username(resolvedUsername)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .success(success)
                    .errorMessage(success ? null : truncate(errorMessage, 500))
                    .build();

            tokenUsageService.record(record);
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }

    private String resolveAgentName(Long agentId) {
        if (agentId == null) return null;
        return agentRepository.findById(agentId)
                .map(Agent::getName)
                .orElse(null);
    }

    private String resolveUsername(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse(null);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private String buildErrorDiagnostic(Exception e, String friendly) {
        String raw = e.getMessage();
        String type = e.getClass().getSimpleName();
        return "stage=" + classifyTimeoutStage(e) + "; friendly=" + friendly + "; type=" + type + "; raw=" + (raw != null ? raw : "");
    }

    private String classifyTimeoutStage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "unknown";
        }
        if (message.contains("Knowledge retrieval") || message.contains("retrieve_knowledge")) {
            return "rag_timeout";
        }
        if (message.contains("tool") || message.contains("Tool")) {
            return "tool_timeout";
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return "model_timeout";
        }
        return "unknown";
    }
}
