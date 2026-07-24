package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ImageRuntimeMonitoringResponse;
import cafe.snails.ecomagents.service.image.runtime.ImageRuntimeMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/image-runtime")
@RequiredArgsConstructor
public class ImageRuntimeMonitoringController {
    private final ImageRuntimeMonitoringService monitoringService;

    @GetMapping
    public ApiResponse<ImageRuntimeMonitoringResponse> snapshot() {
        return ApiResponse.success(monitoringService.snapshot());
    }
}
