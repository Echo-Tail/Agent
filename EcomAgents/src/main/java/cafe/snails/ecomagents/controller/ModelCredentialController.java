package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.service.ModelCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 模型凭据管理接口，负责凭据创建、轮换、查询和删除。 */
@RestController
@RequestMapping("/v1/model-credentials")
@RequiredArgsConstructor
public class ModelCredentialController {
    private final ModelCredentialService service;

    /** 查询模型凭据的脱敏信息。 */
    @GetMapping
    public ApiResponse<List<ModelCredentialResponse>> list() {
        return ApiResponse.success(service.list());
    }

    /** 创建并加密保存模型凭据。 */
    @PostMapping
    public ApiResponse<ModelCredentialResponse> create(@Valid @RequestBody ModelCredentialRequest request) {
        return ApiResponse.success("凭据创建成功", ModelCredentialResponse.from(service.create(request)));
    }

    /** 轮换指定模型凭据的密钥。 */
    @PutMapping("/{id}/secret")
    public ApiResponse<ModelCredentialResponse> rotate(@PathVariable Long id,
            @Valid @RequestBody RotateCredentialRequest request) {
        return ApiResponse.success("凭据轮换成功", service.rotate(id, request.secret()));
    }

    /** 删除未被模型使用的凭据。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("凭据删除成功", null);
    }
}
