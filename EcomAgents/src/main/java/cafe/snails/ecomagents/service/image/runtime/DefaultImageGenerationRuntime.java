package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import cafe.snails.ecomagents.service.*;
import cafe.snails.ecomagents.service.image.runtime.command.*;
import cafe.snails.ecomagents.service.image.runtime.storage.ImageInputSnapshotStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultImageGenerationRuntime implements ImageGenerationRuntime {
    private final ImageGenerationJobRepository jobs;
    private final ImageGenerationJobInputRepository inputs;
    private final ImageGenerationRecordRepository records;
    private final ModelCapabilityResolver capabilityResolver;
    private final ImageInputSnapshotStorage snapshotStorage;

    @Override
    @Transactional
    public ImageGenerationJob submit(ImageGenerationCommand command) {
        validate(command);
        ModelCapability capability = command instanceof TextToImageCommand
                ? ModelCapability.TEXT_TO_IMAGE : ModelCapability.IMAGE_TO_IMAGE;
        ResolvedModelCapability resolved = capabilityResolver.resolve(command.modelId(), capability);
        ImageGenerationJob job = jobs.save(ImageGenerationJob.builder()
                .userId(command.userId()).modelId(command.modelId())
                .mode(command instanceof TextToImageCommand ? ImageGenerationMode.TEXT_TO_IMAGE : ImageGenerationMode.IMAGE_TO_IMAGE)
                .prompt(command.prompt().trim()).negativePrompt(trimToNull(command.negativePrompt()))
                .targetCount(command.targetCount()).optionsJson(trimToNull(command.optionsJson()))
                .provider(resolved.provider()).protocol(resolved.protocol())
                .remoteModelName(resolved.remoteModelName()).apiUrl(resolved.apiUrl())
                .capability(capability).credentialId(resolved.credentialId())
                .status(ImageGenerationJobStatus.PENDING).build());
        if (command instanceof ImageToImageCommand imageCommand) snapshotInputs(job.getId(), imageCommand.inputs());
        log.info("Image job submitted: jobId={}, userId={}, modelId={}, capability={}, targetCount={}, inputCount={}",
                job.getId(), job.getUserId(), job.getModelId(), job.getCapability(), job.getTargetCount(),
                command instanceof ImageToImageCommand imageCommand ? imageCommand.inputs().size() : 0);
        return job;
    }

    @Override @Transactional(readOnly = true)
    public ImageGenerationJob get(Long jobId, Long userId) { return owned(jobId, userId); }

    @Override @Transactional(readOnly = true)
    public List<ImageGenerationRecord> results(Long jobId, Long userId) {
        owned(jobId, userId);
        return records.findByJobIdOrderByOutputIndex(jobId);
    }

    @Override @Transactional
    public ImageGenerationJob cancel(Long jobId, Long userId) {
        ImageGenerationJob job = owned(jobId, userId);
        switch (job.getStatus()) {
            case PENDING -> job.setStatus(ImageGenerationJobStatus.CANCELLED);
            case RUNNING -> job.setStatus(ImageGenerationJobStatus.CANCEL_REQUESTED);
            case CANCEL_REQUESTED, CANCELLED -> { return job; }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成任务不能取消");
        }
        return jobs.save(job);
    }

    @Override @Transactional
    public ImageGenerationJob retry(Long jobId, Long userId) {
        ImageGenerationJob original = owned(jobId, userId);
        if (original.getStatus() != ImageGenerationJobStatus.FAILED &&
                original.getStatus() != ImageGenerationJobStatus.PARTIALLY_SUCCEEDED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有失败或部分成功任务可以重试");
        }
        ImageGenerationJob retried = jobs.save(ImageGenerationJob.builder()
                .userId(userId).modelId(original.getModelId()).retryOfJobId(original.getId())
                .mode(original.getMode()).prompt(original.getPrompt()).negativePrompt(original.getNegativePrompt())
                .targetCount(original.getTargetCount()).optionsJson(original.getOptionsJson())
                .provider(original.getProvider()).protocol(original.getProtocol())
                .remoteModelName(original.getRemoteModelName()).apiUrl(original.getApiUrl())
                .capability(original.getCapability()).credentialId(original.getCredentialId())
                .status(ImageGenerationJobStatus.PENDING).build());
        List<ImageGenerationJobInput> copies = inputs.findByJobIdOrderByInputIndex(original.getId()).stream()
                .map(input -> ImageGenerationJobInput.builder().jobId(retried.getId()).inputIndex(input.getInputIndex())
                        .role(input.getRole()).sourceType(input.getSourceType()).sourceId(input.getSourceId())
                        .snapshotPath(input.getSnapshotPath()).mimeType(input.getMimeType())
                        .fileSize(input.getFileSize()).sha256(input.getSha256()).build()).toList();
        inputs.saveAll(copies);
        log.info("Image job retry submitted: jobId={}, retryOfJobId={}, userId={}, modelId={}, capability={}, targetCount={}",
                retried.getId(), original.getId(), userId, retried.getModelId(), retried.getCapability(),
                retried.getTargetCount());
        return retried;
    }

    private void snapshotInputs(Long jobId, List<ImageInputSnapshotSource> sources) {
        for (int index = 0; index < sources.size(); index++) {
            ImageInputSnapshotSource source = sources.get(index);
            var stored = snapshotStorage.store(jobId, index, source);
            inputs.save(ImageGenerationJobInput.builder().jobId(jobId).inputIndex(index)
                    .role(source.role()).sourceType(source.sourceType()).sourceId(source.sourceId())
                    .snapshotPath(stored.path()).mimeType(stored.mimeType()).fileSize(stored.fileSize())
                    .sha256(stored.sha256()).build());
        }
    }

    private void validate(ImageGenerationCommand command) {
        if (command == null || command.userId() == null || command.modelId() == null) bad("缺少任务身份信息");
        if (command.prompt() == null || command.prompt().isBlank()) bad("图片描述不能为空");
        if (command.targetCount() < 1 || command.targetCount() > 10) bad("生成张数必须在 1~10 之间");
        if (command instanceof ImageToImageCommand image) {
            long references = image.inputs().stream().filter(input -> input.role() == ImageJobInputRole.REFERENCE).count();
            long masks = image.inputs().stream().filter(input -> input.role() == ImageJobInputRole.MASK).count();
            if (references < 1 || references > 4 || masks > 1 || references + masks != image.inputs().size())
                bad("图生图需要 1~4 张参考图，可额外提供 1 张遮罩图");
        }
    }
    private ImageGenerationJob owned(Long id, Long userId) {
        return jobs.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片生成任务不存在"));
    }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
