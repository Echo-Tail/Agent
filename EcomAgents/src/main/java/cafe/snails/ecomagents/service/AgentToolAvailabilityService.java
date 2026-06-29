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

/**
 * Agent 工具可用性服务，综合判断工具绑定、全局启用和配置完整性。
 */
@Service
@RequiredArgsConstructor
public class AgentToolAvailabilityService {

    /** 网页搜索工具在系统中的固定工具 ID。 */
    public static final String WEB_SEARCH_TOOL_ID = "web_search";

    /** Agent 仓库，用于读取工具绑定信息。 */
    private final AgentRepository agentRepository;
    /** 工具配置仓库，用于读取全局启用状态和配置 JSON。 */
    private final ToolConfigRepository toolConfigRepository;
    /** JSON 解析器，用于读取工具配置内容。 */
    private final ObjectMapper objectMapper;

    /**
     * 获取指定 Agent 的网页搜索工具可用性详情。
     */
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

    /**
     * 判断指定 Agent 是否可以实际调用网页搜索工具。
     */
    public boolean isWebSearchAvailable(Long agentId) {
        return getWebSearchAvailability(agentId).isAvailable();
    }

    /**
     * 检查工具配置 JSON 中是否包含非空 apiKey。
     */
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

    /**
     * 根据绑定、启用和配置状态生成面向前端展示的诊断消息。
     */
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
