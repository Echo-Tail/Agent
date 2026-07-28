package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.review.ProductReview;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
/** 将模型返回内容解析为结构化评论洞察。 */
public class ReviewInsightParser {
    public static final String SCHEMA_VERSION = "review_insight_v1";
    private final ObjectMapper objectMapper;

    public ParsedBatch parse(String response, List<ProductReview> sourceReviews) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            require(SCHEMA_VERSION.equals(root.path("schema_version").asText()), "schema_version 无效");
            require(root.path("reviews").isArray(), "reviews 必须为数组");
            Map<Long, ProductReview> allowed = new LinkedHashMap<>();
            sourceReviews.forEach(review -> allowed.put(review.getId(), review));
            Set<Long> returned = new HashSet<>();
            List<ParsedReview> reviews = new ArrayList<>();

            for (JsonNode reviewNode : root.path("reviews")) {
                require(reviewNode.path("review_id").canConvertToLong(), "review_id 必须为整数");
                long reviewId = reviewNode.path("review_id").asLong();
                ProductReview source = allowed.get(reviewId);
                require(source != null, "响应包含不属于当前批次的 review_id");
                require(returned.add(reviewId), "响应包含重复 review_id");
                require(reviewNode.path("insights").isArray(), "insights 必须为数组");
                List<ParsedInsight> insights = new ArrayList<>();
                Set<String> insightKeys = new HashSet<>();
                for (JsonNode insight : reviewNode.path("insights")) {
                    ParsedInsight parsed = parseInsight(insight, source);
                    String key = parsed.productModule() + "\n" + parsed.userProblem().toLowerCase(Locale.ROOT);
                    if (insightKeys.add(key)) insights.add(parsed);
                }
                reviews.add(new ParsedReview(reviewId, insights));
            }
            require(returned.equals(allowed.keySet()), "响应必须包含当前批次的全部 review_id");
            return new ParsedBatch(reviews);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 评论分析 JSON 无效: " + e.getMessage());
        }
    }

    private ParsedInsight parseInsight(JsonNode node, ProductReview source) {
        String userProblem = requiredText(node, "user_problem");
        String scenario = enumText(node, "usage_scenario", CarStereoReviewTaxonomy.USAGE_SCENARIOS);
        String module = enumText(node, "product_module", CarStereoReviewTaxonomy.PRODUCT_MODULES);
        String severity = enumText(node, "severity", CarStereoReviewTaxonomy.SEVERITIES);
        String sentiment = enumText(node, "sentiment", CarStereoReviewTaxonomy.SENTIMENTS);
        String evidence = exactEvidence(source.getReviewText(), requiredText(node, "evidence_quote"));
        String actionType = enumText(node, "action_type", CarStereoReviewTaxonomy.ACTION_TYPES);
        String action = requiredText(node, "improvement_action");
        int returnRisk = boundedInt(node, "return_risk", 1, 5);
        int conversionRisk = boundedInt(node, "conversion_risk", 1, 5);
        BigDecimal confidence = decimal(node, "confidence");
        require(confidence.compareTo(BigDecimal.ZERO) >= 0 && confidence.compareTo(BigDecimal.ONE) <= 0,
                "confidence 必须在 0 到 1 之间");
        return new ParsedInsight(userProblem, scenario, module, severity, sentiment, evidence,
                actionType, action, returnRisk, conversionRisk, confidence);
    }

    private String exactEvidence(String reviewText, String proposed) {
        if (reviewText.contains(proposed)) return proposed;
        String unquoted = proposed;
        if (proposed.length() > 1 && "\"'“”‘’".indexOf(proposed.charAt(0)) >= 0
                && "\"'“”‘’".indexOf(proposed.charAt(proposed.length() - 1)) >= 0) {
            unquoted = proposed.substring(1, proposed.length() - 1).trim();
            if (reviewText.contains(unquoted)) return unquoted;
        }
        int index = reviewText.toLowerCase(Locale.ROOT).indexOf(unquoted.toLowerCase(Locale.ROOT));
        require(index >= 0, "evidence_quote is not a substring of the source review");
        return reviewText.substring(index, index + unquoted.length());
    }

    private String extractJson(String response) {
        require(response != null && !response.isBlank(), "LLM 返回为空");
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        require(start >= 0 && end > start, "LLM 返回不包含 JSON 对象");
        return response.substring(start, end + 1);
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        require(!value.isBlank(), field + " 不能为空");
        return value;
    }

    private String enumText(JsonNode node, String field, Set<String> allowed) {
        String value = requiredText(node, field);
        require(allowed.contains(value), field + " 不属于 car_stereo_v1");
        return value;
    }

    private int boundedInt(JsonNode node, String field, int min, int max) {
        require(node.path(field).canConvertToInt(), field + " 必须为整数");
        int value = node.path(field).asInt();
        require(value >= min && value <= max, field + " 超出范围");
        return value;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        require(node.path(field).isNumber(), field + " 必须为数字");
        return node.path(field).decimalValue();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public record ParsedBatch(List<ParsedReview> reviews) {}
    public record ParsedReview(Long reviewId, List<ParsedInsight> insights) {}
    public record ParsedInsight(
            String userProblem,
            String usageScenario,
            String productModule,
            String severity,
            String sentiment,
            String evidenceQuote,
            String actionType,
            String improvementAction,
            Integer returnRisk,
            Integer conversionRisk,
            BigDecimal confidence) {}
}
