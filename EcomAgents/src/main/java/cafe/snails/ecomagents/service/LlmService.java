package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
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
 * <p>支持 SSE 实时推送 token、超时控制、客户端断连检测、以及按请求覆盖模型参数。</p>
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    /** AgentScope Model 实例，由 AgentScopeConfig 注入 */
    private final Model model;
    /** LLM 全局配置（API key、默认模型等） */
    private final LlmConfig llmConfig;
    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    public LlmService(Model model, LlmConfig llmConfig, ObjectMapper objectMapper) {
        this.model = model;
        this.llmConfig = llmConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式对话，将 token 逐块推送到 SseEmitter，返回完整文本。
     */
    public String streamChat(String systemPrompt, List<Map<String, String>> history, SseEmitter emitter) {
        return streamChat(systemPrompt, history, emitter, null);
    }

    /**
     * 流式对话，支持按请求覆盖 GenerateOptions（用于按 Agent 使用不同模型）。
     */
    public String streamChat(String systemPrompt, List<Map<String, String>> history,
                             SseEmitter emitter, GenerateOptions optionsOverride) {
        // Resolve effective API key: per-model config takes priority over global
        boolean hasPerModelKey = optionsOverride != null
                && optionsOverride.getApiKey() != null
                && !optionsOverride.getApiKey().isBlank();
        if (!hasPerModelKey && "sk-placeholder".equals(llmConfig.getApiKey())) {
            log.warn("LLM API key not configured (placeholder detected), throwing");
            throw new IllegalStateException(
                    "LLM API key not configured. Set llm.api.key or LLM_API_KEY environment variable.");
        }

        List<Msg> messages = buildMessages(systemPrompt, history);
        long streamTimeout = llmConfig.getStreamTimeout();
        GenerateOptions options = optionsOverride != null ? optionsOverride : GenerateOptions.builder()
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .executionConfig(io.agentscope.core.model.ExecutionConfig.builder()
                        .timeout(java.time.Duration.ofSeconds(streamTimeout))
                        .maxAttempts(1)
                        .build())
                .build();

        String modelName = options.getModelName() != null ? options.getModelName() : llmConfig.getModel();
        String apiKeyMask = (hasPerModelKey || llmConfig.getApiKey() != null)
                ? maskApiKey(hasPerModelKey ? optionsOverride.getApiKey() : llmConfig.getApiKey())
                : "none";

        // Create per-request Model when options contain per-model config
        Model effectiveModel = model;
        if (hasPerModelKey && optionsOverride.getBaseUrl() != null) {
            String modelNameOverride = optionsOverride.getModelName() != null
                    ? optionsOverride.getModelName() : llmConfig.getModel();
            log.info("Creating per-request OpenAIChatModel: modelName={}, baseUrl={}, apiKey={}",
                    modelNameOverride, optionsOverride.getBaseUrl(), apiKeyMask);
            effectiveModel = OpenAIChatModel.builder()
                    .apiKey(optionsOverride.getApiKey())
                    .modelName(modelNameOverride)
                    .baseUrl(optionsOverride.getBaseUrl())
                    .endpointPath(optionsOverride.getEndpointPath() != null
                            ? optionsOverride.getEndpointPath() : "/v1/chat/completions")
                    .build();
        }

        log.info("LLM streamChat starting: modelName={}, apiKey={}, systemPromptLen={}, historyMsgs={}",
                modelName, apiKeyMask,
                systemPrompt != null ? systemPrompt.length() : 0,
                messages.size());

        StringBuilder fullText = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean emitterDead = new AtomicBoolean(false);

        var disposable = effectiveModel.stream(messages, List.of(), options)
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

        // 注册客户端断连和超时回调，及时释放订阅资源
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
     * 同步非流式对话，返回完整文本。用于标题生成等不需要流式的场景。
     */
    public String syncChat(String systemPrompt, List<Map<String, String>> history) {
        return syncChat(systemPrompt, history, null);
    }

    /**
     * 同步非流式对话，支持按请求覆盖模型参数。
     */
    public String syncChat(String systemPrompt, List<Map<String, String>> history,
                            GenerateOptions optionsOverride) {
        List<Msg> messages = buildMessages(systemPrompt, history);
        GenerateOptions options = optionsOverride != null ? optionsOverride : GenerateOptions.builder()
                .temperature(llmConfig.getTemperature())
                .maxTokens(256)
                .executionConfig(io.agentscope.core.model.ExecutionConfig.builder()
                        .timeout(java.time.Duration.ofSeconds(30))
                        .maxAttempts(1)
                        .build())
                .build();

        Model effectiveModel = model;
        if (optionsOverride != null && optionsOverride.getApiKey() != null && optionsOverride.getBaseUrl() != null) {
            effectiveModel = OpenAIChatModel.builder()
                    .apiKey(optionsOverride.getApiKey())
                    .modelName(optionsOverride.getModelName() != null ? optionsOverride.getModelName() : llmConfig.getModel())
                    .baseUrl(optionsOverride.getBaseUrl())
                    .endpointPath(optionsOverride.getEndpointPath() != null
                            ? optionsOverride.getEndpointPath() : "/v1/chat/completions")
                    .build();
        }

        StringBuilder fullText = new StringBuilder();
        effectiveModel.stream(messages, List.of(), options)
                .doOnNext(response -> {
                    String text = extractText(response);
                    if (text != null) fullText.append(text);
                })
                .blockLast();

        String result = fullText.toString();
        return result.isEmpty() ? null : result;
    }

    /** 脱敏 API Key（只显示前8位） */
    private static String maskApiKey(String key) {
        if (key == null) return "null";
        if (key.length() <= 8) return key.substring(0, Math.min(key.length(), 4)) + "****";
        return key.substring(0, 8) + "****";
    }

    /** 构建 AgentScope 消息列表（system prompt + 历史消息） */
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

    /** 将 AgentScope ChatResponse 转为 SSE token 事件推送 */
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

    /** 从 ChatResponse 中提取文本内容 */
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
