package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.VisualStrategyGenerateRequest;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.model.ProductProfileImage;
import cafe.snails.ecomagents.model.ProductProfileVersion;
import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import cafe.snails.ecomagents.model.ProductVisualStrategyVersion;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ProductProfileService;
import cafe.snails.ecomagents.service.SellingPointCognitionService;
import cafe.snails.ecomagents.service.VisualStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/product-profiles")
@RequiredArgsConstructor
public class ProductProfileController {

    private final ProductProfileService productProfileService;
    private final SellingPointCognitionService sellingPointCognitionService;
    private final VisualStrategyService visualStrategyService;

    @GetMapping
    public ApiResponse<Page<ProductProfile>> list(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ApiResponse.success(productProfileService.list(userId, status, keyword, pageable));
    }

    @PostMapping
    public ApiResponse<ProductProfile> create(
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String markdownContent,
            @RequestParam(required = false) String asin,
            @RequestParam(required = false) MultipartFile file,
            @CurrentUserId Long userId) {
        if (file != null && !file.isEmpty()) {
            return ApiResponse.success("产品资料已创建，正在进行解析", productProfileService.createFromMarkdownFile(file, userId));
        }
        if (asin != null && !asin.isBlank()) {
            return ApiResponse.success("产品资料已创建，正在通过 Bright Data 采集信息", productProfileService.createFromAsin(asin.trim(), userId));
        }
        if (markdownContent != null && !markdownContent.isBlank()) {
            return ApiResponse.success("产品资料已创建，正在进行解析", productProfileService.createFromMarkdown(productName, markdownContent, userId));
        }
        return ApiResponse.error(400, "请提供 Markdown 文件、ASIN 或 Markdown 内容");
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductProfile> get(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(productProfileService.get(id, userId));
    }

    @PutMapping("/{id}/facts")
    public ApiResponse<ProductProfile> updateFacts(
            @PathVariable Long id,
            @RequestBody String productFactsJson,
            @CurrentUserId Long userId) {
        return ApiResponse.success("产品事实已保存", productProfileService.updateFacts(id, productFactsJson, userId));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ProductProfile> confirm(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success("产品资料已确认", productProfileService.confirm(id, userId));
    }

    @PostMapping("/{id}/reparse")
    public ApiResponse<ProductProfile> reparse(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success("重新解析完成", productProfileService.reparse(id, userId));
    }

    @PostMapping("/{id}/versions")
    public ApiResponse<ProductProfile> createNewVersion(
            @PathVariable Long id,
            @RequestParam String markdownContent,
            @CurrentUserId Long userId) {
        return ApiResponse.success("新版本已创建", productProfileService.createNewVersion(id, markdownContent, userId));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<ProductProfileVersion>> getVersions(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(productProfileService.getVersions(id, userId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<ProductProfileVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.success(productProfileService.getVersion(versionId));
    }

    @PostMapping("/{id}/selling-point-cognitions/generate")
    public ApiResponse<ProductSellingPointCognitionVersion> generateSellingPointCognitions(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success("卖点认知草稿已生成", sellingPointCognitionService.generate(id, userId));
    }

    @GetMapping("/{id}/selling-point-cognitions/current")
    public ApiResponse<ProductSellingPointCognitionVersion> getCurrentSellingPointCognition(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(sellingPointCognitionService.getCurrent(id, userId));
    }

    @GetMapping("/{id}/selling-point-cognitions/versions")
    public ApiResponse<List<ProductSellingPointCognitionVersion>> getSellingPointCognitionVersions(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(sellingPointCognitionService.listVersions(id, userId));
    }

    @PutMapping("/{id}/selling-point-cognitions/{versionId}")
    public ApiResponse<ProductSellingPointCognitionVersion> updateSellingPointCognition(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestBody String cognitionJson,
            @CurrentUserId Long userId) {
        return ApiResponse.success("卖点认知草稿已保存", sellingPointCognitionService.update(id, versionId, cognitionJson, userId));
    }

    @PostMapping("/{id}/selling-point-cognitions/{versionId}/confirm")
    public ApiResponse<ProductSellingPointCognitionVersion> confirmSellingPointCognition(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @CurrentUserId Long userId) {
        return ApiResponse.success("卖点认知版本已确认", sellingPointCognitionService.confirm(id, versionId, userId));
    }

    @PostMapping("/{id}/visual-strategies/generate")
    public ApiResponse<ProductVisualStrategyVersion> generateVisualStrategy(
            @PathVariable Long id,
            @RequestBody(required = false) VisualStrategyGenerateRequest request,
            @CurrentUserId Long userId) {
        Long cognitionVersionId = request != null ? request.cognitionVersionId() : null;
        List<String> contentScope = request != null ? request.contentScope() : null;
        return ApiResponse.success("视觉策略草稿已生成", visualStrategyService.generate(id, cognitionVersionId, contentScope, userId));
    }

    @GetMapping("/{id}/visual-strategies/current")
    public ApiResponse<ProductVisualStrategyVersion> getCurrentVisualStrategy(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(visualStrategyService.getCurrent(id, userId));
    }

    @GetMapping("/{id}/visual-strategies/versions")
    public ApiResponse<List<ProductVisualStrategyVersion>> getVisualStrategyVersions(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(visualStrategyService.listVersions(id, userId));
    }

    @PutMapping("/{id}/visual-strategies/{versionId}")
    public ApiResponse<ProductVisualStrategyVersion> updateVisualStrategy(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestBody String strategyJson,
            @CurrentUserId Long userId) {
        return ApiResponse.success("视觉策略草稿已保存", visualStrategyService.update(id, versionId, strategyJson, userId));
    }

    @PostMapping("/{id}/visual-strategies/{versionId}/confirm")
    public ApiResponse<ProductVisualStrategyVersion> confirmVisualStrategy(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @CurrentUserId Long userId) {
        return ApiResponse.success("视觉策略版本已确认", visualStrategyService.confirm(id, versionId, userId));
    }

    @GetMapping("/{id}/images")
    public ApiResponse<List<ProductProfileImage>> getImages(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(productProfileService.getImages(id, userId));
    }

    @PostMapping("/{id}/images")
    public ApiResponse<ProductProfileImage> uploadImage(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "other") String tag,
            @CurrentUserId Long userId) {
        return ApiResponse.success("图片已上传", productProfileService.uploadImage(id, file, tag, userId));
    }

    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> deleteImage(@PathVariable Long imageId, @CurrentUserId Long userId) {
        productProfileService.deleteImage(imageId, userId);
        return ApiResponse.success("图片已删除", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        productProfileService.delete(id, userId);
        return ApiResponse.success("产品资料已删除", null);
    }
}
