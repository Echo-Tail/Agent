package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ImageJobResponse;
import cafe.snails.ecomagents.dto.image.SessionImageJobRequest;
import cafe.snails.ecomagents.dto.image.SessionImageJobResponse;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import cafe.snails.ecomagents.service.image.runtime.ImageGenerationRuntime;
import cafe.snails.ecomagents.service.image.runtime.command.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImageSessionJobService {
    private final ImageSessionRepository sessionRepository;
    private final ImageSessionJobRepository linkRepository;
    private final ImageGenerationJobRepository jobRepository;
    private final AiModelRepository modelRepository;
    private final AiModelCapabilityRepository capabilityRepository;
    private final ImageGenerationRuntime runtime;

    @Transactional
    public ImageJobResponse submitText(Long sessionId, SessionImageJobRequest request, String idempotencyKey, Long userId) {
        requireSession(sessionId, userId);
        validateOperation(request.operation(), false);
        validateGptImage2Model(request.modelId(), ModelCapability.TEXT_TO_IMAGE);
        var existing = existing(sessionId, idempotencyKey, userId);
        if (existing != null) return existing;
        var job = runtime.submit(new TextToImageCommand(userId, request.modelId(), request.prompt(), request.negativePrompt(),
                request.targetCount() == null ? 1 : request.targetCount(), request.optionsJson()));
        link(sessionId, job.getId(), request.operation(), request.parentJobId(), idempotencyKey);
        return ImageJobResponse.from(job);
    }

    @Transactional
    public ImageJobResponse submitImage(Long sessionId, Long modelId, ImageSessionOperation operation, Long parentJobId,
            String prompt, String negativePrompt, int targetCount, String optionsJson, List<MultipartFile> images,
            MultipartFile mask, String idempotencyKey, Long userId) throws IOException {
        requireSession(sessionId, userId);
        validateOperation(operation, true);
        validateGptImage2Model(modelId, ModelCapability.IMAGE_TO_IMAGE);
        var existing = existing(sessionId, idempotencyKey, userId);
        if (existing != null) return existing;
        List<ImageInputSnapshotSource> sources = new ArrayList<>();
        for (MultipartFile image : images) sources.add(source(image, ImageJobInputRole.REFERENCE));
        if (mask != null && !mask.isEmpty()) sources.add(source(mask, ImageJobInputRole.MASK));
        var job = runtime.submit(new ImageToImageCommand(userId, modelId, prompt, negativePrompt, targetCount, optionsJson, sources));
        link(sessionId, job.getId(), operation, parentJobId, idempotencyKey);
        return ImageJobResponse.from(job);
    }

    public List<SessionImageJobResponse> list(Long sessionId, Long userId) {
        requireSession(sessionId, userId);
        return linkRepository.findBySessionIdOrderByCreatedAt(sessionId).stream()
                .map(link -> jobRepository.findById(link.getJobId()).map(job -> new SessionImageJobResponse(
                        sessionId, link.getOperation(), link.getParentJobId(), link.getCreatedAt(), ImageJobResponse.from(job))).orElse(null))
                .filter(Objects::nonNull).toList();
    }

    private ImageJobResponse existing(Long sessionId, String key, Long userId) {
        if (key == null || key.isBlank() || key.length() > 100) throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少有效的 Idempotency-Key");
        return linkRepository.findBySessionIdAndIdempotencyKey(sessionId, key).map(link ->
                jobRepository.findByIdAndUserId(link.getJobId(), userId).map(ImageJobResponse::from)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片任务不存在"))).orElse(null);
    }

    private void link(Long sessionId, Long jobId, ImageSessionOperation operation, Long parentJobId, String key) {
        if (parentJobId != null && !linkRepository.existsBySessionIdAndJobId(sessionId, parentJobId))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "父任务不属于当前会话");
        linkRepository.save(ImageSessionJob.builder().sessionId(sessionId).jobId(jobId).operation(operation)
                .parentJobId(parentJobId).idempotencyKey(key).createdAt(LocalDateTime.now()).build());
    }

    private void validateOperation(ImageSessionOperation operation, boolean hasImages) {
        if (operation == null || operation == ImageSessionOperation.UPSCALE)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前任务接口不支持该操作");
        if (hasImages && operation == ImageSessionOperation.GENERATE)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "带参考图任务必须使用 VARIATION 或 INPAINT");
        if (!hasImages && operation != ImageSessionOperation.GENERATE)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无参考图任务必须使用 GENERATE");
    }
    private void validateGptImage2Model(Long modelId, ModelCapability capability) {
        var model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "图片模型不存在"));
        if (!Boolean.TRUE.equals(model.getEnabled()) || !"gpt-image-2".equalsIgnoreCase(model.getModelName().trim()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新图像工作台仅支持 gpt-image-2 模型");
        var configured = capabilityRepository.findByModelIdAndCapability(modelId, capability)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "该 gpt-image-2 中转站未配置所需能力"));
        if (configured.getProtocol() != ModelProtocol.OPENAI_IMAGE)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "gpt-image-2 必须使用 OPENAI_IMAGE 协议");
    }
    private void requireSession(Long id, Long userId) {
        if (sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId).isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "图像会话不存在");
    }
    private ImageInputSnapshotSource source(MultipartFile file, ImageJobInputRole role) throws IOException {
        return new ImageInputSnapshotSource(role, ImageJobInputSourceType.UPLOAD, null, file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }
}
