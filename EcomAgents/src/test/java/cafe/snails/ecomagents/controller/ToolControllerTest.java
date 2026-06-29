package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.service.ToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link ToolController} 的单元测试，验证 Controller 层正确委派给 Service。
 */
@ExtendWith(MockitoExtension.class)
class ToolControllerTest {

    @Mock
    private ToolService toolService;

    private ToolController controller;

    private ToolDefinition sampleTool;

    @BeforeEach
    void setUp() {
        controller = new ToolController(toolService);
        sampleTool = ToolDefinition.builder()
                .id("web_search").name("网页搜索")
                .description("搜索互联网获取最新信息")
                .category("web").enabled(true).configJson("").build();
    }

    @Test
    void listTools_shouldDelegate() {
        when(toolService.listTools()).thenReturn(ApiResponse.success(List.of(sampleTool)));
        ApiResponse<List<ToolDefinition>> result = controller.listTools();
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("web_search", result.getData().get(0).getId());
    }

    @Test
    void updateTool_shouldDelegate() {
        ToolDefinition updates = ToolDefinition.builder().name("Updated").build();
        when(toolService.updateTool(eq("web_search"), any()))
                .thenReturn(ApiResponse.success("工具更新成功", updates));
        ApiResponse<ToolDefinition> result = controller.updateTool("web_search", updates);
        assertEquals(200, result.getCode());
        assertEquals("Updated", result.getData().getName());
    }

    @Test
    void toggleTool_shouldDelegate() {
        ToolDefinition toggled = ToolDefinition.builder()
                .id("web_search").enabled(false).build();
        when(toolService.toggleTool("web_search"))
                .thenReturn(ApiResponse.success("工具已停用", toggled));
        ApiResponse<ToolDefinition> result = controller.toggleTool("web_search");
        assertEquals(200, result.getCode());
        assertFalse(result.getData().getEnabled());
    }

    @Test
    void saveToolConfig_shouldDelegate() {
        ToolDefinition configured = ToolDefinition.builder()
                .id("web_search").configJson("{\"key\":\"value\"}").build();
        when(toolService.saveToolConfig(eq("web_search"), eq("{\"key\":\"value\"}")))
                .thenReturn(ApiResponse.success("工具配置已保存", configured));
        ApiResponse<ToolDefinition> result = controller.saveToolConfig("web_search",
                Map.of("configJson", "{\"key\":\"value\"}"));
        assertEquals(200, result.getCode());
        assertEquals("{\"key\":\"value\"}", result.getData().getConfigJson());
    }
}
