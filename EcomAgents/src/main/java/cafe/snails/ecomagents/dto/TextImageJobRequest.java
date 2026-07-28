package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.*;

/**
 * 创建文生图任务的请求参数。
 */
public record TextImageJobRequest(
        @NotNull Long modelId,
        @NotBlank @Size(max = 10000) String prompt,
        @Size(max = 10000) String negativePrompt,
        @Min(1) @Max(10) Integer targetCount,
        String optionsJson) {
}
