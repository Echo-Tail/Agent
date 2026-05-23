package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 系统工具服务，提供基于数据库的工具配置管理能力。
 */
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolConfigRepository repository;
    private final AiModelRepository aiModelRepository;
    private final ObjectMapper objectMapper;

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
     * <p>对 {@code image_generation} 工具，若配置中包含 {@code modelId} 则校验引用的 AiModel 存在且已启用。</p>
     */
    @Transactional
    public ApiResponse<ToolDefinition> saveToolConfig(String id, String configJson) {
        return repository.findById(id)
                .map(config -> {
                    // Validate modelId for image_generation
                    if ("image_generation".equals(id) && configJson != null && !configJson.isEmpty()) {
                        ApiResponse<ToolDefinition> validationError = validateImageGenerationConfig(configJson);
                        if (validationError != null) return validationError;
                    }

                    config.setConfigJson(configJson != null ? configJson : "");
                    return ApiResponse.<ToolDefinition>success("工具配置已保存", toDefinition(repository.save(config)));
                })
                .orElseGet(() -> ApiResponse.error(404, "工具不存在"));
    }

    /**
     * 校验 image_generation 配置中的 modelId 字段。
     *
     * @return 校验失败时返回错误 ApiResponse，校验通过返回 null
     */
    private ApiResponse<ToolDefinition> validateImageGenerationConfig(String configJson) {
        try {
            Map<String, Object> cfg = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            Object modelIdObj = cfg.get("modelId");
            if (modelIdObj != null) {
                Long modelId;
                if (modelIdObj instanceof Number) {
                    modelId = ((Number) modelIdObj).longValue();
                } else {
                    return ApiResponse.error(400, "modelId 必须是有效的数字");
                }
                AiModel model = aiModelRepository.findById(modelId).orElse(null);
                if (model == null) {
                    return ApiResponse.error(400, "模型不存在：ID=" + modelId);
                }
                if (!Boolean.TRUE.equals(model.getEnabled())) {
                    return ApiResponse.error(400, "模型已停用：ID=" + modelId + "（" + model.getName() + "）");
                }
            }
            return null;
        } catch (Exception e) {
            return ApiResponse.error(400, "配置 JSON 格式无效");
        }
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
