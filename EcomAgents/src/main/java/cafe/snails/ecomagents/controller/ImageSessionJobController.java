package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.dto.image.SessionImageJobRequest;
import cafe.snails.ecomagents.dto.image.SessionImageJobResponse;
import cafe.snails.ecomagents.model.ImageSessionOperation;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ImageSessionJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

/** 图片会话作业接口，负责在指定会话中提交和查询生成任务。 */
@RestController
@RequestMapping("/v1/image-sessions/{sessionId}/jobs")
@RequiredArgsConstructor
public class ImageSessionJobController {
    private final ImageSessionJobService service;

    /** 在会话中提交文生图作业。 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ImageJobResponse> submitText(@PathVariable Long sessionId, @Valid @RequestBody SessionImageJobRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @CurrentUserId Long userId) {
        return ApiResponse.success("图片任务已提交", service.submitText(sessionId, request, idempotencyKey, userId));
    }
    /** 在会话中提交带参考图的图生图作业。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageJobResponse> submitImage(@PathVariable Long sessionId, @RequestParam Long modelId,
            @RequestParam ImageSessionOperation operation, @RequestParam(required = false) Long parentJobId,
            @RequestParam String prompt, @RequestParam(required = false) String negativePrompt,
            @RequestParam(defaultValue = "1") int targetCount, @RequestParam(required = false) String optionsJson,
            @RequestPart("images") List<MultipartFile> images, @RequestPart(value = "mask", required = false) MultipartFile mask,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @CurrentUserId Long userId) throws IOException {
        return ApiResponse.success("图片任务已提交", service.submitImage(sessionId, modelId, operation, parentJobId, prompt,
                negativePrompt, targetCount, optionsJson, images, mask, idempotencyKey, userId));
    }
    /** 查询会话中的全部图片生成作业。 */
    @GetMapping
    public ApiResponse<List<SessionImageJobResponse>> list(@PathVariable Long sessionId, @CurrentUserId Long userId) {
        return ApiResponse.success(service.list(sessionId, userId));
    }
}
