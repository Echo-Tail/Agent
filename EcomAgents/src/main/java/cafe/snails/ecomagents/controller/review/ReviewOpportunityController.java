package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewOpportunityDtos.*;
import cafe.snails.ecomagents.dto.review.ReviewQueryDtos.InsightResponse;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.ReviewOpportunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 评论机会点接口，提供机会点、洞察及投入度维护能力。 */
@RestController
@RequestMapping("/v1/review-analysis/projects/{projectId}/analysis-runs/{runId}/opportunities")
@RequiredArgsConstructor
public class ReviewOpportunityController {
    private final ReviewOpportunityService service;

    /** 查询分析任务识别出的机会点。 */
    @GetMapping
    public ApiResponse<List<OpportunityResponse>> list(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.list(projectId, runId, userId));
    }

    /** 查询机会点关联的评论洞察。 */
    @GetMapping("/{opportunityId}/insights")
    public ApiResponse<List<InsightResponse>> insights(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long opportunityId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.insights(projectId, runId, opportunityId, userId));
    }

    /** 更新机会点的预估投入度。 */
    @PatchMapping("/{opportunityId}/effort")
    public ApiResponse<OpportunityResponse> updateEffort(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long opportunityId,
            @Valid @RequestBody UpdateEffortRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.updateEffort(
                projectId, runId, opportunityId, request.implementationEffort(), userId));
    }
}
