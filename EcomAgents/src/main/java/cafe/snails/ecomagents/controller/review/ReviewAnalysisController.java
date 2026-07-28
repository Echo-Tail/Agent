package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewAnalysisDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.ReviewAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 评论分析任务接口，负责分析任务的创建、查询与失败重试。 */
@RestController
@RequestMapping("/v1/review-analysis/projects/{projectId}/analysis-runs")
@RequiredArgsConstructor
public class ReviewAnalysisController {
    private final ReviewAnalysisService service;

    /** 查询项目下的评论分析任务。 */
    @GetMapping
    public ApiResponse<List<AnalysisRunResponse>> list(
            @PathVariable Long projectId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.list(projectId, userId));
    }

    /** 启动新的评论分析任务。 */
    @PostMapping
    public ApiResponse<AnalysisRunResponse> start(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.start(projectId, idempotencyKey, userId));
    }

    /** 查询指定评论分析任务。 */
    @GetMapping("/{runId}")
    public ApiResponse<AnalysisRunResponse> get(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.get(projectId, runId, userId));
    }

    /** 重试分析任务中的失败项。 */
    @PostMapping("/{runId}/retry-failures")
    public ApiResponse<AnalysisRunResponse> retryFailures(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.retryFailures(projectId, runId, userId));
    }
}
