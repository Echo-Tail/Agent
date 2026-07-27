package cafe.snails.ecomagents.service.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewAnalysisWorkerRunner {
    private final ReviewAnalysisExecutor executor;

    @Async
    public void run(Long runId, int batchSize) {
        try {
            executor.execute(runId, batchSize);
        } catch (Exception e) {
            log.error("Review analysis worker failed: runId={}, error={}", runId, e.getMessage(), e);
            executor.markUnexpectedFailure(runId, e);
        }
    }

    @Async
    public void retryFailures(Long runId) {
        try {
            executor.retryFailures(runId);
        } catch (Exception e) {
            log.error("Review analysis retry failed: runId={}, error={}", runId, e.getMessage(), e);
            executor.markUnexpectedFailure(runId, e);
        }
    }
}
