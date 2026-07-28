package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewProjectDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.ReviewProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 评论分析项目接口，管理项目及其关联商品。 */
@RestController
@RequestMapping("/v1/review-analysis/projects")
@RequiredArgsConstructor
public class ReviewProjectController {
    private final ReviewProjectService service;

    /** 查询当前用户的评论分析项目。 */
    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(@CurrentUserId Long userId) {
        return ApiResponse.success(service.list(userId));
    }

    /** 查询指定评论分析项目。 */
    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> get(@PathVariable Long projectId, @CurrentUserId Long userId) {
        return ApiResponse.success(service.get(projectId, userId));
    }

    /** 创建评论分析项目。 */
    @PostMapping
    public ApiResponse<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request, @CurrentUserId Long userId) {
        return ApiResponse.success(service.create(request, userId));
    }

    /** 更新评论分析项目基本信息。 */
    @PatchMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.update(projectId, request, userId));
    }

    /** 整体替换项目关联的商品列表。 */
    @PutMapping("/{projectId}/products")
    public ApiResponse<ProjectResponse> replaceProducts(
            @PathVariable Long projectId,
            @Valid @RequestBody List<@Valid ProjectProductRequest> products,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.replaceProducts(projectId, products, userId));
    }

    /** 删除评论分析项目。 */
    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable Long projectId, @CurrentUserId Long userId) {
        service.delete(projectId, userId);
        return ApiResponse.success(null);
    }
}
