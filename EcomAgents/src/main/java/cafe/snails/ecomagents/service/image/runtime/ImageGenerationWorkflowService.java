package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.service.image.runtime.command.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/** 为需要有限等待的业务流程封装统一图片 Job 运行时。 */
@Service
@RequiredArgsConstructor
public class ImageGenerationWorkflowService {
    private static final Set<ImageGenerationJobStatus> TERMINAL = EnumSet.of(
            ImageGenerationJobStatus.SUCCEEDED, ImageGenerationJobStatus.PARTIALLY_SUCCEEDED,
            ImageGenerationJobStatus.FAILED, ImageGenerationJobStatus.CANCELLED);

    private final ImageGenerationRuntime runtime;
    private final AiModelRepository modelRepository;
    private final ObjectMapper objectMapper;

    public ImageGenerationJob submitText(Long userId, Long modelId, String prompt, String size,
            String quality, int count, String negativePrompt) {
        Long resolvedModelId = resolveModelId(modelId, ModelCapability.TEXT_TO_IMAGE);
        return runtime.submit(new TextToImageCommand(userId, resolvedModelId, prompt, negativePrompt,
                count, options(size, quality)));
    }

    public ImageGenerationJob submitImage(Long userId, Long modelId, String prompt, String size,
            String quality, int count, String negativePrompt, List<MultipartFile> images, MultipartFile mask)
            throws IOException {
        Long resolvedModelId = resolveModelId(modelId, ModelCapability.IMAGE_TO_IMAGE);
        List<ImageInputSnapshotSource> sources = new ArrayList<>();
        for (MultipartFile image : images) sources.add(source(image, ImageJobInputRole.REFERENCE));
        if (mask != null && !mask.isEmpty()) sources.add(source(mask, ImageJobInputRole.MASK));
        return runtime.submit(new ImageToImageCommand(userId, resolvedModelId, prompt, negativePrompt,
                count, options(size, quality), sources));
    }

    public AwaitResult await(Long jobId, Long userId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            ImageGenerationJob job = runtime.get(jobId, userId);
            if (TERMINAL.contains(job.getStatus())) return new AwaitResult(job, runtime.results(jobId, userId), true);
            if (System.nanoTime() >= deadline) return new AwaitResult(job, List.of(), false);
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new AwaitResult(job, List.of(), false);
            }
        }
    }

    public GenerationResult result(AwaitResult awaited) {
        List<ImageGenerationRecord> records = awaited.successfulRecords();
        List<String> urls = records.stream().map(ImageGenerationRecord::getResultPathNormalized).toList();
        List<GeneratedImage> images = records.stream().map(record -> new GeneratedImage(record.getId(),
                record.getResultPathNormalized(), record.getWidth(), record.getHeight())).toList();
        long elapsed = awaited.job().getStartedAt() != null && awaited.job().getCompletedAt() != null
                ? Duration.between(awaited.job().getStartedAt(), awaited.job().getCompletedAt()).toMillis() : 0L;
        return new GenerationResult(urls,
                records.stream().map(ImageGenerationRecord::getRevisedPrompt).filter(Objects::nonNull).findFirst().orElse(null),
                elapsed, records.isEmpty() ? 0L : records.get(0).getId(), awaited.job().getFailureCount(), images);
    }

    private Long resolveModelId(Long modelId, ModelCapability capability) {
        if (modelId != null) return modelId;
        return modelRepository.findEnabledByCapability(capability).stream().findFirst().map(AiModel::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "未配置支持该图片能力的模型"));
    }

    private String options(String size, String quality) {
        var node = objectMapper.createObjectNode();
        if (size != null && !size.isBlank()) node.put("size", size);
        if (quality != null && !quality.isBlank()) node.put("quality", quality);
        return node.toString();
    }

    private ImageInputSnapshotSource source(MultipartFile file, ImageJobInputRole role) throws IOException {
        return new ImageInputSnapshotSource(role, ImageJobInputSourceType.UPLOAD, null,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    public record GeneratedImage(Long recordId, String url, Integer width, Integer height) {}
    public record GenerationResult(List<String> urls, String revisedPrompt, Long timeCostMs, Long recordId,
            int failedCount, List<GeneratedImage> images) {}
    public record AwaitResult(ImageGenerationJob job, List<ImageGenerationRecord> records, boolean completed) {
        public List<ImageGenerationRecord> successfulRecords() {
            return records.stream().filter(record -> "SUCCEEDED".equals(record.getStatus())
                    && record.getResultPath() != null && !record.getResultPath().isBlank()).toList();
        }
    }
}
