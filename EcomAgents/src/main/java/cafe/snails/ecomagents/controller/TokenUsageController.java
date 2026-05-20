package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.TokenUsageRecord;
import cafe.snails.ecomagents.service.TokenUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Token 用量统计控制器，仅管理员可访问。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;

    /** 按日期区间查询各模型调用汇总 */
    @GetMapping("/token-usage/summary")
    public ApiResponse<List<Map<String, Object>>> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(tokenUsageService.getSummaryByDateRange(startDate, endDate));
    }

    /** 按日期区间查询图片模型调用次数 */
    @GetMapping("/token-usage/image-calls")
    public ApiResponse<Long> getImageModelCalls(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(tokenUsageService.getImageModelCallCount(startDate, endDate));
    }

    /** 按日期区间查询详细调用记录 */
    @GetMapping("/token-usage/detail")
    public ApiResponse<List<TokenUsageRecord>> getDetail(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(tokenUsageService.getDetailByDateRange(startDate, endDate));
    }
}
