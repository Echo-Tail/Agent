package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

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

    private ToolService service;

    private ToolConfig sampleConfig;
    private ToolConfig disabledConfig;

    @BeforeEach
    void setUp() {
        service = new ToolService(repository);
        // Default: admin role so configJson is returned; override in specific tests for non-admin
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        sampleConfig = ToolConfig.builder()
                .id("web_search").name("网页搜索")
                .description("搜索互联网获取最新信息")
                .category("web").enabled(true).configJson("").build();
        disabledConfig = ToolConfig.builder()
                .id("image_generation").name("图片生成")
                .description("根据文字描述生成图片")
                .category("media").enabled(false).configJson("").build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
    void listTools_nonAdmin_shouldStripConfigJson() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        ToolConfig config = ToolConfig.builder()
                .id("test_tool").name("Test Tool")
                .description("A test tool").category("test")
                .enabled(true).configJson("{\"secret\":\"sk-xxx\"}").build();
        when(repository.findAll()).thenReturn(List.of(config));
        ApiResponse<List<ToolDefinition>> result = service.listTools();
        assertEquals(1, result.getData().size());
        assertEquals("test_tool", result.getData().get(0).getId());
        assertTrue(result.getData().get(0).getConfigJson().isEmpty());
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
