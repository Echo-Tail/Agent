package cafe.snails.ecomagents.service.image.runtime.command;

/** 图片生成运行时命令的统一定义。 */
public sealed interface ImageGenerationCommand permits TextToImageCommand, ImageToImageCommand {
    Long userId();
    Long modelId();
    String prompt();
    String negativePrompt();
    int targetCount();
    String optionsJson();
}
