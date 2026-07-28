package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewCollectionDtos.CollectionResponse;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.ReviewCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 评论采集接口，负责启动、查询和重试评论采集批次。 */
@RestController
@RequestMapping("/v1/review-analysis/projects/{projectId}/collections")
@RequiredArgsConstructor
public class ReviewCollectionController {
    private final ReviewCollectionService service;

    /** 为指定项目启动评论采集批次。 */
    @PostMapping
    public ApiResponse<CollectionResponse> start(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.start(projectId, idempotencyKey, userId));
    }

    /** 查询评论采集批次状态。 */
    @GetMapping("/{batchId}")
    public ApiResponse<CollectionResponse> get(
            @PathVariable Long projectId,
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "false") boolean refresh,
            @CurrentUserId Long userId) {
        return ApiResponse.success(refresh
                ? service.progress(projectId, batchId, userId)
                : service.get(projectId, batchId, userId));
    }

    /** 重试失败的评论采集批次。 */
    @PostMapping("/{batchId}/retry")
    public ApiResponse<CollectionResponse> retry(
            @PathVariable Long projectId,
            @PathVariable Long batchId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.retry(projectId, batchId, userId));
    }
}
