package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统工具服务，提供预定义的工具列表供前端选择和配置。
 */
@Service
public class ToolService {

    private static final List<ToolDefinition> ALL_TOOLS = List.of(
            ToolDefinition.builder()
                    .id("web_search")
                    .name("网页搜索")
                    .description("搜索互联网获取最新信息")
                    .category("web")
                    .build(),
            ToolDefinition.builder()
                    .id("image_generation")
                    .name("图片生成")
                    .description("根据文字描述生成图片")
                    .category("media")
                    .build(),
            ToolDefinition.builder()
                    .id("browser_automation")
                    .name("浏览器自动化")
                    .description("自动浏览网页并提取内容")
                    .category("browser")
                    .build(),
            ToolDefinition.builder()
                    .id("file_operation")
                    .name("文件操作")
                    .description("读取和写入本地文件")
                    .category("terminal_files")
                    .build(),
            ToolDefinition.builder()
                    .id("code_execution")
                    .name("代码执行")
                    .description("运行 Python / JavaScript 等代码片段")
                    .category("terminal_files")
                    .build(),
            ToolDefinition.builder()
                    .id("memory_read")
                    .name("记忆读取")
                    .description("读取持久化的对话记忆")
                    .category("memory")
                    .build()
    );

    /**
     * 获取系统所有可用工具。
     */
    public ApiResponse<List<ToolDefinition>> listTools() {
        return ApiResponse.success(ALL_TOOLS);
    }

    /**
     * 根据工具 ID 列表筛选出匹配的工具定义。
     */
    public List<ToolDefinition> getToolsByIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) return List.of();
        return ALL_TOOLS.stream()
                .filter(t -> toolIds.contains(t.getId()))
                .toList();
    }
}
