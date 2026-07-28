package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.review.ReviewOpportunityDtos.OpportunityResponse;
import cafe.snails.ecomagents.dto.review.ReviewQueryDtos.InsightResponse;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReviewOpportunityService {
    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "minor", 1, "moderate", 2, "major", 3, "critical", 4);

    private final ReviewAnalysisProjectRepository projectRepository;
    private final ReviewAnalysisRunRepository runRepository;
    private final ReviewInsightRepository insightRepository;
    private final ProductReviewRepository reviewRepository;
    private final ImprovementOpportunityRepository opportunityRepository;
    private final ReviewOpportunityInsightRepository linkRepository;
    private final ReviewOpportunityClusterer clusterer;
    private final ReviewOpportunityScorer scorer;

    public List<OpportunityResponse> generate(Long runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析任务不存在"));
        var insights = insightRepository.findByAnalysisRunId(runId);
        opportunityRepository.deleteByAnalysisRunId(runId);
        if (insights.isEmpty()) return List.of();

        Map<Long, ReviewInsight> byId = new HashMap<>();
        insights.forEach(value -> byId.put(value.getId(), value));
        Set<Long> reviewIds = new HashSet<>();
        insights.forEach(value -> reviewIds.add(value.getReviewId()));
        Map<Long, ProductReview> reviews = new HashMap<>();
        reviewRepository.findAllById(reviewIds).forEach(value -> reviews.put(value.getId(), value));
        long totalReviews = Math.max(1, reviewRepository.countByProjectId(run.getProjectId()));

        Map<Bucket, List<ReviewInsight>> buckets = new LinkedHashMap<>();
        for (var insight : insights) {
            var key = new Bucket(insight.getUsageScenario(), insight.getProductModule(), insight.getActionType());
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(insight);
        }

        List<ImprovementOpportunity> saved = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            for (var cluster : clusterer.cluster(run, entry.getValue())) {
                List<ReviewInsight> members = cluster.insightIds().stream().map(byId::get)
                        .filter(Objects::nonNull).toList();
                if (members.isEmpty()) continue;
                String severity = members.stream().map(ReviewInsight::getSeverity)
                        .max(Comparator.comparingInt(value -> SEVERITY_RANK.getOrDefault(value, 0)))
                        .orElse("minor");
                var score = scorer.score(members, reviews, totalReviews, entry.getKey().actionType());
                var now = LocalDateTime.now();
                var opportunity = opportunityRepository.save(ImprovementOpportunity.builder()
                        .analysisRunId(runId)
                        .title(cluster.title())
                        .usageScenario(entry.getKey().scenario())
                        .productModule(entry.getKey().module())
                        .severity(severity)
                        .actionType(entry.getKey().actionType())
                        .recommendedAction(cluster.recommendedAction())
                        .insightCount(members.size())
                        .affectedReviewRatio(score.affectedReviewRatio())
                        .customerImpact(score.customerImpact())
                        .businessImpact(score.businessImpact())
                        .implementationEffort(score.implementationEffort())
                        .priorityScore(score.priorityScore())
                        .rationale(cluster.rationale())
                        .manuallyEdited(false)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                linkRepository.saveAll(members.stream().map(insight -> ReviewOpportunityInsight.builder()
                        .opportunityId(opportunity.getId()).insightId(insight.getId()).build()).toList());
                saved.add(opportunity);
            }
        }
        return saved.stream().sorted(Comparator.comparing(
                ImprovementOpportunity::getPriorityScore).reversed()).map(this::toResponse).toList();
    }

    public List<OpportunityResponse> list(Long projectId, Long runId, Long userId) {
        requireOwnedRun(projectId, runId, userId);
        return opportunityRepository.findByAnalysisRunIdOrderByPriorityScoreDesc(runId)
                .stream().map(this::toResponse).toList();
    }

    public List<InsightResponse> insights(
            Long projectId, Long runId, Long opportunityId, Long userId) {
        requireOwnedRun(projectId, runId, userId);
        opportunityRepository.findByIdAndAnalysisRunId(opportunityId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Improvement opportunity not found"));
        Set<Long> insightIds = linkRepository.findByOpportunityId(opportunityId).stream()
                .map(ReviewOpportunityInsight::getInsightId).collect(java.util.stream.Collectors.toSet());
        if (insightIds.isEmpty()) return List.of();
        List<ReviewInsight> insights = insightRepository.findAllById(insightIds).stream()
                .filter(value -> Objects.equals(value.getAnalysisRunId(), runId)).toList();
        Set<Long> reviewIds = insights.stream().map(ReviewInsight::getReviewId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, ProductReview> reviews = new HashMap<>();
        reviewRepository.findAllById(reviewIds).forEach(value -> reviews.put(value.getId(), value));
        return insights.stream().map(value -> {
            ProductReview review = reviews.get(value.getReviewId());
            return new InsightResponse(value.getId(), value.getReviewId(),
                    review == null ? null : review.getAsin(), review == null ? null : review.getRating(),
                    review == null ? null : review.getReviewText(), value.getUserProblem(),
                    value.getUsageScenario(), value.getProductModule(), value.getSeverity(),
                    value.getSentiment(), value.getEvidenceQuote(), value.getActionType(),
                    value.getImprovementAction(), value.getReturnRisk(), value.getConversionRisk(),
                    value.getConfidence(), value.getManuallyEdited(), value.getUpdatedAt());
        }).toList();
    }

    @Transactional
    public OpportunityResponse updateEffort(
            Long projectId, Long runId, Long opportunityId, BigDecimal effort, Long userId) {
        requireOwnedRun(projectId, runId, userId);
        if (effort == null || effort.compareTo(BigDecimal.ONE) < 0
                || effort.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实施成本必须在 1 到 100 之间");
        }
        var opportunity = opportunityRepository.findByIdAndAnalysisRunId(opportunityId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "改进机会不存在"));
        opportunity.setImplementationEffort(effort);
        opportunity.setPriorityScore(scorer.priority(
                opportunity.getCustomerImpact(), opportunity.getBusinessImpact(), effort));
        opportunity.setManuallyEdited(true);
        opportunity.setUpdatedAt(LocalDateTime.now());
        return toResponse(opportunityRepository.save(opportunity));
    }

    private ReviewAnalysisRun requireOwnedRun(Long projectId, Long runId, Long userId) {
        if (projectRepository.findByIdAndCreatedBy(projectId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论分析项目不存在");
        }
        return runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析任务不存在"));
    }

    private OpportunityResponse toResponse(ImprovementOpportunity value) {
        return new OpportunityResponse(value.getId(), value.getAnalysisRunId(), value.getTitle(),
                value.getUsageScenario(), value.getProductModule(), value.getSeverity(), value.getActionType(),
                value.getRecommendedAction(), value.getInsightCount(), value.getAffectedReviewRatio(),
                value.getCustomerImpact(), value.getBusinessImpact(), value.getImplementationEffort(),
                value.getPriorityScore(), value.getRationale(), value.getManuallyEdited());
    }

    private record Bucket(String scenario, String module, String actionType) {}
}
