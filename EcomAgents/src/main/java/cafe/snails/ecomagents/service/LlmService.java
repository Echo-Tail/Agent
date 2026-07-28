package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.ModelRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM 交互服务，封装 AgentScope SDK 的流式对话调用。
 * <p>核心能力：
 * <ul>
 *   <li>流式对话（SSE 逐 token 推送）— {@link #streamChat}</li>
 *   <li>同步对话（非流式）— {@link #syncChat}</li>
 *   <li>按请求覆盖模型参数（per-request Model）</li>
 *   <li>超时控制、客户端断连检测、API Key 脱敏日志</li>
 * </ul>
 * </p>
 */
@Service
public class LlmService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    /** 默认最大输出 token 数，避免模型返回过长文本。 */
    public static final int DEFAULT_MAX_TOKENS = 256000;

    private final AiModelService aiModelService;
    private final ModelRuntimeProperties runtimeProperties;
    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 创建 LLM 服务并注入默认模型、配置和 JSON 序列化器。
     */
    public LlmService(AiModelService aiModelService, ModelRuntimeProperties runtimeProperties,
                      ObjectMapper objectMapper) {
        this.aiModelService = aiModelService;
        this.runtimeProperties = runtimeProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式对话，将 token 逐块推送到 SseEmitter。
     * <p>使用 AgentScope SDK 的 {@link Model#stream} 方法发起流式请求，
     * 通过 SSE 逐 token 推送，支持超时控制和客户端断连检测。</p>
     *
     * @param systemPrompt 系统提示词
     * @param history      历史消息列表（每项含 role + content）
     * @param emitter      SSE 发射器（接收 token 事件推送）
     * @return 完整回复文本
     */
    public String streamChat(String systemPrompt, List<Map<String, String>> history, SseEmitter emitter) {
        return streamChat(systemPrompt, history, emitter, null);
    }

    /**
     * 流式对话，支持按请求覆盖 GenerateOptions。
     * <p>当 optionsOverride 提供了 API Key 和 Base URL 时，会创建独立的
     * {@link OpenAIChatModel} 实例（per-request Model），用于按 Agent 使用不同模型配置。
     * 否则使用全局注入的 Model。</p>
     *
     * @param systemPrompt    系统提示词
     * @param history         历史消息列表
     * @param emitter         SSE 发射器
     * @param optionsOverride 可选的模型参数覆盖（API Key / Model Name / Base URL 等）
     * @return 完整回复文本
     * @throws IllegalStateException API Key 未配置时抛出
     * @throws RuntimeException      LLM 流式调用失败时抛出
     */
    public String streamChat(String systemPrompt, List<Map<String, String>> history,
                             SseEmitter emitter, GenerateOptions optionsOverride) {
        if (optionsOverride == null) {
            optionsOverride = aiModelService.buildModelOptions(null);
        }
        if (optionsOverride == null) {
            throw new IllegalStateException("No enabled default AI model is configured");
        }
        boolean hasPerModelKey = optionsOverride.getApiKey() != null
                && !optionsOverride.getApiKey().isBlank();
        if (!hasPerModelKey) {
            throw new IllegalStateException("The selected AI model has no credential configured");
        }

        List<Msg> messages = buildMessages(systemPrompt, history);
        long streamTimeout = runtimeProperties.getStreamTimeout();

        String modelName = optionsOverride.getModelName();
        String apiKeyMask = maskApiKey(optionsOverride.getApiKey());

        Model effectiveModel = createModel(optionsOverride);

        log.info("LLM streamChat starting: modelName={}, apiKey={}, systemPromptLen={}, historyMsgs={}",
                modelName, apiKeyMask,
                systemPrompt != null ? systemPrompt.length() : 0,
                messages.size());

        StringBuilder fullText = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean emitterDead = new AtomicBoolean(false);

        var disposable = effectiveModel.stream(messages, List.of(), optionsOverride)
                .subscribe(
                        response -> emitToken(response, fullText, emitter, emitterDead),
                        error -> {
                            log.error("LLM streaming error: modelName={}, error={}",
                                    modelName, error.getMessage(), error);
                            errorRef.set(error);
                            latch.countDown();
                        },
                        () -> {
                            log.debug("LLM stream completed normally: modelName={}, totalTokens={}",
                                    modelName, fullText.length());
                            latch.countDown();
                        }
                );

        emitter.onCompletion(() -> {
            if (!emitterDead.getAndSet(true)) {
                disposable.dispose();
                latch.countDown();
            }
        });
        emitter.onTimeout(() -> {
            log.warn("SseEmitter timed out: modelName={}, receivedTokens={}",
                    modelName, fullText.length());
            if (!emitterDead.getAndSet(true)) {
                disposable.dispose();
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(streamTimeout, TimeUnit.SECONDS);
            if (!completed) {
                disposable.dispose();
                log.warn("LLM stream timed out after {}s: modelName={}, receivedTokens={}",
                        streamTimeout, modelName, fullText.length());
            }
        } catch (InterruptedException e) {
            log.warn("LLM stream latch interrupted: modelName={}, receivedTokens={}",
                    modelName, fullText.length());
            disposable.dispose();
            Thread.currentThread().interrupt();
        }

        if (errorRef.get() != null) {
            log.warn("LLM streaming failed, throwing: modelName={}, receivedTokens={}",
                    modelName, fullText.length());
            throw new RuntimeException("LLM streaming failed", errorRef.get());
        }

        log.info("LLM streamChat done: modelName={}, totalTokens={}", modelName, fullText.length());
        return fullText.toString();
    }

    /**
     * 同步（非流式）对话，返回完整文本。
     * <p>适用于标题生成、简单问答等不需要 SSE 流式推送的场景。
     * 默认 maxTokens=256，超时 30s。</p>
     *
     * @param systemPrompt 系统提示词
     * @param history      历史消息列表
     * @return 回复文本；模型返回空时返回 null
     */
    public String syncChat(String systemPrompt, List<Map<String, String>> history) {
        return syncChat(systemPrompt, history, null);
    }

    /**
     * 同步（非流式）对话，支持按请求覆盖模型参数。
     * <p>当 optionsOverride 提供 API Key 和 Base URL 时创建 per-request Model。</p>
     *
     * @param systemPrompt    系统提示词
     * @param history         历史消息列表
     * @param optionsOverride 可选的模型参数覆盖
     * @return 回复文本；模型返回空时返回 null
     */
    public String syncChat(String systemPrompt, List<Map<String, String>> history,
                            GenerateOptions optionsOverride) {
        if (optionsOverride == null) {
            optionsOverride = aiModelService.buildModelOptions(null);
        }
        if (optionsOverride == null) {
            throw new IllegalStateException("No enabled default AI model is configured");
        }
        List<Msg> messages = buildMessages(systemPrompt, history);

        Model effectiveModel = createModel(optionsOverride);

        StringBuilder fullText = new StringBuilder();
        effectiveModel.stream(messages, List.of(), optionsOverride)
                .doOnNext(response -> {
                    String text = extractText(response);
                    if (text != null) fullText.append(text);
                })
                .blockLast();

        String result = fullText.toString();
        return result.isEmpty() ? null : result;
    }

    private Model createModel(GenerateOptions options) {
        if (options.getApiKey() == null || options.getApiKey().isBlank()) {
            throw new IllegalStateException("The selected AI model has no credential configured");
        }
        if (options.getModelName() == null || options.getModelName().isBlank()) {
            throw new IllegalStateException("The selected AI model has no model name configured");
        }
        if (options.getBaseUrl() == null || options.getBaseUrl().isBlank()) {
            throw new IllegalStateException("The selected AI model has no API URL configured");
        }
        return OpenAIChatModel.builder()
                .apiKey(options.getApiKey())
                .modelName(options.getModelName())
                .baseUrl(options.getBaseUrl())
                .endpointPath(options.getEndpointPath() != null
                        ? options.getEndpointPath() : "/v1/chat/completions")
                .build();
    }

    /**
     * 脱敏 API Key（仅显示前 8 位），用于安全日志记录。
     *
     * @param key 原始 API Key
     * @return 脱敏后的 Key（如 "sk-abc123****"）
     */
    private static String maskApiKey(String key) {
        if (key == null) return "null";
        if (key.length() <= 8) return key.substring(0, Math.min(key.length(), 4)) + "****";
        return key.substring(0, 8) + "****";
    }

    /**
     * 构建 AgentScope 消息列表。
     * <p>按顺序：system prompt（如有）→ 历史消息（user/assistant 交替）。空内容的消息会被跳过。</p>
     *
     * @param systemPrompt 系统提示词（可为 null）
     * @param history      历史消息列表（每项含 role + content）
     * @return AgentScope Msg 列表
     */
    private List<Msg> buildMessages(String systemPrompt, List<Map<String, String>> history) {
        List<Msg> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent(systemPrompt)
                    .build());
        }
        for (Map<String, String> msg : history) {
            MsgRole role = "user".equals(msg.get("role")) ? MsgRole.USER : MsgRole.ASSISTANT;
            String content = msg.get("content");
            if (content != null && !content.isBlank()) {
                messages.add(Msg.builder()
                        .role(role)
                        .textContent(content)
                        .build());
            }
        }
        return messages;
    }

    /**
     * 将 AgentScope {@link ChatResponse} 转为 SSE token 事件并推送到 emitter。
     * <p>提取文本内容后追加到 fullText 缓冲区，同时通过 SSE 推送 {@code type=token} 事件。</p>
     *
     * @param response     AgentScope 响应块
     * @param fullText     完整文本缓冲区
     * @param emitter      SSE 发射器
     * @param emitterDead  客户端断连标志（CAS 控制）
     */
    private void emitToken(ChatResponse response, StringBuilder fullText,
                           SseEmitter emitter, AtomicBoolean emitterDead) {
        if (emitterDead.get()) return;

        String text = extractText(response);
        if (text == null || text.isEmpty()) return;

        fullText.append(text);

        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "token");
            event.put("content", text);
            emitter.send(SseEmitter.event()
                    .data(" " + objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.warn("Client disconnected during streaming: {}", e.getMessage());
            emitterDead.set(true);
        }
    }

    /**
     * 从 AgentScope {@link ChatResponse} 中提取文本内容。
     * <p>仅提取第一个 ContentBlock，且要求为 TextBlock 类型。</p>
     *
     * @param response AgentScope 响应
     * @return 文本内容；无文本时返回 null
     */
    private String extractText(ChatResponse response) {
        List<ContentBlock> blocks = response.getContent();
        if (blocks == null || blocks.isEmpty()) return null;
        ContentBlock first = blocks.get(0);
        if (first instanceof TextBlock tb) {
            return tb.getText();
        }
        return null;
    }

}
