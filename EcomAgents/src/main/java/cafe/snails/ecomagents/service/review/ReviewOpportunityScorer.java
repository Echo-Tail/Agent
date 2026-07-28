package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.model.review.*;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;

@Component
/** 根据业务影响和实施成本计算改进机会优先级。 */
public class ReviewOpportunityScorer {
    private static final Map<String, BigDecimal> SEVERITY = Map.of(
            "critical", BigDecimal.valueOf(100),
            "major", BigDecimal.valueOf(75),
            "moderate", BigDecimal.valueOf(45),
            "minor", BigDecimal.valueOf(20));

    public Score score(List<ReviewInsight> insights, Map<Long, ProductReview> reviews, long totalReviews, String actionType) {
        BigDecimal severity = insights.stream().map(value -> SEVERITY.getOrDefault(
                value.getSeverity(), BigDecimal.valueOf(20))).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal ratio = divide(insights.stream().map(ReviewInsight::getReviewId).distinct().count(),
                Math.max(1, totalReviews), 5);
        BigDecimal evidenceQuality = average(insights, insight -> {
            ProductReview review = reviews.get(insight.getReviewId());
            BigDecimal verified = review != null && Boolean.TRUE.equals(review.getVerifiedPurchase())
                    ? BigDecimal.valueOf(40) : BigDecimal.ZERO;
            BigDecimal helpful = review == null ? BigDecimal.ZERO : BigDecimal.valueOf(
                    Math.min(1d, Math.max(0, review.getHelpfulCount()) / 10d) * 30d);
            return verified.add(helpful).add(insight.getConfidence().multiply(BigDecimal.valueOf(30)));
        });
        BigDecimal customerImpact = severity.multiply(BigDecimal.valueOf(.55))
                .add(ratio.multiply(BigDecimal.valueOf(100)).multiply(BigDecimal.valueOf(.30)))
                .add(evidenceQuality.multiply(BigDecimal.valueOf(.15)));
        BigDecimal returnRisk = average(insights, value -> BigDecimal.valueOf(value.getReturnRisk()));
        BigDecimal conversionRisk = average(insights, value -> BigDecimal.valueOf(value.getConversionRisk()));
        long lowRatings = insights.stream().map(ReviewInsight::getReviewId).distinct()
                .map(reviews::get).filter(Objects::nonNull)
                .filter(review -> review.getRating() != null
                        && review.getRating().compareTo(BigDecimal.valueOf(3)) <= 0).count();
        long rated = insights.stream().map(ReviewInsight::getReviewId).distinct()
                .map(reviews::get).filter(Objects::nonNull).filter(review -> review.getRating() != null).count();
        BigDecimal lowRatingRatio = divide(lowRatings, Math.max(1, rated), 5);
        BigDecimal businessImpact = returnRisk.multiply(BigDecimal.valueOf(20)).multiply(BigDecimal.valueOf(.45))
                .add(conversionRisk.multiply(BigDecimal.valueOf(20)).multiply(BigDecimal.valueOf(.35)))
                .add(lowRatingRatio.multiply(BigDecimal.valueOf(100)).multiply(BigDecimal.valueOf(.20)));
        BigDecimal effort = defaultEffort(actionType);
        BigDecimal priority = customerImpact.multiply(businessImpact)
                .divide(effort.max(BigDecimal.TEN), 2, RoundingMode.HALF_UP);
        return new Score(ratio, round(customerImpact), round(businessImpact), effort, priority);
    }

    public BigDecimal priority(BigDecimal customerImpact, BigDecimal businessImpact, BigDecimal effort) {
        return customerImpact.multiply(businessImpact)
                .divide(effort.max(BigDecimal.TEN), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultEffort(String actionType) {
        return switch (actionType) {
            case "docs", "support" -> BigDecimal.valueOf(20);
            case "hardware" -> BigDecimal.valueOf(100);
            case "accessory" -> BigDecimal.valueOf(70);
            default -> BigDecimal.valueOf(40);
        };
    }

    private BigDecimal average(List<ReviewInsight> values,
            java.util.function.Function<ReviewInsight, BigDecimal> mapper) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        return values.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 5, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(long numerator, long denominator, int scale) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record Score(
            BigDecimal affectedReviewRatio,
            BigDecimal customerImpact,
            BigDecimal businessImpact,
            BigDecimal implementationEffort,
            BigDecimal priorityScore) {}
}
