package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统工具控制器，提供可用工具列表。
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
}
