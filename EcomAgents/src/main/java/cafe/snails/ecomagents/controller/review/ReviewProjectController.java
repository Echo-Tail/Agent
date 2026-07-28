package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewProjectDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.ReviewProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/review-analysis/projects")
@RequiredArgsConstructor
public class ReviewProjectController {
    private final ReviewProjectService service;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(@CurrentUserId Long userId) {
        return ApiResponse.success(service.list(userId));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> get(@PathVariable Long projectId, @CurrentUserId Long userId) {
        return ApiResponse.success(service.get(projectId, userId));
    }

    @PostMapping
    public ApiResponse<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request, @CurrentUserId Long userId) {
        return ApiResponse.success(service.create(request, userId));
    }

    @PatchMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.update(projectId, request, userId));
    }

    @PutMapping("/{projectId}/products")
    public ApiResponse<ProjectResponse> replaceProducts(
            @PathVariable Long projectId,
            @Valid @RequestBody List<@Valid ProjectProductRequest> products,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.replaceProducts(projectId, products, userId));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable Long projectId, @CurrentUserId Long userId) {
        service.delete(projectId, userId);
        return ApiResponse.success(null);
    }
}
