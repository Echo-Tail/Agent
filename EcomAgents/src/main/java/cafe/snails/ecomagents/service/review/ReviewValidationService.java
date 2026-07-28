package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.review.ReviewValidationDtos.*;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/** 负责评论洞察抽样审核和质量门禁计算。 */
public class ReviewValidationService {
    private static final int AUDIT_SAMPLE_SIZE = 50;
    private final ReviewProjectService projectService;
    private final ReviewAnalysisRunRepository runRepository;
    private final ReviewInsightRepository insightRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewInsightAuditRepository auditRepository;
    private final ImprovementOpportunityRepository opportunityRepository;
    private final ReviewOpportunityInsightRepository linkRepository;

    public List<AuditSample> sample(Long projectId, Long runId, Long userId) {
        requireRun(projectId, runId, userId);
        List<ReviewInsight> insights = insightRepository.findByAnalysisRunId(runId).stream()
                .sorted(Comparator.comparing(ReviewInsight::getId)).limit(AUDIT_SAMPLE_SIZE).toList();
        Map<Long, ProductReview> reviews = reviewRepository.findAllById(
                insights.stream().map(ReviewInsight::getReviewId).toList()).stream()
                .collect(Collectors.toMap(ProductReview::getId, Function.identity()));
        Map<Long, ReviewInsightAudit> audits = auditRepository.findByInsightIdIn(
                insights.stream().map(ReviewInsight::getId).toList()).stream()
                .filter(value -> Objects.equals(value.getReviewedBy(), userId))
                .collect(Collectors.toMap(ReviewInsightAudit::getInsightId, Function.identity()));
        return insights.stream().map(insight -> {
            var review = reviews.get(insight.getReviewId());
            var audit = audits.get(insight.getId());
            return new AuditSample(insight.getId(), insight.getReviewId(),
                    review == null ? null : review.getAsin(), review == null ? null : review.getReviewText(),
                    insight.getEvidenceQuote(), insight.getProductModule(), insight.getSeverity(), audit != null,
                    audit == null ? null : audit.getEvidenceValid(),
                    audit == null ? null : audit.getModuleAccepted(),
                    audit == null ? null : audit.getSeverityAccepted(),
                    audit == null ? null : audit.getNotes());
        }).toList();
    }

    @Transactional
    public AuditSample audit(
            Long projectId, Long runId, Long insightId, AuditRequest request, Long userId) {
        requireRun(projectId, runId, userId);
        var insight = insightRepository.findById(insightId)
                .filter(value -> Objects.equals(value.getAnalysisRunId(), runId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Review insight not found"));
        var audit = auditRepository.findByInsightIdAndReviewedBy(insightId, userId)
                .orElseGet(() -> ReviewInsightAudit.builder().insightId(insightId).reviewedBy(userId).build());
        audit.setEvidenceValid(request.evidenceValid());
        audit.setModuleAccepted(request.moduleAccepted());
        audit.setSeverityAccepted(request.severityAccepted());
        audit.setNotes(request.notes());
        audit.setReviewedAt(LocalDateTime.now());
        auditRepository.save(audit);
        var review = reviewRepository.findById(insight.getReviewId()).orElse(null);
        return new AuditSample(insightId, insight.getReviewId(), review == null ? null : review.getAsin(),
                review == null ? null : review.getReviewText(), insight.getEvidenceQuote(),
                insight.getProductModule(), insight.getSeverity(), true, audit.getEvidenceValid(),
                audit.getModuleAccepted(), audit.getSeverityAccepted(), audit.getNotes());
    }

    public ValidationReport report(Long projectId, Long runId, Long userId) {
        requireRun(projectId, runId, userId);
        List<AuditSample> samples = sample(projectId, runId, userId);
        List<AuditSample> audited = samples.stream().filter(AuditSample::audited).toList();
        BigDecimal evidenceRate = rate(audited, AuditSample::evidenceValid);
        BigDecimal moduleRate = rate(audited, AuditSample::moduleAccepted);
        BigDecimal severityRate = rate(audited, AuditSample::severityAccepted);
        List<ProductReview> storedReviews = reviewRepository.findByProjectIdOrderById(projectId);
        long uniqueReviews = storedReviews.stream().map(value -> value.getAsin() + ":" +
                (value.getExternalReviewId() == null ? value.getContentHash() : value.getExternalReviewId()))
                .distinct().count();
        long duplicateRows = storedReviews.size() - uniqueReviews;
        BigDecimal duplicateRate = storedReviews.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(duplicateRows).divide(BigDecimal.valueOf(storedReviews.size()), 4, RoundingMode.HALF_UP);
        var top = opportunityRepository.findByAnalysisRunIdOrderByPriorityScoreDesc(runId).stream().limit(10).toList();
        int traceable = (int) top.stream().filter(value -> !linkRepository.findByOpportunityId(value.getId()).isEmpty()).count();
        int requiredAudits = Math.min(AUDIT_SAMPLE_SIZE, samples.size());
        List<GateCheck> checks = List.of(
                check("audit_sample", audited.size() >= requiredAudits, audited.size(), requiredAudits),
                check("evidence_validity", meets(evidenceRate, "0.98"), evidenceRate, ">= 0.98"),
                check("module_acceptance", meets(moduleRate, "0.85"), moduleRate, ">= 0.85"),
                check("severity_acceptance", meets(severityRate, "0.80"), severityRate, ">= 0.80"),
                check("duplicate_rate", duplicateRate.compareTo(new BigDecimal("0.01")) < 0, duplicateRate, "< 0.01"),
                check("top_opportunity_traceability", traceable == top.size(), traceable, top.size()));
        return new ValidationReport(runId, samples.size(), audited.size(), evidenceRate, moduleRate,
                severityRate, duplicateRate, traceable, top.size(),
                checks.stream().allMatch(GateCheck::passed), checks);
    }

    private ReviewAnalysisRun requireRun(Long projectId, Long runId, Long userId) {
        projectService.requireOwned(projectId, userId);
        return runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Review analysis run not found"));
    }

    private BigDecimal rate(List<AuditSample> values, Function<AuditSample, Boolean> classifier) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        long accepted = values.stream().filter(value -> Boolean.TRUE.equals(classifier.apply(value))).count();
        return BigDecimal.valueOf(accepted).divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private boolean meets(BigDecimal value, String target) {
        return value.compareTo(new BigDecimal(target)) >= 0;
    }

    private GateCheck check(String key, boolean passed, Object actual, Object target) {
        return new GateCheck(key, passed, String.valueOf(actual), String.valueOf(target));
    }
}
