package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.*;
import java.time.LocalDateTime;

public record ImageJobResponse(Long id, Long modelId, Long retryOfJobId, ImageGenerationMode mode,
        String prompt, String negativePrompt, Integer targetCount, String provider, ModelProtocol protocol,
        String remoteModelName, ModelCapability capability, ImageGenerationJobStatus status,
        ImageGenerationExecutionPhase executionPhase, Integer successCount, Integer failureCount,
        String errorCode, String safeErrorMessage, Boolean retryable, LocalDateTime createdAt,
        LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime updatedAt) {
    public static ImageJobResponse from(ImageGenerationJob job) {
        return new ImageJobResponse(job.getId(), job.getModelId(), job.getRetryOfJobId(), job.getMode(),
                job.getPrompt(), job.getNegativePrompt(), job.getTargetCount(), job.getProvider(), job.getProtocol(),
                job.getRemoteModelName(), job.getCapability(), job.getStatus(), job.getExecutionPhase(),
                job.getSuccessCount(), job.getFailureCount(), job.getErrorCode(), job.getSafeErrorMessage(),
                job.getRetryable(), job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(), job.getUpdatedAt());
    }
}
