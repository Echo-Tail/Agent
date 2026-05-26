package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ToolAvailability;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentToolAvailabilityService {

    public static final String WEB_SEARCH_TOOL_ID = "web_search";

    private final AgentRepository agentRepository;
    private final ToolConfigRepository toolConfigRepository;
    private final ObjectMapper objectMapper;

    public ToolAvailability getWebSearchAvailability(Long agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return ToolAvailability.builder()
                    .toolId(WEB_SEARCH_TOOL_ID)
                    .agentId(agentId)
                    .message("Agent 不存在")
                    .build();
        }

        boolean systemAgent = Boolean.TRUE.equals(agent.getIsSystem());
        boolean bound = systemAgent || (agent.getTools() != null && agent.getTools().contains(WEB_SEARCH_TOOL_ID));
        ToolConfig config = toolConfigRepository.findById(WEB_SEARCH_TOOL_ID).orElse(null);
        boolean enabled = config != null && Boolean.TRUE.equals(config.getEnabled());
        boolean configured = config != null && hasApiKey(config.getConfigJson());
        boolean available = bound && enabled && configured;

        return ToolAvailability.builder()
                .toolId(WEB_SEARCH_TOOL_ID)
                .agentId(agentId)
                .boundToAgent(bound)
                .globallyEnabled(enabled)
                .configured(configured)
                .available(available)
                .message(buildMessage(bound, enabled, configured, systemAgent))
                .build();
    }

    public boolean isWebSearchAvailable(Long agentId) {
        return getWebSearchAvailability(agentId).isAvailable();
    }

    private boolean hasApiKey(String configJson) {
        if (configJson == null || configJson.isBlank()) return false;
        try {
            Map<String, Object> cfg = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            Object apiKey = cfg.get("apiKey");
            return apiKey != null && !apiKey.toString().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private String buildMessage(boolean bound, boolean enabled, boolean configured, boolean systemAgent) {
        if (!bound) {
            return "当前 Agent 未绑定网页搜索工具，请在 Agent 编辑页选择 web_search。";
        }
        if (!enabled) {
            return "网页搜索工具已被管理员停用，请先在工具管理中启用。";
        }
        if (!configured) {
            return "网页搜索工具未配置 Tavily API Key，请先在工具管理中配置。";
        }
        if (systemAgent) {
            return "系统助手会自动使用所有已启用且已配置的工具，网页搜索工具可用。";
        }
        return "网页搜索工具可用。";
    }
}
