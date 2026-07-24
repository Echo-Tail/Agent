package cafe.snails.ecomagents.service.image.runtime.command;

public record TextToImageCommand(Long userId, Long modelId, String prompt, String negativePrompt,
        int targetCount, String optionsJson) implements ImageGenerationCommand {
}
