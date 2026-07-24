package cafe.snails.ecomagents.service.image.runtime.command;

import java.util.List;

public record ImageToImageCommand(Long userId, Long modelId, String prompt, String negativePrompt,
        int targetCount, String optionsJson, List<ImageInputSnapshotSource> inputs)
        implements ImageGenerationCommand {
    public ImageToImageCommand {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
