package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.service.TokenUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenerationUsageRecorder {
    private final TokenUsageService tokenUsageService;
    private final UserRepository users;

    public void record(ImageGenerationJob job) {
        try {
            boolean success = job.getStatus() == ImageGenerationJobStatus.SUCCEEDED
                    || job.getStatus() == ImageGenerationJobStatus.PARTIALLY_SUCCEEDED;
            String username = users.findById(job.getUserId()).map(User::getUsername).orElse(null);
            int units = Math.max(1, job.getSuccessCount() + job.getFailureCount());
            tokenUsageService.record(TokenUsageRecord.builder().modelId(job.getModelId())
                    .modelName(job.getRemoteModelName()).modelType("IMAGE").userId(job.getUserId())
                    .agentId(0L).agentName("图片生成").username(username)
                    .promptTokens(units).completionTokens(0).totalTokens(units)
                    .success(success).errorMessage(success ? null : job.getSafeErrorMessage()).build());
        } catch (Exception e) {
            log.warn("Failed to record image job usage {}: {}", job.getId(), e.getMessage());
        }
    }
}
