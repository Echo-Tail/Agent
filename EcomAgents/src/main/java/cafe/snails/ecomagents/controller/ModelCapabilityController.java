package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.AiModelCapability;
import cafe.snails.ecomagents.service.ModelCapabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/models/{modelId}/capabilities")
@RequiredArgsConstructor
public class ModelCapabilityController {
    private final ModelCapabilityService service;

    @GetMapping
    public ApiResponse<List<AiModelCapability>> list(@PathVariable Long modelId) {
        return ApiResponse.success(service.list(modelId));
    }

    @PutMapping
    public ApiResponse<List<AiModelCapability>> replace(@PathVariable Long modelId,
            @Valid @RequestBody List<@Valid ModelCapabilityConfigRequest> requests) {
        return ApiResponse.success("模型能力更新成功", service.replace(modelId, requests));
    }
}
