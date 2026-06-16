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

@RestController
@RequestMapping("/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping("/spaces")
    public ApiResponse<List<AssetSpace>> listSpaces() {
        return ApiResponse.success(assetService.listSpaces());
    }

    @PostMapping("/spaces")
    public ApiResponse<AssetSpace> createSpace(@RequestBody Map<String, String> body, @CurrentUserId Long userId) {
        return assetService.createSpace(body.get("name"), body.get("description"), userId);
    }

    @PutMapping("/spaces/{id}")
    public ApiResponse<AssetSpace> updateSpace(@PathVariable Long id, @RequestBody Map<String, String> body, @CurrentUserId Long userId) {
        return assetService.updateSpace(id, body.get("name"), body.get("description"), userId);
    }

    @DeleteMapping("/spaces/{id}")
    public ApiResponse<Void> deleteSpace(@PathVariable Long id, @CurrentUserId Long userId) {
        return assetService.deleteSpace(id, userId);
    }

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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PublicAsset> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "spaceId", required = false) Long spaceId,
            @CurrentUserId Long userId) {
        return assetService.uploadAsset(file, spaceId, userId);
    }

    @PutMapping("/{id}/move")
    public ApiResponse<PublicAsset> moveAsset(@PathVariable Long id, @RequestBody Map<String, Long> body, @CurrentUserId Long userId) {
        return assetService.moveAsset(id, body.get("spaceId"), userId);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long id, @CurrentUserId Long userId) {
        return assetService.deleteAsset(id, userId);
    }

    @PostMapping("/from-record/{recordId}")
    public ApiResponse<PublicAsset> importFromRecord(
            @PathVariable Long recordId,
            @RequestParam(value = "spaceId", required = false) Long spaceId,
            @CurrentUserId Long userId) {
        return assetService.importFromRecord(recordId, spaceId, userId);
    }
}
