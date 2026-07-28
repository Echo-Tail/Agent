package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.AssetSpace;
import cafe.snails.ecomagents.model.PublicAsset;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 公共素材库接口，管理素材空间、素材上传、移动、导入和删除。 */
@RestController
@RequestMapping("/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /** 查询全部素材空间。 */
    @GetMapping("/spaces")
    public ApiResponse<List<AssetSpace>> listSpaces() {
        return ApiResponse.success(assetService.listSpaces());
    }

    /** 创建素材空间。 */
    @PostMapping("/spaces")
    public ApiResponse<AssetSpace> createSpace(@RequestBody Map<String, String> body, @CurrentUserId Long userId) {
        return assetService.createSpace(body.get("name"), body.get("description"), userId);
    }

    /** 更新素材空间信息。 */
    @PutMapping("/spaces/{id}")
    public ApiResponse<AssetSpace> updateSpace(@PathVariable Long id, @RequestBody Map<String, String> body, @CurrentUserId Long userId) {
        return assetService.updateSpace(id, body.get("name"), body.get("description"), userId);
    }

    /** 删除素材空间。 */
    @DeleteMapping("/spaces/{id}")
    public ApiResponse<Void> deleteSpace(@PathVariable Long id, @CurrentUserId Long userId) {
        return assetService.deleteSpace(id, userId);
    }

    /** 分页查询指定空间内的素材。 */
    @GetMapping
    public ApiResponse<Page<PublicAsset>> listAssets(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long uploadedBy,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size);
        return ApiResponse.success(assetService.listAssets(spaceId, keyword, uploadedBy, startDate, endDate, pageable));
    }

    /** 上传文件并创建素材记录。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PublicAsset> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "spaceId", required = false) Long spaceId,
            @CurrentUserId Long userId) {
        return assetService.uploadAsset(file, spaceId, userId);
    }

    /** 将素材移动到目标空间。 */
    @PutMapping("/{id}/move")
    public ApiResponse<PublicAsset> moveAsset(@PathVariable Long id, @RequestBody Map<String, Long> body, @CurrentUserId Long userId) {
        return assetService.moveAsset(id, body.get("spaceId"), userId);
    }

    /** 删除指定素材。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long id, @CurrentUserId Long userId) {
        return assetService.deleteAsset(id, userId);
    }

    /** 从图片生成记录导入素材。 */
    @PostMapping("/from-record/{recordId}")
    public ApiResponse<PublicAsset> importFromRecord(
            @PathVariable Long recordId,
            @RequestParam(value = "spaceId", required = false) Long spaceId,
            @CurrentUserId Long userId) {
        return assetService.importFromRecord(recordId, spaceId, userId);
    }
}
