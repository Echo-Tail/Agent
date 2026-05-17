package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.repository.AiModelRepository;
import io.agentscope.core.model.GenerateOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link AiModelService} 的单元测试，覆盖模型 CRUD、默认模型逻辑和 URL 解析工具方法。
 */
@ExtendWith(MockitoExtension.class)
class AiModelServiceTest {

    @Mock
    private AiModelRepository repository;

    @Mock
    private LlmConfig llmConfig;

    private AiModelService service;

    private AiModel sampleModel;

    @BeforeEach
    void setUp() {
        lenient().when(llmConfig.getMaxTokens()).thenReturn(2048);
        service = new AiModelService(repository, llmConfig);
        sampleModel = AiModel.builder()
                .id(1L).name("GPT-4o").provider("openai").modelName("gpt-4o")
                .apiUrl("https://api.openai.com/v1/chat/completions")
                .apiKey("sk-test").maxTokens(4096).temperature(0.7)
                .isDefault(true).enabled(true)
                .createdAt(LocalDate.of(2024, 1, 1)).createdBy(0L).build();
    }

    @Test
    void listModels_shouldReturnAll() {
        when(repository.findAll()).thenReturn(List.of(sampleModel));
        ApiResponse<List<AiModel>> result = service.listModels();
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("GPT-4o", result.getData().get(0).getName());
    }

    @Test
    void listEnabledModels_shouldFilterEnabled() {
        AiModel disabled = AiModel.builder().id(2L).name("Disabled").modelName("disabled")
                .enabled(false).createdAt(LocalDate.now()).createdBy(0L).build();
        when(repository.findAll()).thenReturn(List.of(sampleModel, disabled));
        ApiResponse<List<AiModel>> result = service.listEnabledModels();
        assertEquals(1, result.getData().size());
        assertTrue(result.getData().get(0).getEnabled());
    }

    @Test
    void getModel_existing_shouldReturn() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleModel));
        ApiResponse<AiModel> result = service.getModel(1L);
        assertEquals(200, result.getCode());
        assertEquals("GPT-4o", result.getData().getName());
    }

    @Test
    void getModel_notFound_shouldReturn404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        ApiResponse<AiModel> result = service.getModel(99L);
        assertEquals(404, result.getCode());
    }

    @Test
    void createModel_shouldSetDefaults() {
        AiModel input = AiModel.builder().name("New Model").modelName("new-model").build();
        when(repository.count()).thenReturn(0L);
        when(repository.save(any())).thenAnswer(i -> {
            AiModel saved = i.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        ApiResponse<AiModel> result = service.createModel(input);
        assertEquals(200, result.getCode());
        AiModel created = result.getData();
        assertEquals("New Model", created.getName());
        assertNotNull(created.getCreatedAt());
        assertEquals(0L, created.getCreatedBy());
        assertTrue(created.getIsDefault());
    }

    @Test
    void createModel_withDefault_shouldClearExistingDefault() {
        AiModel existingDefault = AiModel.builder().id(1L).name("Old Default")
                .modelName("old").isDefault(true).build();
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(existingDefault));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        AiModel input = AiModel.builder().name("New Default").modelName("new")
                .isDefault(true).build();
        service.createModel(input);
        verify(repository, times(2)).save(any());
        assertFalse(existingDefault.getIsDefault());
    }

    @Test
    void updateModel_shouldUpdateFields() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleModel));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        AiModel updates = AiModel.builder().name("Updated").temperature(0.5).build();
        ApiResponse<AiModel> result = service.updateModel(1L, updates);
        assertEquals(200, result.getCode());
        assertEquals("Updated", result.getData().getName());
        assertEquals(0.5, result.getData().getTemperature());
        assertEquals("gpt-4o", result.getData().getModelName());
    }

    @Test
    void updateModel_setDefault_shouldClearExisting() {
        AiModel existingDefault = AiModel.builder().id(2L).name("Other Default")
                .modelName("other").isDefault(true).build();
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(existingDefault));
        when(repository.findById(1L)).thenReturn(Optional.of(sampleModel));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        AiModel updates = AiModel.builder().isDefault(true).build();
        ApiResponse<AiModel> result = service.updateModel(1L, updates);
        assertTrue(result.getData().getIsDefault());
        assertFalse(existingDefault.getIsDefault());
        verify(repository, times(2)).save(any());
    }

    @Test
    void updateModel_notFound_shouldReturn404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        ApiResponse<AiModel> result = service.updateModel(99L, AiModel.builder().build());
        assertEquals(404, result.getCode());
    }

    @Test
    void deleteModel_existing_shouldDelete() {
        when(repository.existsById(1L)).thenReturn(true);
        ApiResponse<Void> result = service.deleteModel(1L);
        assertEquals(200, result.getCode());
        verify(repository).deleteById(1L);
    }

    @Test
    void deleteModel_notFound_shouldReturn404() {
        when(repository.existsById(99L)).thenReturn(false);
        ApiResponse<Void> result = service.deleteModel(99L);
        assertEquals(404, result.getCode());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void buildModelOptions_shouldBuildGenerateOptions() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleModel));
        GenerateOptions options = service.buildModelOptions(1L);
        assertNotNull(options);
    }

    @Test
    void buildModelOptions_nullId_shouldReturnNull() {
        assertNull(service.buildModelOptions(null));
    }

    @Test
    void buildModelOptions_notFound_shouldReturnNull() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertNull(service.buildModelOptions(99L));
    }

    @Test
    void buildEndpointPath_openai_shouldReturnChatCompletions() {
        AiModel m = AiModel.builder().apiType("openai").apiVersion("/v1").build();
        assertEquals("/v1/chat/completions", AiModelService.buildEndpointPath(m));
    }

    @Test
    void buildEndpointPath_anthropic_shouldReturnMessages() {
        AiModel m = AiModel.builder().apiType("anthropic").apiVersion("/v1").build();
        assertEquals("/v1/messages", AiModelService.buildEndpointPath(m));
    }

    @Test
    void buildEndpointPath_emptyVersion_shouldOmitPrefix() {
        AiModel m = AiModel.builder().apiType("openai").apiVersion("").build();
        assertEquals("/chat/completions", AiModelService.buildEndpointPath(m));
    }

    @Test
    void buildEndpointPath_nullVersion_shouldDefaultToEmpty() {
        AiModel m = AiModel.builder().apiType("openai").apiVersion(null).build();
        assertEquals("/chat/completions", AiModelService.buildEndpointPath(m));
    }

    @ParameterizedTest
    @CsvSource({
        "https://api.openai.com/v1/chat/completions, https://api.openai.com, /v1/chat/completions",
        "https://api.deepseek.com/v1/chat/completions, https://api.deepseek.com, /v1/chat/completions",
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions, https://dashscope.aliyuncs.com, /compatible-mode/v1/chat/completions",
        "http://localhost:11434/v1/chat/completions, http://localhost:11434, /v1/chat/completions",
        ",,",
        "%%%invalid,,",
        "https://host.com:8080/path?key=val, https://host.com:8080, /path?key=val"
    })
    void extractBaseUrlAndPath(String apiUrl, String expectedBase, String expectedPath) {
        assertEquals(expectedBase, AiModelService.extractBaseUrl(apiUrl));
        assertEquals(expectedPath, AiModelService.extractPath(apiUrl));
    }
}
