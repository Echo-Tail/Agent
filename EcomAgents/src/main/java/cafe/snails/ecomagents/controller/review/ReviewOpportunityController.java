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

@RestController
@RequestMapping("/v1/review-analysis/projects/{projectId}/analysis-runs/{runId}/opportunities")
@RequiredArgsConstructor
public class ReviewOpportunityController {
    private final ReviewOpportunityService service;

    @GetMapping
    public ApiResponse<List<OpportunityResponse>> list(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.list(projectId, runId, userId));
    }

    @GetMapping("/{opportunityId}/insights")
    public ApiResponse<List<InsightResponse>> insights(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long opportunityId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.insights(projectId, runId, opportunityId, userId));
    }

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
