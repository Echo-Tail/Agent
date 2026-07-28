package cafe.snails.ecomagents.dto.review;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 评论洞察质量验证相关的数据传输对象集合。
 */
public final class ReviewValidationDtos {
    private ReviewValidationDtos() {}

    /** 提交单条洞察人工审核结果的请求。 */
    public record AuditRequest(
            @NotNull Boolean evidenceValid,
            @NotNull Boolean moduleAccepted,
            @NotNull Boolean severityAccepted,
            String notes) {}

    /** 待审核或已审核的洞察抽样记录。 */
    public record AuditSample(
            Long insightId, Long reviewId, String asin, String reviewText, String evidenceQuote,
            String productModule, String severity, Boolean audited,
            Boolean evidenceValid, Boolean moduleAccepted, Boolean severityAccepted, String notes) {}

    /** 单项质量门禁检查结果。 */
    public record GateCheck(String key, boolean passed, String actual, String target) {}

    /** 评论洞察质量验证报告。 */
    public record ValidationReport(
            Long runId, int sampleSize, int auditedCount,
            BigDecimal evidenceValidityRate, BigDecimal moduleAcceptanceRate,
            BigDecimal severityAcceptanceRate, BigDecimal duplicateRate,
            int traceableTopOpportunities, int topOpportunityCount,
            boolean releaseReady, List<GateCheck> checks) {}
}
