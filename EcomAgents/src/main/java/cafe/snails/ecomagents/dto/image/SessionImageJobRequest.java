package cafe.snails.ecomagents.dto.image;

import cafe.snails.ecomagents.model.ImageSessionOperation;
import jakarta.validation.constraints.*;

/**
 * 在图片会话中创建生成任务的请求。
 */
public record SessionImageJobRequest(
        @NotNull Long modelId,
        @NotNull ImageSessionOperation operation,
        Long parentJobId,
        @NotBlank @Size(max = 10000) String prompt,
        @Size(max = 10000) String negativePrompt,
        @Min(1) @Max(10) Integer targetCount,
        String optionsJson) {}
