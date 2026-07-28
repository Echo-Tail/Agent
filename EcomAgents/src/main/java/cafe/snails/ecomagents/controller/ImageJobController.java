package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.image.runtime.ImageGenerationRuntime;
import cafe.snails.ecomagents.service.image.runtime.command.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

/** 图片生成作业接口，负责提交、查询、取消及重试作业。 */
@RestController
@RequestMapping("/v1/image-jobs")
@RequiredArgsConstructor
public class ImageJobController {
    private final ImageGenerationRuntime runtime;

    /** 提交文生图作业。 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ImageJobResponse> submitText(@Valid @RequestBody TextImageJobRequest request,
            @CurrentUserId Long userId) {
        var command = new TextToImageCommand(userId, request.modelId(), request.prompt(), request.negativePrompt(),
                request.targetCount() == null ? 1 : request.targetCount(), request.optionsJson());
        return ApiResponse.success("图片任务已提交", ImageJobResponse.from(runtime.submit(command)));
    }

    /** 提交包含参考图片的图生图作业。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageJobResponse> submitImage(
            @RequestParam Long modelId, @RequestParam String prompt,
            @RequestParam(required = false) String negativePrompt,
            @RequestParam(defaultValue = "1") int targetCount,
            @RequestParam(required = false) String optionsJson,
            @RequestPart("images") List<MultipartFile> images,
            @RequestPart(value = "mask", required = false) MultipartFile mask,
            @CurrentUserId Long userId) throws IOException {
        List<ImageInputSnapshotSource> sources = new ArrayList<>();
        for (MultipartFile image : images) sources.add(source(image, ImageJobInputRole.REFERENCE));
        if (mask != null && !mask.isEmpty()) sources.add(source(mask, ImageJobInputRole.MASK));
        var command = new ImageToImageCommand(userId, modelId, prompt, negativePrompt, targetCount, optionsJson, sources);
        return ApiResponse.success("图片任务已提交", ImageJobResponse.from(runtime.submit(command)));
    }

    /** 查询指定图片作业状态。 */
    @GetMapping("/{id}")
    public ApiResponse<ImageJobResponse> get(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(ImageJobResponse.from(runtime.get(id, userId)));
    }

    /** 查询指定作业的生成结果。 */
    @GetMapping("/{id}/results")
    public ApiResponse<List<ImageGenerationRecord>> results(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(runtime.results(id, userId));
    }

    /** 取消尚未完成的图片作业。 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<ImageJobResponse> cancel(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(ImageJobResponse.from(runtime.cancel(id, userId)));
    }

    /** 重试失败或已终止的图片作业。 */
    @PostMapping("/{id}/retry")
    public ApiResponse<ImageJobResponse> retry(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(ImageJobResponse.from(runtime.retry(id, userId)));
    }

    private ImageInputSnapshotSource source(MultipartFile file, ImageJobInputRole role) throws IOException {
        return new ImageInputSnapshotSource(role, ImageJobInputSourceType.UPLOAD, null,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }
}
