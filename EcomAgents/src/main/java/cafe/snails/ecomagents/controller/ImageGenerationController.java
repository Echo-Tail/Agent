package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.ImageExpressionCache;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.repository.ImageExpressionCacheRepository;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;

/** 图片历史、超分、分析和工作台辅助接口。图片生成统一使用 /v1/image-jobs。 */
@Slf4j @RestController @RequestMapping("/v1/images") @RequiredArgsConstructor
public class ImageGenerationController {
    private final ImageGenerationRecordService imageGenerationRecordService;
    private final ImageSuperResolutionService imageSuperResolutionService;
    private final ImageSuperResolutionJobService imageSuperResolutionJobService;
    private final ImageAnalysisService imageAnalysisService;
    private final BrightDataService brightDataService;
    private final ImageExpressionCacheRepository expressionCacheRepository;

    @PostMapping("/super-resolution")
    public ApiResponse<ImageSuperResolutionService.SuperResolutionResult> superResolution(@RequestBody ImageSuperResolutionService.SuperResolutionRequest request, @CurrentUserId Long userId) {
        return ApiResponse.success(imageSuperResolutionService.upscale(request, userId));
    }
    @PostMapping("/super-resolution/jobs")
    public ApiResponse<ImageSuperResolutionJobService.JobResponse> createSuperResolutionJob(@RequestBody ImageSuperResolutionJobService.CreateJobRequest request, @CurrentUserId Long userId) {
        return ApiResponse.success(imageSuperResolutionJobService.submit(request, userId));
    }
    @PostMapping("/super-resolution/jobs/upload")
    public ApiResponse<ImageSuperResolutionJobService.JobResponse> uploadSuperResolutionJob(@RequestParam("file") MultipartFile file, @RequestParam("upscaleFactor") Integer upscaleFactor, @RequestParam(value="origin", required=false) String origin, @CurrentUserId Long userId) {
        return ApiResponse.success(imageSuperResolutionJobService.submitUpload(file, upscaleFactor, origin, userId));
    }
    @PostMapping("/super-resolution/jobs/{id}/retry")
    public ApiResponse<ImageSuperResolutionJobService.JobResponse> retrySuperResolutionJob(@PathVariable("id") Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(imageSuperResolutionJobService.retry(id, userId));
    }
    @GetMapping("/super-resolution/jobs")
    public ApiResponse<List<ImageSuperResolutionJobService.JobResponse>> listSuperResolutionJobs(@RequestParam(value="origin", required=false) String origin, @CurrentUserId Long userId) {
        return ApiResponse.success(imageSuperResolutionJobService.list(userId, origin));
    }
    @GetMapping("/super-resolution/jobs/active-count")
    public ApiResponse<Long> countActiveSuperResolutionJobs(@CurrentUserId Long userId) { return ApiResponse.success(imageSuperResolutionJobService.activeCount(userId)); }
    @GetMapping("/super-resolution/sources")
    public ApiResponse<Page<ImageGenerationRecord>> listSuperResolutionSources(@CurrentUserId Long userId, @RequestParam(value="page", defaultValue="0") int page, @RequestParam(value="size", defaultValue="12") int size,
            @RequestParam(value="startDate", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value="endDate", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value="prompt", required=false) String prompt) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(imageSuperResolutionJobService.eligibleHistorySources(userId, startDate, endDate, prompt, pageable));
    }
    @GetMapping("/records")
    public ApiResponse<Page<ImageGenerationRecord>> listRecords(@CurrentUserId Long userId, @RequestParam(value="page", defaultValue="0") int page, @RequestParam(value="size", defaultValue="5") int size,
            @RequestParam(value="startDate", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value="endDate", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value="prompt", required=false) String prompt) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(imageGenerationRecordService.listRecords(userId, startDate, endDate, prompt, pageable));
    }
    @GetMapping("/records/{id}")
    public ApiResponse<ImageGenerationRecord> getRecord(@PathVariable("id") Long id, @CurrentUserId Long userId) { return ApiResponse.success(imageGenerationRecordService.getRecord(id, userId)); }
    @DeleteMapping("/records/{id}")
    public ApiResponse<Void> deleteRecord(@PathVariable("id") Long id, @CurrentUserId Long userId) { imageGenerationRecordService.deleteRecord(id, userId); return ApiResponse.success("删除成功", null); }
    @PostMapping("/analyze-expression")
    public ApiResponse<String> analyzeImageExpression(@RequestParam String imageUrl) { return ApiResponse.success("分析完成", imageAnalysisService.analyzeImageExpression(imageUrl)); }
    @PostMapping("/collect-asin-images")
    public ApiResponse<List<String>> collectAsinImages(@RequestParam String asin, @CurrentUserId Long userId) {
        List<String> urls = brightDataService.getImageUrlsByAsin(asin.trim(), userId);
        return ApiResponse.success("获取到 " + urls.size() + " 张图片", urls);
    }
    @PostMapping("/analyze-expression-cached")
    public ApiResponse<String> analyzeExpressionCached(@RequestParam String imageUrl) {
        String result = imageAnalysisService.analyzeImageExpression(imageUrl);
        String promptHash = md5(imageAnalysisService.getAnalysisPrompt());
        String imageUrlHash = md5(imageUrl);
        ImageExpressionCache record = ImageExpressionCache.builder().imageUrlHash(imageUrlHash).imageUrl(imageUrl).promptHash(promptHash).expressionJson(result).build();
        expressionCacheRepository.save(record);
        log.info("[表达分析] 已分析并持久化: imageUrlHash={}, promptHash={}, id={}", imageUrlHash, promptHash, record.getId());
        return ApiResponse.success("分析完成", result);
    }
    @PostMapping("/upload-local")
    public ApiResponse<String> uploadLocalImage(@RequestParam MultipartFile file, @CurrentUserId Long userId) {
        try {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            java.nio.file.Path targetDir = java.nio.file.Paths.get("uploads", "workbench");
            java.nio.file.Files.createDirectories(targetDir);
            java.nio.file.Path target = targetDir.resolve(fileName);
            file.transferTo(target.toFile());
            return ApiResponse.success("上传成功", "/uploads/workbench/" + fileName);
        } catch (Exception e) { return ApiResponse.error(500, "上传失败: " + e.getMessage()); }
    }
    private static String md5(String text) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException("MD5 error", e); }
    }
}
