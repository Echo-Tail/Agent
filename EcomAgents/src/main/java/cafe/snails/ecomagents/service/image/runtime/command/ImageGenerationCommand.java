package cafe.snails.ecomagents.service.image.runtime.command;

public sealed interface ImageGenerationCommand permits TextToImageCommand, ImageToImageCommand {
    Long userId();
    Long modelId();
    String prompt();
    String negativePrompt();
    int targetCount();
    String optionsJson();
}
