package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ImageRuntimeMonitoringResponse;
import cafe.snails.ecomagents.service.image.runtime.ImageRuntimeMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 图片生成运行时监控接口。 */
@RestController
@RequestMapping("/v1/admin/image-runtime")
@RequiredArgsConstructor
public class ImageRuntimeMonitoringController {
    private final ImageRuntimeMonitoringService monitoringService;

    /** 获取当前图片生成队列与执行器状态快照。 */
    @GetMapping
    public ApiResponse<ImageRuntimeMonitoringResponse> snapshot() {
        return ApiResponse.success(monitoringService.snapshot());
    }
}
