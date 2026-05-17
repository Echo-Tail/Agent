package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ModelValidateRequest;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.service.AiModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link AiModelController} 的单元测试，验证 Controller 层正确委派给 Service。
 */
@ExtendWith(MockitoExtension.class)
class AiModelControllerTest {

    @Mock
    private AiModelService aiModelService;

    private AiModelController controller;
    private AiModel sampleModel;

    @BeforeEach
    void setUp() {
        controller = new AiModelController(aiModelService);
        sampleModel = AiModel.builder()
                .id(1L).name("GPT-4o").provider("openai").modelName("gpt-4o")
                .apiUrl("https://api.openai.com/v1/chat/completions")
                .apiKey("sk-test").maxTokens(4096).temperature(0.7)
                .isDefault(true).enabled(true)
                .createdAt(LocalDate.of(2024, 1, 1)).createdBy(0L).build();
    }

    @Test
    void listModels_shouldDelegate() {
        when(aiModelService.listModels()).thenReturn(ApiResponse.success(List.of(sampleModel)));
        ApiResponse<List<AiModel>> result = controller.listModels();
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void listEnabledModels_shouldDelegate() {
        when(aiModelService.listEnabledModels()).thenReturn(ApiResponse.success(List.of(sampleModel)));
        ApiResponse<List<AiModel>> result = controller.listEnabledModels();
        assertEquals(200, result.getCode());
        assertEquals("gpt-4o", result.getData().get(0).getModelName());
    }

    @Test
    void getModel_shouldDelegate() {
        when(aiModelService.getModel(1L)).thenReturn(ApiResponse.success(sampleModel));
        ApiResponse<AiModel> result = controller.getModel(1L);
        assertEquals("GPT-4o", result.getData().getName());
    }

    @Test
    void getModel_notFound_shouldReturn404() {
        when(aiModelService.getModel(99L)).thenReturn(ApiResponse.error(404, "模型不存在"));
        ApiResponse<AiModel> result = controller.getModel(99L);
        assertEquals(404, result.getCode());
    }

    @Test
    void createModel_shouldDelegate() {
        when(aiModelService.createModel(any())).thenReturn(ApiResponse.success("模型创建成功", sampleModel));
        ApiResponse<AiModel> result = controller.createModel(sampleModel);
        assertEquals(200, result.getCode());
    }

    @Test
    void updateModel_shouldDelegate() {
        AiModel updates = AiModel.builder().name("Updated").build();
        when(aiModelService.updateModel(eq(1L), any())).thenReturn(ApiResponse.success("模型更新成功", updates));
        ApiResponse<AiModel> result = controller.updateModel(1L, updates);
        assertEquals("Updated", result.getData().getName());
    }

    @Test
    void deleteModel_shouldDelegate() {
        when(aiModelService.deleteModel(1L)).thenReturn(ApiResponse.success("模型已删除", null));
        ApiResponse<Void> result = controller.deleteModel(1L);
        assertEquals(200, result.getCode());
    }

    @Test
    void validateModel_shouldDelegate() {
        ModelValidateRequest req = ModelValidateRequest.builder()
                .baseUrl("https://api.openai.com").apiType("openai").apiVersion("/v1").apiKey("sk-test").build();
        when(aiModelService.validateModel(any())).thenReturn(ApiResponse.success(List.of("gpt-4o", "gpt-4o-mini")));
        ApiResponse<List<String>> result = controller.validateModel(req);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }
}
