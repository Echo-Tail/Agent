package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.harness.HarnessHooks;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * HarnessAgent 工厂，为每次 chat 请求创建带 per-request Hook 的 HarnessAgent 实例。
 * <p>
 * HarnessAgent 本身不缓存（因为 Hook 需要绑定 per-request 的 SseEmitter），
 * 但 Model 构建逻辑集中在此，避免分散到多处。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HarnessAgentManager {

    private static final Logger log = LoggerFactory.getLogger(HarnessAgentManager.class);

    private final AgentRepository agentRepository;
    private final AiModelRepository aiModelRepository;
    private final WorkspaceConfig workspaceConfig;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;

    /**
     * 为指定 Agent 创建带 per-request Hook 的 HarnessAgent 实例。
     * 每次 chat 请求调用一次，用完即弃。
     */
    public HarnessAgent createChatAgent(Long agentId, SseEmitter emitter, Long userId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        var model = OpenAIChatModel.builder()
                .apiKey(resolveApiKey(agent))
                .modelName(resolveModelName(agent))
                .baseUrl(resolveBaseUrl(agent))
                .endpointPath(resolveEndpointPath(agent))
                .stream(true)
                .build();

        Toolkit toolkit = new Toolkit();
        java.nio.file.Path workspacePath = java.nio.file.Path.of(
                workspaceConfig.getRoot(), "agent-" + agentId);

        HarnessHooks hooks = new HarnessHooks(emitter, objectMapper, agentId);

        HarnessAgent harnessAgent = HarnessAgent.builder()
                .name(agent.getName() != null ? agent.getName() : "agent-" + agentId)
                .model(model)
                .workspace(workspacePath)
                .toolkit(toolkit)
                .hooks(List.of(hooks))
                .maxIters(20)
                .build();

        log.debug("HarnessAgent created for agent {} (user {})", agentId, userId);
        return harnessAgent;
    }

    // ===== Model resolution helpers =====

    private String resolveApiKey(Agent agent) {
        AiModel aiModel = findModel(agent);
        if (aiModel != null && aiModel.getApiKey() != null && !aiModel.getApiKey().isBlank()) {
            return aiModel.getApiKey();
        }
        return llmConfig.getApiKey();
    }

    private String resolveModelName(Agent agent) {
        AiModel aiModel = findModel(agent);
        if (aiModel != null && aiModel.getModelName() != null) {
            return aiModel.getModelName();
        }
        return llmConfig.getModel();
    }

    private String resolveBaseUrl(Agent agent) {
        AiModel aiModel = findModel(agent);
        String apiUrl = aiModel != null ? aiModel.getApiUrl() : null;
        if (apiUrl == null) apiUrl = llmConfig.getApiUrl();
        return extractBaseUrl(apiUrl);
    }

    private String resolveEndpointPath(Agent agent) {
        AiModel aiModel = findModel(agent);
        String apiUrl = aiModel != null ? aiModel.getApiUrl() : null;
        if (apiUrl == null) apiUrl = llmConfig.getApiUrl();
        return extractPath(apiUrl);
    }

    private AiModel findModel(Agent agent) {
        if (agent.getModelId() == null) return null;
        return aiModelRepository.findById(agent.getModelId()).orElse(null);
    }

    private static String extractBaseUrl(String apiUrl) {
        try {
            java.net.URI uri = java.net.URI.create(apiUrl);
            int port = uri.getPort();
            return port > 0
                    ? uri.getScheme() + "://" + uri.getHost() + ":" + port
                    : uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return "https://api.openai.com";
        }
    }

    private static String extractPath(String apiUrl) {
        try {
            java.net.URI uri = java.net.URI.create(apiUrl);
            String path = uri.getPath();
            String query = uri.getQuery();
            return query != null ? path + "?" + query : path;
        } catch (Exception e) {
            return "/v1/chat/completions";
        }
    }
}
