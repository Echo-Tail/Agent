package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link ToolService} 的单元测试，覆盖基于数据库的工具管理逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ToolServiceTest {

    @Mock
    private ToolConfigRepository repository;

    @Mock
    private AiModelRepository aiModelRepository;

    private ToolService service;
    private ObjectMapper objectMapper;

    private ToolConfig sampleConfig;
    private ToolConfig disabledConfig;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ToolService(repository, aiModelRepository, objectMapper);
        sampleConfig = ToolConfig.builder()
                .id("web_search").name("网页搜索")
                .description("搜索互联网获取最新信息")
                .category("web").enabled(true).configJson("").build();
        disabledConfig = ToolConfig.builder()
                .id("image_generation").name("图片生成")
                .description("根据文字描述生成图片")
                .category("media").enabled(false).configJson("").build();
    }

    @Test
    void listTools_shouldReturnAll() {
        when(repository.findAll()).thenReturn(List.of(sampleConfig, disabledConfig));
        ApiResponse<List<ToolDefinition>> result = service.listTools();
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals("web_search", result.getData().get(0).getId());
        assertTrue(result.getData().get(0).getEnabled());
        assertFalse(result.getData().get(1).getEnabled());
    }

    @Test
    void getToolsByIds_shouldFilterCorrectly() {
        when(repository.findAllById(List.of("web_search")))
                .thenReturn(List.of(sampleConfig));
        List<ToolDefinition> result = service.getToolsByIds(List.of("web_search"));
        assertEquals(1, result.size());
        assertEquals("web_search", result.get(0).getId());
    }

    @Test
    void getToolsByIds_shouldOnlyReturnEnabled() {
        when(repository.findAllById(List.of("web_search", "image_generation")))
                .thenReturn(List.of(sampleConfig, disabledConfig));
        List<ToolDefinition> result = service.getToolsByIds(List.of("web_search", "image_generation"));
        assertEquals(1, result.size()); // only the enabled one passes the filter
        assertTrue(result.get(0).getEnabled());
        assertEquals("web_search", result.get(0).getId());
    }

    @Test
    void getToolsByIds_nullOrEmpty_shouldReturnEmpty() {
        assertTrue(service.getToolsByIds(null).isEmpty());
        assertTrue(service.getToolsByIds(List.of()).isEmpty());
    }

    @Test
    void updateTool_shouldUpdateFields() {
        when(repository.findById("web_search")).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        ToolDefinition updates = ToolDefinition.builder()
                .name("Updated Search").description("Updated description")
                .category("new_category").build();
        ApiResponse<ToolDefinition> result = service.updateTool("web_search", updates);

        assertEquals(200, result.getCode());
        assertEquals("Updated Search", result.getData().getName());
        assertEquals("Updated description", result.getData().getDescription());
        assertEquals("new_category", result.getData().getCategory());
    }

    @Test
    void updateTool_notFound_shouldReturn404() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());
        ApiResponse<ToolDefinition> result = service.updateTool("nonexistent", ToolDefinition.builder().build());
        assertEquals(404, result.getCode());
    }

    @Test
    void toggleTool_shouldFlipEnabledTrueToFalse() {
        when(repository.findById("web_search")).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(sampleConfig.getEnabled());
        ApiResponse<ToolDefinition> result = service.toggleTool("web_search");
        assertEquals(200, result.getCode());
        assertFalse(result.getData().getEnabled());
    }

    @Test
    void toggleTool_shouldFlipEnabledFalseToTrue() {
        when(repository.findById("image_generation")).thenReturn(Optional.of(disabledConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertFalse(disabledConfig.getEnabled());
        ApiResponse<ToolDefinition> result = service.toggleTool("image_generation");
        assertEquals(200, result.getCode());
        assertTrue(result.getData().getEnabled());
    }

    @Test
    void toggleTool_notFound_shouldReturn404() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());
        ApiResponse<ToolDefinition> result = service.toggleTool("nonexistent");
        assertEquals(404, result.getCode());
    }

    @Test
    void saveToolConfig_shouldPersistJson() {
        when(repository.findById("web_search")).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<ToolDefinition> result = service.saveToolConfig("web_search", "{\"key\":\"value\"}");
        assertEquals(200, result.getCode());
        assertEquals("{\"key\":\"value\"}", result.getData().getConfigJson());
    }

    @Test
    void saveToolConfig_nullJson_shouldSetEmpty() {
        when(repository.findById("web_search")).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<ToolDefinition> result = service.saveToolConfig("web_search", null);
        assertEquals(200, result.getCode());
        assertEquals("", result.getData().getConfigJson());
    }

    @Test
    void saveToolConfig_notFound_shouldReturn404() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());
        ApiResponse<ToolDefinition> result = service.saveToolConfig("nonexistent", "{}");
        assertEquals(404, result.getCode());
    }

    @Test
    void saveToolConfig_imageGeneration_withValidModelId_shouldSucceed() {
        ToolConfig imgConfig = ToolConfig.builder()
                .id("image_generation").name("图片生成")
                .description("根据文字描述生成图片")
                .category("media").enabled(true).configJson("").build();
        when(repository.findById("image_generation")).thenReturn(Optional.of(imgConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        AiModel model = AiModel.builder().id(1L).name("DALL-E 3")
                .provider("openai").modelName("dall-e-3")
                .enabled(true).createdAt(LocalDate.now()).createdBy(1L).build();
        when(aiModelRepository.findById(1L)).thenReturn(Optional.of(model));

        String config = "{\"apiKey\":\"sk-xxx\",\"modelId\":1}";
        ApiResponse<ToolDefinition> result = service.saveToolConfig("image_generation", config);
        assertEquals(200, result.getCode());
        assertTrue(result.getData().getConfigJson().contains("modelId"));
    }

    @Test
    void saveToolConfig_imageGeneration_withInvalidModelId_shouldReturn400() {
        ToolConfig imgConfig = ToolConfig.builder()
                .id("image_generation").name("图片生成")
                .description("根据文字描述生成图片")
                .category("media").enabled(true).configJson("").build();
        when(repository.findById("image_generation")).thenReturn(Optional.of(imgConfig));
        when(aiModelRepository.findById(999L)).thenReturn(Optional.empty());

        String config = "{\"apiKey\":\"sk-xxx\",\"modelId\":999}";
        ApiResponse<ToolDefinition> result = service.saveToolConfig("image_generation", config);
        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("模型不存在"));
    }

    @Test
    void saveToolConfig_imageGeneration_withDisabledModel_shouldReturn400() {
        ToolConfig imgConfig = ToolConfig.builder()
                .id("image_generation").name("图片生成")
                .description("根据文字描述生成图片")
                .category("media").enabled(true).configJson("").build();
        when(repository.findById("image_generation")).thenReturn(Optional.of(imgConfig));

        AiModel disabled = AiModel.builder().id(2L).name("DALL-E 2")
                .provider("openai").modelName("dall-e-2")
                .enabled(false).createdAt(LocalDate.now()).createdBy(1L).build();
        when(aiModelRepository.findById(2L)).thenReturn(Optional.of(disabled));

        String config = "{\"apiKey\":\"sk-xxx\",\"modelId\":2}";
        ApiResponse<ToolDefinition> result = service.saveToolConfig("image_generation", config);
        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("已停用"));
    }

    @Test
    void saveToolConfig_imageGeneration_withoutModelId_shouldBeBackwardCompatible() {
        ToolConfig imgConfig = ToolConfig.builder()
                .id("image_generation").name("图片生成")
                .description("根据文字描述生成图片")
                .category("media").enabled(true).configJson("").build();
        when(repository.findById("image_generation")).thenReturn(Optional.of(imgConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // No modelId in config — old-format config should still work
        String config = "{\"apiKey\":\"sk-xxx\"}";
        ApiResponse<ToolDefinition> result = service.saveToolConfig("image_generation", config);
        assertEquals(200, result.getCode());
    }

    @Test
    void saveToolConfig_nonImageTool_withModelId_shouldBeIgnored() {
        // web_search with a modelId should pass validation since web_search has no modelId check
        when(repository.findById("web_search")).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        String config = "{\"apiKey\":\"sk-xxx\",\"modelId\":999}";
        ApiResponse<ToolDefinition> result = service.saveToolConfig("web_search", config);
        assertEquals(200, result.getCode());
        // Verify AiModelRepository was never called
        verify(aiModelRepository, never()).findById(any());
    }

    @Test
    void toDefinition_shouldMapAllFields() {
        ToolConfig config = ToolConfig.builder()
                .id("test_tool").name("Test Tool")
                .description("A test tool").category("test")
                .enabled(true).configJson("{\"test\":true}").build();

        // Access the private method via reflection-level testing:
        // We verify the mapping by calling listTools which internally uses toDefinition
        when(repository.findAll()).thenReturn(List.of(config));
        ApiResponse<List<ToolDefinition>> result = service.listTools();

        assertEquals(1, result.getData().size());
        ToolDefinition dto = result.getData().get(0);
        assertEquals("test_tool", dto.getId());
        assertEquals("Test Tool", dto.getName());
        assertEquals("A test tool", dto.getDescription());
        assertEquals("test", dto.getCategory());
        assertTrue(dto.getEnabled());
        assertEquals("{\"test\":true}", dto.getConfigJson());
    }
}
