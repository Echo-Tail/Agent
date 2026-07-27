package cafe.snails.ecomagents.controller.review;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.review.ReviewCollectionDtos.CollectionResponse;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.review.ReviewCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/review-analysis/projects/{projectId}/collections")
@RequiredArgsConstructor
public class ReviewCollectionController {
    private final ReviewCollectionService service;

    @PostMapping
    public ApiResponse<CollectionResponse> start(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.start(projectId, idempotencyKey, userId));
    }

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

    @PostMapping("/{batchId}/retry")
    public ApiResponse<CollectionResponse> retry(
            @PathVariable Long projectId,
            @PathVariable Long batchId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(service.retry(projectId, batchId, userId));
    }
}
