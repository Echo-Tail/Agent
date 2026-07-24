package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.service.ModelCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/model-credentials")
@RequiredArgsConstructor
public class ModelCredentialController {
    private final ModelCredentialService service;

    @GetMapping
    public ApiResponse<List<ModelCredentialResponse>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping
    public ApiResponse<ModelCredentialResponse> create(@Valid @RequestBody ModelCredentialRequest request) {
        return ApiResponse.success("凭据创建成功", ModelCredentialResponse.from(service.create(request)));
    }

    @PutMapping("/{id}/secret")
    public ApiResponse<ModelCredentialResponse> rotate(@PathVariable Long id,
            @Valid @RequestBody RotateCredentialRequest request) {
        return ApiResponse.success("凭据轮换成功", service.rotate(id, request.secret()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("凭据删除成功", null);
    }
}
