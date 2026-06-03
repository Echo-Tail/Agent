package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统工具服务，提供基于数据库的工具配置管理能力。
 */
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolConfigRepository repository;

    /**
     * 获取系统所有可用工具（含未启用）。
     * 非管理员不返回 configJson（避免泄露 API Key 等敏感配置）。
     */
    public ApiResponse<List<ToolDefinition>> listTools() {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ApiResponse.success(repository.findAll().stream()
                .map(config -> {
                    ToolDefinition def = toDefinition(config);
                    if (!isAdmin) {
                        def.setConfigJson("");
                    }
                    return def;
                })
                .toList());
    }

    /**
     * 根据工具 ID 列表筛选出匹配的工具定义（仅返回启用的工具）。
     */
    public List<ToolDefinition> getToolsByIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) return List.of();
        return repository.findAllById(toolIds).stream()
                .filter(ToolConfig::getEnabled)
                .map(this::toDefinition)
                .toList();
    }

    /**
     * 更新工具定义。
     */
    @Transactional
    public ApiResponse<ToolDefinition> updateTool(String id, ToolDefinition definition) {
        return repository.findById(id)
                .map(config -> {
                    if (definition.getName() != null) config.setName(definition.getName());
                    if (definition.getDescription() != null) config.setDescription(definition.getDescription());
                    if (definition.getCategory() != null) config.setCategory(definition.getCategory());
                    if (definition.getEnabled() != null) config.setEnabled(definition.getEnabled());
                    if (definition.getConfigJson() != null) config.setConfigJson(definition.getConfigJson());
                    return ApiResponse.success("工具更新成功", toDefinition(repository.save(config)));
                })
                .orElseGet(() -> ApiResponse.error(404, "工具不存在"));
    }

    /**
     * 切换工具启用/停用状态。
     */
    @Transactional
    public ApiResponse<ToolDefinition> toggleTool(String id) {
        return repository.findById(id)
                .map(config -> {
                    config.setEnabled(!config.getEnabled());
                    return ApiResponse.success(
                            config.getEnabled() ? "工具已启用" : "工具已停用",
                            toDefinition(repository.save(config)));
                })
                .orElseGet(() -> ApiResponse.error(404, "工具不存在"));
    }

    /**
     * 保存工具 JSON 配置。
     */
    @Transactional
    public ApiResponse<ToolDefinition> saveToolConfig(String id, String configJson) {
        return repository.findById(id)
                .map(config -> {
                    config.setConfigJson(configJson != null ? configJson : "");
                    return ApiResponse.<ToolDefinition>success("工具配置已保存", toDefinition(repository.save(config)));
                })
                .orElseGet(() -> ApiResponse.error(404, "工具不存在"));
    }



    /**
     * 将 ToolConfig 实体转换为 ToolDefinition DTO。
     */
    private ToolDefinition toDefinition(ToolConfig config) {
        return ToolDefinition.builder()
                .id(config.getId())
                .name(config.getName())
                .description(config.getDescription())
                .category(config.getCategory())
                .enabled(config.getEnabled())
                .configJson(config.getConfigJson())
                .build();
    }
}
