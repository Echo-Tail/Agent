package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.AiModelCapability;
import cafe.snails.ecomagents.service.ModelCapabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** AI 模型能力配置接口，管理模型支持的调用能力及协议。 */
@RestController
@RequestMapping("/v1/models/{modelId}/capabilities")
@RequiredArgsConstructor
public class ModelCapabilityController {
    private final ModelCapabilityService service;

    /** 查询指定模型的能力配置。 */
    @GetMapping
    public ApiResponse<List<AiModelCapability>> list(@PathVariable Long modelId) {
        return ApiResponse.success(service.list(modelId));
    }

    /** 使用请求内容整体替换指定模型的能力配置。 */
    @PutMapping
    public ApiResponse<List<AiModelCapability>> replace(@PathVariable Long modelId,
            @Valid @RequestBody List<@Valid ModelCapabilityConfigRequest> requests) {
        return ApiResponse.success("模型能力更新成功", service.replace(modelId, requests));
    }
}
