package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.model.ImageGenerationJob;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.service.image.runtime.command.ImageGenerationCommand;
import java.util.List;

/** 定义图片生成任务的提交、查询与重试运行时能力。 */
public interface ImageGenerationRuntime {
    ImageGenerationJob submit(ImageGenerationCommand command);
    ImageGenerationJob get(Long jobId, Long userId);
    List<ImageGenerationRecord> results(Long jobId, Long userId);
    ImageGenerationJob cancel(Long jobId, Long userId);
    ImageGenerationJob retry(Long jobId, Long userId);
}
