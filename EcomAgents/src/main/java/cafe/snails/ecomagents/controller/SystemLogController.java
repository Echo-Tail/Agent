package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.SystemLogRequest;
import cafe.snails.ecomagents.model.SystemLog;
import cafe.snails.ecomagents.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统日志控制器，提供日志写入、分页查询、统计和清空接口。
 */
@RestController
@RequestMapping("/v1/system-logs")
@RequiredArgsConstructor
public class SystemLogController {

    /** 系统日志业务服务。 */
    private final SystemLogService systemLogService;

    /** 写入一条系统日志。 */
    @PostMapping
    public ApiResponse<SystemLog> createLog(@Valid @RequestBody SystemLogRequest request) {
        return systemLogService.writeLog(
                request.getLevel(),
                request.getCategory(),
                request.getMessage(),
                request.getData(),
                request.getDuration(),
                request.getRoute(),
                request.getUserId()
        );
    }

    /** 按级别、类别、时间范围和关键字分页查询日志。 */
    @GetMapping
    public ApiResponse<Page<SystemLog>> queryLogs(
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return systemLogService.queryLogs(levels, categories, startDate, endDate, search, page, size);
    }

    /** 获取系统日志统计信息。 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return systemLogService.getStats();
    }

    /** 清空系统日志。 */
    @DeleteMapping
    public ApiResponse<Void> clearLogs() {
        return systemLogService.clearLogs();
    }
}
