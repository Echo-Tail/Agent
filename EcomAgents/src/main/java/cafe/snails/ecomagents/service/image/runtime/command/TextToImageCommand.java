package cafe.snails.ecomagents.service.image.runtime.command;

/** 提交文生图任务所需的运行时命令。 */
public record TextToImageCommand(Long userId, Long modelId, String prompt, String negativePrompt,
        int targetCount, String optionsJson) implements ImageGenerationCommand {
}
