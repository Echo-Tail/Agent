package cafe.snails.ecomagents.dto.review;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class ReviewValidationDtos {
    private ReviewValidationDtos() {}

    public record AuditRequest(
            @NotNull Boolean evidenceValid,
            @NotNull Boolean moduleAccepted,
            @NotNull Boolean severityAccepted,
            String notes) {}

    public record AuditSample(
            Long insightId, Long reviewId, String asin, String reviewText, String evidenceQuote,
            String productModule, String severity, Boolean audited,
            Boolean evidenceValid, Boolean moduleAccepted, Boolean severityAccepted, String notes) {}

    public record GateCheck(String key, boolean passed, String actual, String target) {}

    public record ValidationReport(
            Long runId, int sampleSize, int auditedCount,
            BigDecimal evidenceValidityRate, BigDecimal moduleAcceptanceRate,
            BigDecimal severityAcceptanceRate, BigDecimal duplicateRate,
            int traceableTopOpportunities, int topOpportunityCount,
            boolean releaseReady, List<GateCheck> checks) {}
}
