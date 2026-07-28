package cafe.snails.ecomagents.service.image.runtime.worker;

import cafe.snails.ecomagents.service.image.runtime.ImageGenerationJobExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
/** 按计划周期触发图片生成工作线程。 */
public class ImageGenerationWorkerRunner {
    private final ImageGenerationJobExecutor executor;

    @Async
    public void run(Long jobId, String workerId, Runnable completion) {
        try { executor.executeClaimed(jobId, workerId); }
        catch (Exception e) { log.error("Image job {} execution failed: {}", jobId, e.getMessage()); }
        finally { completion.run(); }
    }
}
