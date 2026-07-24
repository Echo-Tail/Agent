package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.image.ImageSessionDtos.AssetResponse;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ImageAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ImageAssetController {
    private final ImageAssetService service;

    @PostMapping("/v1/image-sessions/{sessionId}/assets")
    public ApiResponse<AssetResponse> upload(@PathVariable Long sessionId, @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "ORIGINAL") String type, @CurrentUserId Long userId) {
        return ApiResponse.success(service.upload(sessionId, file, type, userId));
    }
    @GetMapping("/v1/image-assets/{id}")
    public ApiResponse<AssetResponse> get(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.get(id, userId)); }
    @DeleteMapping("/v1/image-assets/{id}")
    public ApiResponse<AssetResponse> delete(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.delete(id, userId)); }
}
