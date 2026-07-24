package cafe.snails.ecomagents.dto.image;

import cafe.snails.ecomagents.model.ImageSessionOperation;
import jakarta.validation.constraints.*;

public record SessionImageJobRequest(
        @NotNull Long modelId,
        @NotNull ImageSessionOperation operation,
        Long parentJobId,
        @NotBlank @Size(max = 10000) String prompt,
        @Size(max = 10000) String negativePrompt,
        @Min(1) @Max(10) Integer targetCount,
        String optionsJson) {}
