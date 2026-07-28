package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewValidationDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.ReviewValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/review-analysis/projects/{projectId}/analysis-runs/{runId}/validation")
@RequiredArgsConstructor
public class ReviewValidationController {
    private final ReviewValidationService service;

    @GetMapping("/sample")
    public ApiResponse<List<AuditSample>> sample(
            @PathVariable Long projectId, @PathVariable Long runId, @CurrentUserId Long userId) {
        return ApiResponse.success(service.sample(projectId, runId, userId));
    }

    @PutMapping("/sample/{insightId}")
    public ApiResponse<AuditSample> audit(
            @PathVariable Long projectId, @PathVariable Long runId, @PathVariable Long insightId,
            @Valid @RequestBody AuditRequest request, @CurrentUserId Long userId) {
        return ApiResponse.success(service.audit(projectId, runId, insightId, request, userId));
    }

    @GetMapping("/report")
    public ApiResponse<ValidationReport> report(
            @PathVariable Long projectId, @PathVariable Long runId, @CurrentUserId Long userId) {
        return ApiResponse.success(service.report(projectId, runId, userId));
    }
}
