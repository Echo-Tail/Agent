package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.SystemLogRequest;
import cafe.snails.ecomagents.model.SystemLog;
import cafe.snails.ecomagents.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/system-logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @PostMapping
    public ApiResponse<SystemLog> createLog(@RequestBody SystemLogRequest request) {
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

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return systemLogService.getStats();
    }

    @DeleteMapping
    public ApiResponse<Void> clearLogs() {
        return systemLogService.clearLogs();
    }
}
