package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.harness.HarnessHooks;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import cafe.snails.ecomagents.tool.RetrieveKnowledgeTool;
import cafe.snails.ecomagents.tool.WebSearchTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HarnessAgent 工厂，为每次 chat 请求创建带 per-request Hook 的 HarnessAgent 实例。
 * <p>核心职责：
 * <ul>
 *   <li>从数据库加载 Agent 和 AiModel 配置</li>
 *   <li>构建 {@link OpenAIChatModel}（API Key / Model Name / Base URL 按 Agent 或全局配置解析）</li>
 *   <li>注册 per-Agent 工具（web_search、retrieve_knowledge 等）</li>
 *   <li>创建流式（Hook + SSE）或非流式（简单回复）HarnessAgent</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HarnessAgentManager {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(HarnessAgentManager.class);

    private final AgentRepository agentRepository;
    private final AiModelRepository aiModelRepository;
    private final ToolConfigRepository toolConfigRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final WorkspaceConfig workspaceConfig;
    private final ModelCredentialService credentialService;
    private final ObjectMapper objectMapper;

    /**
     * 创建简单的 HarnessAgent（无 SSE 流式 Hook）。
     * <p>用于群聊中 @Agent 触发自动回复的场景，maxIters=4 以快速响应。</p>
     *
     * @param agentId Agent ID
     * @return HarnessAgent 实例
     * @throws IllegalArgumentException Agent 不存在或绑定的模型不存在/被禁用时抛出
     */
    public HarnessAgent createSimpleAgent(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        validateAgentModel(agent);

        var chatModel = OpenAIChatModel.builder()
                .apiKey(resolveApiKey(agent))
                .modelName(resolveModelName(agent))
                .baseUrl(resolveBaseUrl(agent))
                .endpointPath(resolveEndpointPath(agent))
                .stream(false)
                .build();

        Toolkit toolkit = new Toolkit();
        registerAgentTools(toolkit, agent);

        java.nio.file.Path workspacePath = java.nio.file.Path.of(
                workspaceConfig.getRoot(), "agent-" + agentId);

        HarnessAgent harnessAgent = HarnessAgent.builder()
                .name(agent.getName() != null ? agent.getName() : "agent-" + agentId)
                .model(chatModel)
                .workspace(workspacePath)
                .toolkit(toolkit)
                .maxIters(4)
                .build();

        log.debug("Simple HarnessAgent created for agent {}", agentId);
        return harnessAgent;
    }

    /**
     * 创建带 SSE Hook 的 HarnessAgent（流式对话）。
     * <p>用于前端聊天界面的流式交互，通过 {@link HarnessHooks} 将 Agent 推理过程
     * （推理开始、逐 token 输出、工具调用、错误）实时推送到 SSE。</p>
     *
     * @param agentId         Agent ID
     * @param emitter         SSE 发射器（接收 Hook 事件推送）
     * @param userId          当前用户 ID（用于日志记录）
     * @param completed       完成标志（CAS 控制，防止重复 complete）
     * @param partialContent  部分内容缓冲区（用于在异常时保存已生成的文本）
     * @return HarnessAgent 实例
     * @throws IllegalArgumentException Agent 不存在或绑定的模型不存在/被禁用时抛出
     */
    public HarnessAgent createChatAgent(Long agentId, SseEmitter emitter, Long userId,
                                        AtomicBoolean completed, StringBuilder partialContent) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        validateAgentModel(agent);

        var chatModel = OpenAIChatModel.builder()
                .apiKey(resolveApiKey(agent))
                .modelName(resolveModelName(agent))
                .baseUrl(resolveBaseUrl(agent))
                .endpointPath(resolveEndpointPath(agent))
                .stream(true)
                .build();

        Toolkit toolkit = new Toolkit();
        registerAgentTools(toolkit, agent);

        java.nio.file.Path workspacePath = java.nio.file.Path.of(
                workspaceConfig.getRoot(), "agent-" + agentId);

        HarnessHooks hooks = new HarnessHooks(emitter, objectMapper, agentId, completed, partialContent);

        HarnessAgent harnessAgent = HarnessAgent.builder()
                .name(agent.getName() != null ? agent.getName() : "agent-" + agentId)
                .model(chatModel)
                .workspace(workspacePath)
                .toolkit(toolkit)
                .hooks(List.of(hooks))
                .maxIters(6)
                .build();

        log.debug("HarnessAgent created for agent {} (user {})", agentId, userId);
        return harnessAgent;
    }

    /**
     * 校验 Agent 绑定的模型是否存在且已启用。
     *
     * @param agent Agent 实体
     * @throws IllegalArgumentException 模型不存在或被禁用时抛出
     */
    private void validateAgentModel(Agent agent) {
        AiModel model = findModel(agent);
        if (model == null) {
            throw new IllegalArgumentException(agent.getModelId() == null
                    ? "未配置已启用的默认模型"
                    : "Agent 绑定的模型不存在");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new IllegalArgumentException("Agent 绑定的模型已被禁用");
        }
    }

    // ===== Tool registration =====

    /**
     * 为指定 Agent 注册工具。
     * <p>工具注册规则：
     * <ul>
     *   <li>系统 Agent：注册所有已启用的全局工具</li>
     *   <li>普通 Agent：仅注册 Agent 绑定的工具（per-agent 过滤），跳过禁用和未知工具</li>
     * </ul>
     * 当 Agent 的 RAG 模式为 AGENTIC 且有知识库时，自动注册 {@link RetrieveKnowledgeTool}。</p>
     *
     * @param toolkit AgentScope Toolkit
     * @param agent   Agent 实体
     */
    private void registerAgentTools(Toolkit toolkit, Agent agent) {
        Set<String> agentToolIds = agent.getTools() != null
                ? new HashSet<>(agent.getTools())
                : Set.of();

        List<ToolConfig> agentToolConfigs;
        if (Boolean.TRUE.equals(agent.getIsSystem())) {
            agentToolConfigs = toolConfigRepository.findAll().stream()
                    .filter(tool -> Boolean.TRUE.equals(tool.getEnabled()))
                    .toList();
            log.debug("System agent {} will register all enabled tools: {}", agent.getId(),
                    agentToolConfigs.stream().map(ToolConfig::getId).toList());
        } else {
            if (agentToolIds.isEmpty()) return;
            agentToolConfigs = toolConfigRepository.findAllById(agentToolIds);
        }
        if (agentToolConfigs == null || agentToolConfigs.isEmpty()) return;

        if (!Boolean.TRUE.equals(agent.getIsSystem())) {
            Set<String> configuredToolIds = new HashSet<>();
            for (ToolConfig tool : agentToolConfigs) {
                configuredToolIds.add(tool.getId());
                if (!Boolean.TRUE.equals(tool.getEnabled())) {
                    log.warn("Agent {} skipped disabled tool at runtime: toolId={}", agent.getId(), tool.getId());
                }
            }
            for (String toolId : agentToolIds) {
                if (!configuredToolIds.contains(toolId)) {
                    log.warn("Agent {} skipped unknown tool at runtime: toolId={}", agent.getId(), toolId);
                }
            }
        }

        for (ToolConfig tool : agentToolConfigs) {
            if (!Boolean.TRUE.equals(tool.getEnabled())) continue;

            switch (tool.getId()) {
                case "web_search" -> registerWebSearch(toolkit, tool);
                default -> log.debug("Tool '{}' not yet implemented, skipping", tool.getId());
            }
        }

        // Register retrieve_knowledge tool if agent has knowledge bases and RAG mode is AGENTIC
        if ("AGENTIC".equals(agent.getRagMode())
                && agent.getKnowledgeBaseIds() != null
                && !agent.getKnowledgeBaseIds().isEmpty()) {
            toolkit.registerTool(new RetrieveKnowledgeTool(knowledgeBaseService, agent.getKnowledgeBaseIds()));
            log.info("RetrieveKnowledgeTool registered for agent {}", agent.getId());
        }
    }

    /**
     * 注册 web_search 工具（Tavily API）。
     * <p>从 ToolConfig 的 configJson 中提取 apiKey，若未配置则跳过注册并记录警告。</p>
     *
     * @param toolkit AgentScope Toolkit
     * @param config  ToolConfig 实体
     */
    private void registerWebSearch(Toolkit toolkit, ToolConfig config) {
        String apiKey = extractApiKey(config.getConfigJson());
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("web_search tool is enabled but no API key configured");
            return;
        }
        toolkit.registerTool(new WebSearchTool(apiKey));
        log.info("WebSearchTool registered (Tavily)");
    }

    /**
     * 从 ToolConfig 的 JSON 配置中提取 apiKey。
     *
     * @param configJson JSON 配置字符串（如 {"apiKey":"xxx"}）
     * @return API Key；解析失败时返回 null
     */
    private String extractApiKey(String configJson) {
        if (configJson == null || configJson.isBlank()) return null;
        try {
            Map<String, Object> cfg = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            return (String) cfg.get("apiKey");
        } catch (Exception e) {
            log.warn("Failed to parse web_search config JSON: {}", e.getMessage());
            return null;
        }
    }

    // ===== Model resolution helpers =====

    /**
     * 解析 Agent 使用的 API Key。
     * <p>优先级：Agent 绑定的 AiModel 的 API Key > 全局 LLM API Key。</p>
     */
    private String resolveApiKey(Agent agent) {
        AiModel aiModel = findModel(agent);
        if (aiModel == null) return null;
        if (aiModel.getDefaultCredentialId() != null) {
            return credentialService.resolveSecret(aiModel.getDefaultCredentialId());
        }
        return aiModel.getApiKey();
    }

    /**
     * 解析 Agent 使用的模型名称。
     * <p>优先级：Agent 绑定的 AiModel 的 modelName > 全局 LLM 模型名。</p>
     */
    private String resolveModelName(Agent agent) {
        AiModel aiModel = findModel(agent);
        if (aiModel != null && aiModel.getModelName() != null) {
            return aiModel.getModelName();
        }
        return null;
    }

    /**
     * 解析 Agent 使用的 API Base URL。
     * <p>从完整的 API URL（如 {@code https://api.openai.com/v1/chat/completions}）中提取 scheme + host + port。</p>
     */
    private String resolveBaseUrl(Agent agent) {
        AiModel aiModel = findModel(agent);
        return extractBaseUrl(aiModel != null ? aiModel.getApiUrl() : null);
    }

    /**
     * 解析 Agent 使用的 API 端点路径。
     * <p>从完整的 API URL 中提取路径部分；若 URL 中无路径，则使用 AiModel 的 apiVersion 构造默认路径。</p>
     */
    private String resolveEndpointPath(Agent agent) {
        AiModel aiModel = findModel(agent);
        String apiUrl = aiModel != null ? aiModel.getApiUrl() : null;

        String path = extractPath(apiUrl);
        if (!path.isBlank()) {
            if (path.endsWith("/chat/completions") || path.endsWith("/messages")) {
                return path;
            }
            return path + "/chat/completions";
        }

        String version = aiModel != null ? aiModel.getApiVersion() : null;
        if (version != null && !version.isBlank()) {
            return version + "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    /** 根据 Agent 查找绑定的 AiModel（可能返回 null） */
    private AiModel findModel(Agent agent) {
        if (agent.getModelId() != null) {
            return aiModelRepository.findById(agent.getModelId()).orElse(null);
        }
        return aiModelRepository.findByIsDefaultTrue()
                .filter(model -> Boolean.TRUE.equals(model.getEnabled()))
                .orElse(null);
    }

    /**
     * 从完整 API URL 中提取 base URL（scheme + host + port）。
     * <p>例如：从 {@code https://api.openai.com/v1/chat/completions} 提取 {@code https://api.openai.com}。</p>
     */
    private static String extractBaseUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(apiUrl);
            int port = uri.getPort();
            return port > 0
                    ? uri.getScheme() + "://" + uri.getHost() + ":" + port
                    : uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从完整 API URL 中提取路径（含 query string）。
     * <p>例如：从 {@code https://api.openai.com/v1/chat/completions} 提取 {@code /v1/chat/completions}。</p>
     */
    private static String extractPath(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) return "";
        try {
            java.net.URI uri = java.net.URI.create(apiUrl);
            String path = uri.getPath();
            String query = uri.getQuery();
            return query != null ? path + "?" + query : path;
        } catch (Exception e) {
            return "";
        }
    }
}
