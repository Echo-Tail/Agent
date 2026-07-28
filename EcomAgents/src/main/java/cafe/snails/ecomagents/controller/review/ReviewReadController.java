package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewAnalysisDtos.AnalysisRunResponse;
import cafe.snails.ecomagents.dto.review.ReviewQueryDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/review-analysis/projects/{projectId}")
@RequiredArgsConstructor
public class ReviewReadController {
    private final ReviewReadService service;

    @GetMapping("/reviews")
    public ApiResponse<Page<ProductReviewResponse>> reviews(
            @PathVariable Long projectId,
            @RequestParam(required = false) String asin,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) BigDecimal maxRating,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.reviews(projectId, asin, minRating, maxRating, verified, keyword,
                page(page, size, Sort.by(Sort.Direction.DESC, "reviewDate", "id")), userId));
    }

    @GetMapping("/analysis-runs/{runId}/insights")
    public ApiResponse<Page<InsightResponse>> insights(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Boolean manuallyEdited,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.insights(projectId, runId, scenario, module, severity, sentiment,
                actionType, manuallyEdited, keyword,
                page(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")), userId));
    }

    @PatchMapping("/analysis-runs/{runId}/insights/{insightId}")
    public ApiResponse<InsightResponse> updateInsight(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long insightId,
            @Valid @RequestBody UpdateInsightRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.updateInsight(projectId, runId, insightId, request, userId));
    }

    @PostMapping("/analysis-runs/{runId}/confirm")
    public ApiResponse<AnalysisRunResponse> confirm(
            @PathVariable Long projectId, @PathVariable Long runId, @CurrentUserId Long userId) {
        return ApiResponse.success(ReviewAnalysisService.toResponse(service.confirm(projectId, runId, userId)));
    }

    @GetMapping("/analysis-runs/{runId}/dashboard")
    public ApiResponse<DashboardResponse> dashboard(
            @PathVariable Long projectId, @PathVariable Long runId, @CurrentUserId Long userId) {
        return ApiResponse.success(service.dashboard(projectId, runId, userId));
    }

    private Pageable page(int page, int size, Sort sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        return PageRequest.of(safePage, safeSize, sort);
    }
}
