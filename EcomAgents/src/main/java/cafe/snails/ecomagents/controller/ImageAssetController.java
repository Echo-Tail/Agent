package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.image.ImageSessionDtos.AssetResponse;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ImageAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 图片会话素材接口，负责会话内图片的上传、查询和删除。 */
@RestController
@RequiredArgsConstructor
public class ImageAssetController {
    private final ImageAssetService service;

    /** 向指定图片会话上传素材。 */
    @PostMapping("/v1/image-sessions/{sessionId}/assets")
    public ApiResponse<AssetResponse> upload(@PathVariable Long sessionId, @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "ORIGINAL") String type, @CurrentUserId Long userId) {
        return ApiResponse.success(service.upload(sessionId, file, type, userId));
    }
    /** 查询图片素材详情。 */
    @GetMapping("/v1/image-assets/{id}")
    public ApiResponse<AssetResponse> get(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.get(id, userId)); }
    /** 删除当前用户有权访问的图片素材。 */
    @DeleteMapping("/v1/image-assets/{id}")
    public ApiResponse<AssetResponse> delete(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.delete(id, userId)); }
}
