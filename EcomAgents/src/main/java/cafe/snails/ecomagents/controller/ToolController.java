package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * 系统工具控制器，提供工具列表查询、更新、启用/停用和配置管理接口。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    /**
     * 获取系统所有可用工具。
     */
    @GetMapping("/tools")
    public ApiResponse<List<ToolDefinition>> listTools() {
        return toolService.listTools();
    }

    /**
     * 更新工具定义。
     */
    @PutMapping("/tools/{id}")
    public ApiResponse<ToolDefinition> updateTool(@PathVariable("id") String id,
                                                   @Valid @RequestBody ToolDefinition definition) {
        return toolService.updateTool(id, definition);
    }

    /**
     * 切换工具启用/停用状态。
     */
    @PatchMapping("/tools/{id}/toggle")
    public ApiResponse<ToolDefinition> toggleTool(@PathVariable("id") String id) {
        return toolService.toggleTool(id);
    }

    /**
     * 保存工具 JSON 配置。
     */
    @PutMapping("/tools/{id}/config")
    public ApiResponse<ToolDefinition> saveToolConfig(@PathVariable("id") String id,
                                                       @RequestBody Map<String, String> body) {
        return toolService.saveToolConfig(id, body.get("configJson"));
    }
}
