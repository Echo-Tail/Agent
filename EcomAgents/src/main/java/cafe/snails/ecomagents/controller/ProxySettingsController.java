package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ProxySettingsDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ProxySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理系统出站代理配置、自动探测和连通性测试。
 */
@RestController
@RequestMapping("/v1/admin/proxy-settings")
@RequiredArgsConstructor
public class ProxySettingsController {
    private final ProxySettingsService service;

    /** 查询当前代理配置。 */
    @GetMapping
    public ApiResponse<SettingsResponse> getSettings() {
        return ApiResponse.success(service.getSettings());
    }

    /** 保存代理配置。 */
    @PutMapping
    public ApiResponse<SettingsResponse> update(
            @Valid @RequestBody UpdateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.update(request, userId));
    }

    /** 自动探测系统和本机可用代理。 */
    @PostMapping("/detect")
    public ApiResponse<DetectionResponse> detect() {
        return ApiResponse.success(service.detect());
    }

    /** 测试指定代理的外部 HTTPS 连通性。 */
    @PostMapping("/test")
    public ApiResponse<TestResponse> test(@Valid @RequestBody TestRequest request) {
        return ApiResponse.success(service.test(request.proxyUrl()));
    }
}
