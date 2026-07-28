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

/** 商品档案接口，覆盖档案、版本、卖点认知、视觉策略和商品图片管理。 */
@RestController
@RequestMapping("/v1/product-profiles")
@RequiredArgsConstructor
public class ProductProfileController {

    private final ProductProfileService productProfileService;
    private final SellingPointCognitionService sellingPointCognitionService;
    private final VisualStrategyService visualStrategyService;

    /** 分页查询当前用户的商品档案。 */
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

    /** 创建商品档案并解析上传的原始资料。 */
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

    /** 查询商品档案详情。 */
    @GetMapping("/{id}")
    public ApiResponse<ProductProfile> get(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(productProfileService.get(id, userId));
    }

    /** 更新商品事实信息。 */
    @PutMapping("/{id}/facts")
    public ApiResponse<ProductProfile> updateFacts(
            @PathVariable Long id,
            @RequestBody String productFactsJson,
            @CurrentUserId Long userId) {
        return ApiResponse.success("产品事实已保存", productProfileService.updateFacts(id, productFactsJson, userId));
    }

    /** 确认商品档案内容。 */
    @PostMapping("/{id}/confirm")
    public ApiResponse<ProductProfile> confirm(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success("产品资料已确认", productProfileService.confirm(id, userId));
    }

    /** 重新解析商品原始资料。 */
    @PostMapping("/{id}/reparse")
    public ApiResponse<ProductProfile> reparse(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success("重新解析完成", productProfileService.reparse(id, userId));
    }

    /** 基于当前档案创建新版本。 */
    @PostMapping("/{id}/versions")
    public ApiResponse<ProductProfile> createNewVersion(
            @PathVariable Long id,
            @RequestParam String markdownContent,
            @CurrentUserId Long userId) {
        return ApiResponse.success("新版本已创建", productProfileService.createNewVersion(id, markdownContent, userId));
    }

    /** 查询商品档案的历史版本。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<ProductProfileVersion>> getVersions(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(productProfileService.getVersions(id, userId));
    }

    /** 查询指定档案版本详情。 */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<ProductProfileVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.success(productProfileService.getVersion(versionId));
    }

    /** 调用模型生成商品卖点认知。 */
    @PostMapping("/{id}/selling-point-cognitions/generate")
    public ApiResponse<ProductSellingPointCognitionVersion> generateSellingPointCognitions(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success("卖点认知草稿已生成", sellingPointCognitionService.generate(id, userId));
    }

    /** 查询当前生效的卖点认知版本。 */
    @GetMapping("/{id}/selling-point-cognitions/current")
    public ApiResponse<ProductSellingPointCognitionVersion> getCurrentSellingPointCognition(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(sellingPointCognitionService.getCurrent(id, userId));
    }

    /** 查询卖点认知历史版本。 */
    @GetMapping("/{id}/selling-point-cognitions/versions")
    public ApiResponse<List<ProductSellingPointCognitionVersion>> getSellingPointCognitionVersions(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(sellingPointCognitionService.listVersions(id, userId));
    }

    /** 更新指定卖点认知版本。 */
    @PutMapping("/{id}/selling-point-cognitions/{versionId}")
    public ApiResponse<ProductSellingPointCognitionVersion> updateSellingPointCognition(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestBody String cognitionJson,
            @CurrentUserId Long userId) {
        return ApiResponse.success("卖点认知草稿已保存", sellingPointCognitionService.update(id, versionId, cognitionJson, userId));
    }

    /** 确认并启用指定卖点认知版本。 */
    @PostMapping("/{id}/selling-point-cognitions/{versionId}/confirm")
    public ApiResponse<ProductSellingPointCognitionVersion> confirmSellingPointCognition(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @CurrentUserId Long userId) {
        return ApiResponse.success("卖点认知版本已确认", sellingPointCognitionService.confirm(id, versionId, userId));
    }

    /** 基于商品档案生成视觉策略。 */
    @PostMapping("/{id}/visual-strategies/generate")
    public ApiResponse<ProductVisualStrategyVersion> generateVisualStrategy(
            @PathVariable Long id,
            @RequestBody(required = false) VisualStrategyGenerateRequest request,
            @CurrentUserId Long userId) {
        Long cognitionVersionId = request != null ? request.cognitionVersionId() : null;
        List<String> contentScope = request != null ? request.contentScope() : null;
        return ApiResponse.success("视觉策略草稿已生成", visualStrategyService.generate(id, cognitionVersionId, contentScope, userId));
    }

    /** 查询当前生效的视觉策略。 */
    @GetMapping("/{id}/visual-strategies/current")
    public ApiResponse<ProductVisualStrategyVersion> getCurrentVisualStrategy(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(visualStrategyService.getCurrent(id, userId));
    }

    /** 查询视觉策略历史版本。 */
    @GetMapping("/{id}/visual-strategies/versions")
    public ApiResponse<List<ProductVisualStrategyVersion>> getVisualStrategyVersions(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        return ApiResponse.success(visualStrategyService.listVersions(id, userId));
    }

    /** 更新指定视觉策略版本。 */
    @PutMapping("/{id}/visual-strategies/{versionId}")
    public ApiResponse<ProductVisualStrategyVersion> updateVisualStrategy(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestBody String strategyJson,
            @CurrentUserId Long userId) {
        return ApiResponse.success("视觉策略草稿已保存", visualStrategyService.update(id, versionId, strategyJson, userId));
    }

    /** 确认并启用指定视觉策略版本。 */
    @PostMapping("/{id}/visual-strategies/{versionId}/confirm")
    public ApiResponse<ProductVisualStrategyVersion> confirmVisualStrategy(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @CurrentUserId Long userId) {
        return ApiResponse.success("视觉策略版本已确认", visualStrategyService.confirm(id, versionId, userId));
    }

    /** 查询商品档案关联的图片。 */
    @GetMapping("/{id}/images")
    public ApiResponse<List<ProductProfileImage>> getImages(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(productProfileService.getImages(id, userId));
    }

    /** 向商品档案上传图片。 */
    @PostMapping("/{id}/images")
    public ApiResponse<ProductProfileImage> uploadImage(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "other") String tag,
            @CurrentUserId Long userId) {
        return ApiResponse.success("图片已上传", productProfileService.uploadImage(id, file, tag, userId));
    }

    /** 删除商品档案图片。 */
    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> deleteImage(@PathVariable Long imageId, @CurrentUserId Long userId) {
        productProfileService.deleteImage(imageId, userId);
        return ApiResponse.success("图片已删除", null);
    }

    /** 删除商品档案。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        productProfileService.delete(id, userId);
        return ApiResponse.success("产品资料已删除", null);
    }
}
