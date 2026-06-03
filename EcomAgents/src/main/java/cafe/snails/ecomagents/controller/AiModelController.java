package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ModelValidateRequest;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置 CRUD 控制器。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    /** 获取所有模型列表 */
    @GetMapping("/models")
    public ApiResponse<List<AiModel>> listModels() {
        return aiModelService.listModels();
    }

    /** 获取已启用的模型列表 */
    @GetMapping("/models/enabled")
    public ApiResponse<List<AiModel>> listEnabledModels() {
        return aiModelService.listEnabledModels();
    }

    /** 获取默认模型 */
    @GetMapping("/models/default")
    public ApiResponse<AiModel> getDefaultModel() {
        return aiModelService.getDefaultModel();
    }

    /** 获取已启用的图片生成模型列表 */
    @GetMapping("/models/image")
    public ApiResponse<List<AiModel>> getImageModels() {
        return aiModelService.getEnabledImageModels();
    }

    /** 获取单个模型详情 */
    @GetMapping("/models/{id}")
    public ApiResponse<AiModel> getModel(@PathVariable("id") Long id) {
        return aiModelService.getModel(id);
    }

    /** 创建模型配置 */
    @PostMapping("/models")
    public ApiResponse<AiModel> createModel(@RequestBody AiModel model) {
        return aiModelService.createModel(model);
    }

    /** 更新模型配置 */
    @PutMapping("/models/{id}")
    public ApiResponse<AiModel> updateModel(@PathVariable("id") Long id, @RequestBody AiModel updates) {
        return aiModelService.updateModel(id, updates);
    }

    /** 删除模型配置 */
    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable("id") Long id) {
        return aiModelService.deleteModel(id);
    }

    /** 验证模型配置连通性并获取可用模型列表 */
    @PostMapping("/models/validate")
    public ApiResponse<List<String>> validateModel(@RequestBody ModelValidateRequest request) {
        return aiModelService.validateModel(request);
    }
}
