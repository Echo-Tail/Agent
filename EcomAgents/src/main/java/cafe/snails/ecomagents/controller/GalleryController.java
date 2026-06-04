package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.GalleryItemResponse;
import cafe.snails.ecomagents.dto.GalleryPublishRequest;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 画廊控制器 — 精选作品的管理与浏览。
 * <p>所有已登录用户可查看画廊，发布/取消发布需校验用户身份，管理员下架需 admin 角色。</p>
 */
@RestController
@RequestMapping("/v1/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    /**
     * 发布作品到画廊。
     */
    @PostMapping("/items")
    public ApiResponse<GalleryItemResponse> publish(
            @RequestBody GalleryPublishRequest request,
            @CurrentUserId Long userId) {
        var item = galleryService.publish(request, userId);
        var response = galleryService.getDetail(item.getId());
        return ApiResponse.success("发布成功", response);
    }

    /**
     * 分页查询画廊作品列表。
     */
    @GetMapping("/items")
    public ApiResponse<Page<GalleryItemResponse>> listItems(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = galleryService.listPublished(pageable);
        return ApiResponse.success(result);
    }

    /**
     * 获取画廊作品详情。
     */
    @GetMapping("/items/{id}")
    public ApiResponse<GalleryItemResponse> getItem(@PathVariable("id") Long id) {
        var result = galleryService.getDetail(id);
        return ApiResponse.success(result);
    }

    /**
     * 创作者取消发布自己的作品。
     */
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> unpublish(@PathVariable("id") Long id, @CurrentUserId Long userId) {
        galleryService.unpublish(id, userId);
        return ApiResponse.success("已取消发布", null);
    }

    /**
     * 管理员下架作品。
     */
    @DeleteMapping("/items/{id}/admin")
    public ApiResponse<Void> adminRemove(@PathVariable("id") Long id) {
        galleryService.adminRemove(id);
        return ApiResponse.success("已下架", null);
    }

    /**
     * 获取当前用户可发布的历史记录（排除已发布的）。
     */
    @GetMapping("/my-records")
    public ApiResponse<List<ImageGenerationRecord>> getMyPublishableRecords(@CurrentUserId Long userId) {
        var records = galleryService.getPublishableRecords(userId);
        return ApiResponse.success(records);
    }
}
