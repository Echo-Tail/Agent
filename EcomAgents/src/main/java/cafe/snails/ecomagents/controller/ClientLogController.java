package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ClientLogRequest;
import cafe.snails.ecomagents.service.ClientLogFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前端客户端日志控制器。
 * <p>
 * 接收前端 Logger 缓冲批量上报的日志，由 {@link ClientLogFileService}
 * 写入本地文件（按天归档 + 5MB 自动切分）。
 * </p>
 * 前端通过 fetch（非 axios）提交日志，不会进入 axios 拦截器循环。
 */
@Slf4j
@RestController
@RequestMapping("/v1/client-logs")
@RequiredArgsConstructor
public class ClientLogController {

    private final ClientLogFileService clientLogFileService;

    /**
     * 接收前端客户端日志批量上报。
     *
     * @param request 批量日志请求体
     * @return 成功响应
     */
    @PostMapping
    public ApiResponse<Void> uploadLogs(@RequestBody ClientLogRequest request) {
        clientLogFileService.writeBatch(request);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("ok")
                .build();
    }
}
