package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import cafe.snails.ecomagents.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.GenerateOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewAnalysisExecutor {
    static final int MAX_REVIEWS_PER_LLM_REQUEST = 50;
    private final ReviewAnalysisRunRepository runRepository;
    private final ReviewAnalysisProjectRepository projectRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewInsightRepository insightRepository;
    private final ReviewAnalysisFailureRepository failureRepository;
    private final AiModelService aiModelService;
    private final LlmService llmService;
    private final ReviewInsightParser parser;
    private final ReviewOpportunityService opportunityService;
    private final ObjectMapper objectMapper;

    public void execute(Long runId, int requestedBatchSize) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析任务不存在"));
        var project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析项目不存在"));
        int batchSize = Math.max(1, Math.min(MAX_REVIEWS_PER_LLM_REQUEST, requestedBatchSize));
        var reviews = reviewRepository.findByProjectIdOrderById(run.getProjectId());
        run.setStatus("running");
        runRepository.save(run);

        int processed = 0;
        int failed = 0;
        String lastError = null;
        var options = aiModelService.buildModelOptions(run.getModelId());
        if (options == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "模型配置不存在");

        for (int from = 0; from < reviews.size(); from += batchSize) {
            List<ProductReview> batch = reviews.subList(from, Math.min(from + batchSize, reviews.size()));
            try {
                analyze(run, batch, options);
                processed += batch.size();
            } catch (Exception e) {
                lastError = safeMessage(e);
                log.warn("Review analysis batch failed: runId={}, from={}, size={}, error={}",
                        runId, from, batch.size(), lastError);
                for (ProductReview review : batch) {
                    try {
                        analyze(run, List.of(review), options);
                        processed++;
                    } catch (Exception singleError) {
                        failed++;
                        lastError = safeMessage(singleError);
                        recordFailure(runId, review.getId(), lastError);
                    }
                }
            }
            run.setProcessedReviewCount(processed);
            run.setFailedReviewCount(failed);
            run.setErrorMessage(lastError);
            runRepository.save(run);
        }

        if (processed == 0) {
            run.setStatus("failed");
            project.setStatus("failed");
            if (run.getErrorMessage() == null) run.setErrorMessage("没有评论成功完成分析");
        } else {
            try {
                opportunityService.generate(runId);
            } catch (Exception e) {
                lastError = "改进机会生成失败: " + safeMessage(e);
                run.setErrorMessage(lastError);
                log.warn("Review opportunity generation failed: runId={}, error={}", runId, lastError);
            }
            run.setStatus("draft");
            project.setStatus("review");
        }
        project.setUpdatedAt(LocalDateTime.now());
        runRepository.save(run);
        projectRepository.save(project);
    }

    public void retryFailures(Long runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Review analysis run not found"));
        var project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Review analysis project not found"));
        var failures = failureRepository.findByAnalysisRunIdOrderByReviewId(runId);
        if (failures.isEmpty()) return;
        var options = aiModelService.buildModelOptions(run.getModelId());
        run.setStatus("running");
        project.setStatus("analyzing");
        runRepository.save(run);
        projectRepository.save(project);
        int recovered = 0;
        String lastError = null;
        for (var failure : failures) {
            var review = reviewRepository.findById(failure.getReviewId()).orElse(null);
            if (review == null) continue;
            try {
                analyze(run, List.of(review), options);
                recovered++;
            } catch (Exception error) {
                lastError = safeMessage(error);
                recordFailure(runId, review.getId(), lastError);
            }
            run.setProcessedReviewCount(run.getProcessedReviewCount() + recovered);
            recovered = 0;
            run.setFailedReviewCount(Math.toIntExact(failureRepository.countByAnalysisRunId(runId)));
            run.setErrorMessage(lastError);
            runRepository.save(run);
        }
        try {
            opportunityService.generate(runId);
        } catch (Exception error) {
            run.setErrorMessage("Opportunity regeneration failed: " + safeMessage(error));
        }
        run.setStatus("draft");
        project.setStatus("review");
        project.setUpdatedAt(LocalDateTime.now());
        runRepository.save(run);
        projectRepository.save(project);
    }

    public void markUnexpectedFailure(Long runId, Exception error) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus("failed");
            run.setErrorMessage(safeMessage(error));
            runRepository.save(run);
            projectRepository.findById(run.getProjectId()).ifPresent(project -> {
                project.setStatus("failed");
                project.setUpdatedAt(LocalDateTime.now());
                projectRepository.save(project);
            });
        });
    }

    private void persist(Long runId, ReviewInsightParser.ParsedBatch batch) {
        List<ReviewInsight> insights = new ArrayList<>();
        for (var review : batch.reviews()) {
            for (var value : review.insights()) {
                insights.add(ReviewInsight.builder()
                        .analysisRunId(runId)
                        .reviewId(review.reviewId())
                        .userProblem(value.userProblem())
                        .usageScenario(value.usageScenario())
                        .productModule(value.productModule())
                        .severity(value.severity())
                        .sentiment(value.sentiment())
                        .evidenceQuote(value.evidenceQuote())
                        .actionType(value.actionType())
                        .improvementAction(value.improvementAction())
                        .returnRisk(value.returnRisk())
                        .conversionRisk(value.conversionRisk())
                        .confidence(value.confidence())
                        .manuallyEdited(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
            }
        }
        if (!insights.isEmpty()) insightRepository.saveAll(insights);
    }

    private void analyze(ReviewAnalysisRun run, List<ProductReview> reviews, GenerateOptions options) throws Exception {
        String response = llmService.syncChat(systemPrompt(run.getRolePrompt()),
                List.of(Map.of("role", "user", "content", userPrompt(reviews))), options);
        persist(run.getId(), parser.parse(response, reviews));
        for (var review : reviews) {
            failureRepository.deleteByAnalysisRunIdAndReviewId(run.getId(), review.getId());
        }
    }

    private void recordFailure(Long runId, Long reviewId, String message) {
        var failure = failureRepository.findByAnalysisRunIdAndReviewId(runId, reviewId)
                .orElseGet(() -> ReviewAnalysisFailure.builder()
                        .analysisRunId(runId).reviewId(reviewId).attemptCount(0).build());
        failure.setAttemptCount(failure.getAttemptCount() + 1);
        failure.setErrorMessage(message);
        failure.setLastAttemptAt(LocalDateTime.now());
        failureRepository.save(failure);
    }

    private String systemPrompt(String rolePrompt) {
        return rolePrompt + """

                You must analyze only the supplied Amazon US car stereo reviews.
                Return one JSON object and no prose or markdown.
                Never invent a defect or evidence. A positive review with no actionable issue must have an empty insights array.
                Every evidence_quote must be an exact, case-sensitive substring of review_text.
                Keep evidence_quote in the review's original language. Write user_problem and improvement_action in Simplified Chinese.
                Split independent problems in one review into separate insights.
                schema_version must be review_insight_v1.
                usage_scenario values: %s
                product_module values: %s
                severity values: %s
                sentiment values: %s
                action_type values: %s
                Every input review_id must appear exactly once in reviews.
                Each insight requires user_problem, usage_scenario, product_module, severity, sentiment,
                evidence_quote, action_type, improvement_action, return_risk (1-5),
                conversion_risk (1-5), and confidence (0-1).
                """.formatted(
                CarStereoReviewTaxonomy.USAGE_SCENARIOS,
                CarStereoReviewTaxonomy.PRODUCT_MODULES,
                CarStereoReviewTaxonomy.SEVERITIES,
                CarStereoReviewTaxonomy.SENTIMENTS,
                CarStereoReviewTaxonomy.ACTION_TYPES);
    }

    private String userPrompt(List<ProductReview> reviews) throws Exception {
        List<Map<String, Object>> input = reviews.stream().map(review -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("review_id", review.getId());
            value.put("asin", review.getAsin());
            value.put("rating", review.getRating());
            value.put("title", review.getTitle());
            value.put("review_text", review.getReviewText());
            value.put("verified_purchase", review.getVerifiedPurchase());
            value.put("helpful_count", review.getHelpfulCount());
            return value;
        }).toList();
        return "Analyze these reviews and return {\"schema_version\":\"review_insight_v1\",\"reviews\":[...]}\n"
                + objectMapper.writeValueAsString(input);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
