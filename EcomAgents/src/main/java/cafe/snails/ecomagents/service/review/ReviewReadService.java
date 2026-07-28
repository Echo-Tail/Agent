package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.review.ReviewQueryDtos.*;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewReadService {
    private final ReviewProjectService projectService;
    private final ProductReviewRepository reviewRepository;
    private final ReviewAnalysisRunRepository runRepository;
    private final ReviewInsightRepository insightRepository;
    private final ImprovementOpportunityRepository opportunityRepository;

    public Page<ProductReviewResponse> reviews(
            Long projectId, String asin, BigDecimal minRating, BigDecimal maxRating,
            Boolean verified, String keyword, Pageable pageable, Long userId) {
        projectService.requireOwned(projectId, userId);
        Specification<ProductReview> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (hasText(asin)) spec = spec.and((root, query, cb) ->
                cb.equal(root.get("asin"), asin.trim().toUpperCase(Locale.ROOT)));
        if (minRating != null) spec = spec.and((root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("rating"), minRating));
        if (maxRating != null) spec = spec.and((root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("rating"), maxRating));
        if (verified != null) spec = spec.and((root, query, cb) ->
                cb.equal(root.get("verifiedPurchase"), verified));
        if (hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("reviewText")), pattern)));
        }
        return reviewRepository.findAll(spec, pageable).map(this::toReviewResponse);
    }

    public Page<InsightResponse> insights(
            Long projectId, Long runId, String scenario, String module, String severity,
            String sentiment, String actionType, Boolean manuallyEdited, String keyword,
            Pageable pageable, Long userId) {
        requireRun(projectId, runId, userId);
        Specification<ReviewInsight> spec = (root, query, cb) ->
                cb.equal(root.get("analysisRunId"), runId);
        spec = equalIfPresent(spec, "usageScenario", scenario);
        spec = equalIfPresent(spec, "productModule", module);
        spec = equalIfPresent(spec, "severity", severity);
        spec = equalIfPresent(spec, "sentiment", sentiment);
        spec = equalIfPresent(spec, "actionType", actionType);
        if (manuallyEdited != null) spec = spec.and((root, query, cb) ->
                cb.equal(root.get("manuallyEdited"), manuallyEdited));
        if (hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("userProblem")), pattern),
                    cb.like(cb.lower(root.get("improvementAction")), pattern),
                    cb.like(cb.lower(root.get("evidenceQuote")), pattern)));
        }
        Page<ReviewInsight> result = insightRepository.findAll(spec, pageable);
        Map<Long, ProductReview> reviews = reviewRepository.findAllById(
                result.stream().map(ReviewInsight::getReviewId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductReview::getId, Function.identity()));
        return result.map(value -> toInsightResponse(value, reviews.get(value.getReviewId())));
    }

    @Transactional
    public InsightResponse updateInsight(
            Long projectId, Long runId, Long insightId, UpdateInsightRequest request, Long userId) {
        var run = requireRun(projectId, runId, userId);
        requireEditable(run);
        var insight = insightRepository.findById(insightId)
                .filter(value -> Objects.equals(value.getAnalysisRunId(), runId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Review insight not found"));
        var review = reviewRepository.findById(insight.getReviewId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Source review not found"));
        validateTaxonomy(request);
        if (!review.getReviewText().contains(request.evidenceQuote())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Evidence quote must be an exact substring of the review");
        }
        insight.setUserProblem(request.userProblem().trim());
        insight.setUsageScenario(request.usageScenario());
        insight.setProductModule(request.productModule());
        insight.setSeverity(request.severity());
        insight.setSentiment(request.sentiment());
        insight.setEvidenceQuote(request.evidenceQuote());
        insight.setActionType(request.actionType());
        insight.setImprovementAction(request.improvementAction().trim());
        insight.setReturnRisk(request.returnRisk());
        insight.setConversionRisk(request.conversionRisk());
        insight.setManuallyEdited(true);
        insight.setUpdatedAt(LocalDateTime.now());
        return toInsightResponse(insightRepository.save(insight), review);
    }

    @Transactional
    public ReviewAnalysisRun confirm(Long projectId, Long runId, Long userId) {
        var run = requireRun(projectId, runId, userId);
        if ("confirmed".equals(run.getStatus())) return run;
        if (!"draft".equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only a draft analysis can be confirmed");
        }
        run.setStatus("confirmed");
        run.setConfirmedBy(userId);
        run.setConfirmedAt(LocalDateTime.now());
        return runRepository.save(run);
    }

    public DashboardResponse dashboard(Long projectId, Long runId, Long userId) {
        requireRun(projectId, runId, userId);
        List<ProductReview> reviews = reviewRepository.findByProjectIdOrderById(projectId);
        List<ReviewInsight> insights = insightRepository.findByAnalysisRunId(runId);
        int opportunityCount = opportunityRepository.findByAnalysisRunIdOrderByPriorityScoreDesc(runId).size();
        BigDecimal averageRating = reviews.stream().map(ProductReview::getRating).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long rated = reviews.stream().filter(value -> value.getRating() != null).count();
        averageRating = rated == 0 ? null : averageRating.divide(BigDecimal.valueOf(rated), 2, RoundingMode.HALF_UP);
        return new DashboardResponse(runId, reviews.size(), insights.size(), opportunityCount,
                insights.stream().filter(value -> Boolean.TRUE.equals(value.getManuallyEdited())).count(),
                averageRating, counts(reviews, value -> value.getRating() == null ? "unknown" :
                        value.getRating().setScale(0, RoundingMode.HALF_UP).toPlainString()),
                counts(insights, ReviewInsight::getSeverity),
                counts(insights, ReviewInsight::getUsageScenario),
                counts(insights, ReviewInsight::getProductModule),
                counts(insights, ReviewInsight::getActionType),
                reviews.stream().collect(Collectors.groupingBy(ProductReview::getAsin,
                        TreeMap::new, Collectors.summingInt(value -> 1))));
    }

    private ReviewAnalysisRun requireRun(Long projectId, Long runId, Long userId) {
        projectService.requireOwned(projectId, userId);
        return runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Review analysis run not found"));
    }

    private void requireEditable(ReviewAnalysisRun run) {
        if (!"draft".equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Insights can only be edited while the analysis is in draft");
        }
    }

    private void validateTaxonomy(UpdateInsightRequest value) {
        if (!CarStereoReviewTaxonomy.USAGE_SCENARIOS.contains(value.usageScenario())
                || !CarStereoReviewTaxonomy.PRODUCT_MODULES.contains(value.productModule())
                || !CarStereoReviewTaxonomy.SEVERITIES.contains(value.severity())
                || !CarStereoReviewTaxonomy.SENTIMENTS.contains(value.sentiment())
                || !CarStereoReviewTaxonomy.ACTION_TYPES.contains(value.actionType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Insight contains an unsupported taxonomy value");
        }
    }

    private <T> List<DimensionCount> counts(List<T> values, Function<T, String> classifier) {
        return values.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new DimensionCount(entry.getKey(), entry.getValue())).toList();
    }

    private Specification<ReviewInsight> equalIfPresent(
            Specification<ReviewInsight> spec, String field, String value) {
        return !hasText(value) ? spec : spec.and((root, query, cb) -> cb.equal(root.get(field), value.trim()));
    }

    private ProductReviewResponse toReviewResponse(ProductReview value) {
        return new ProductReviewResponse(value.getId(), value.getAsin(), value.getRating(), value.getTitle(),
                value.getReviewText(), value.getReviewDate(), value.getVerifiedPurchase(), value.getHelpfulCount(),
                value.getReviewerName(), value.getSourceUrl(), value.getCollectedAt());
    }

    private InsightResponse toInsightResponse(ReviewInsight value, ProductReview review) {
        return new InsightResponse(value.getId(), value.getReviewId(),
                review == null ? null : review.getAsin(), review == null ? null : review.getRating(),
                review == null ? null : review.getReviewText(), value.getUserProblem(), value.getUsageScenario(),
                value.getProductModule(), value.getSeverity(), value.getSentiment(), value.getEvidenceQuote(),
                value.getActionType(), value.getImprovementAction(), value.getReturnRisk(),
                value.getConversionRisk(), value.getConfidence(), value.getManuallyEdited(), value.getUpdatedAt());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
