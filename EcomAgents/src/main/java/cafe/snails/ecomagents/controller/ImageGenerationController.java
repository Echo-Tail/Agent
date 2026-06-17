package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ImageGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 图片生成控制器 — 文生图、图生图、历史记录。
 * <p>独立于 Agent 体系，所有用户可用。</p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/images")
@RequiredArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    /**
     * 文生图：根据文字描述生成图片。
     */
    @PostMapping("/generate")
    public ApiResponse<ImageGenerationService.ImageGenerationResult> generate(
            @RequestBody Map<String, String> body,
            @CurrentUserId Long userId) {
        String prompt = body.get("prompt");
        String size = body.get("size");
        String quality = body.get("quality");
        int n = parseIntOrDefault(body.get("n"), 1);
        Long modelId = body.get("modelId") != null ? Long.parseLong(body.get("modelId")) : null;
        log.info("[文生图] userId={} prompt=\"{}\" size={} quality={} n={} modelId={}",
                userId, truncate(prompt, 120), size, quality, n, modelId);
        var result = imageGenerationService.generate(prompt, size, quality, n, userId, modelId);
        log.info("[文生图完成] userId={} timeCost={}ms recordId={} failedCount={}",
                userId, result.timeCostMs(), result.recordId(), result.failedCount());
        return ApiResponse.success(result);
    }

    /**
     * 图生图：上传参考图片进行编辑。
     */
    @PostMapping("/edit")
    public ApiResponse<ImageGenerationService.ImageGenerationResult> edit(
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "quality", required = false) String quality,
            @RequestParam("image") List<MultipartFile> images,
            @RequestParam(value = "mask", required = false) MultipartFile mask,
            @RequestParam(value = "n", required = false, defaultValue = "1") int n,
            @RequestParam(value = "modelId", required = false) Long modelId,
            @CurrentUserId Long userId) {
        log.info("[图生图] userId={} prompt=\"{}\" size={} quality={} images={} mask={} n={} modelId={}",
                userId, truncate(prompt, 120), size, quality,
                images != null ? images.size() : 0,
                mask != null ? mask.getOriginalFilename() : "null",
                n, modelId);
        var result = imageGenerationService.edit(prompt, size, quality, images, mask, n, userId, modelId);
        log.info("[图生图完成] userId={} timeCost={}ms recordId={} failedCount={}",
                userId, result.timeCostMs(), result.recordId(), result.failedCount());
        return ApiResponse.success(result);
    }

    /**
     * 安全解析整数，解析失败返回默认值。
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 截断长字符串（含省略号），防止日志输出过长。 */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /**
     * 分页查询当前用户的图片生成历史记录，支持日期范围和 prompt 模糊匹配。
     */
    @GetMapping("/records")
    public ApiResponse<Page<ImageGenerationRecord>> listRecords(
            @CurrentUserId Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "5") int size,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "prompt", required = false) String prompt) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = imageGenerationService.listRecords(userId, startDate, endDate, prompt, pageable);
        return ApiResponse.success(result);
    }

    /**
     * 删除一条历史记录。
     */
    @DeleteMapping("/records/{id}")
    public ApiResponse<Void> deleteRecord(@PathVariable("id") Long id, @CurrentUserId Long userId) {
        imageGenerationService.deleteRecord(id, userId);
        return ApiResponse.success("删除成功", null);
    }
}
