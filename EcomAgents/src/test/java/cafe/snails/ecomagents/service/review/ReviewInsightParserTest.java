package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.review.ProductReview;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewInsightParserTest {
    private final ReviewInsightParser parser = new ReviewInsightParser(new ObjectMapper());

    @Test
    void parse_shouldAcceptStrictTraceableInsightJson() {
        var source = review(9L, "CarPlay disconnects every few minutes.");
        String json = """
                {
                  "schema_version": "review_insight_v1",
                  "reviews": [{
                    "review_id": 9,
                    "has_actionable_issue": true,
                    "insights": [{
                      "user_problem": "CarPlay disconnects repeatedly",
                      "usage_scenario": "daily_commute",
                      "product_module": "carplay",
                      "severity": "major",
                      "sentiment": "negative",
                      "evidence_quote": "CarPlay disconnects every few minutes.",
                      "action_type": "firmware",
                      "improvement_action": "Improve connection recovery",
                      "return_risk": 4,
                      "conversion_risk": 4,
                      "confidence": 0.92
                    }]
                  }]
                }
                """;

        var result = parser.parse(json, List.of(source));

        assertEquals(1, result.reviews().size());
        assertEquals("carplay", result.reviews().get(0).insights().get(0).productModule());
    }

    @Test
    void parse_shouldRejectInventedEvidence() {
        var source = review(9L, "The screen is bright.");
        String json = validEmptyJson().replace("\"insights\": []", """
                "insights": [{
                  "user_problem":"Screen freezes",
                  "usage_scenario":"daily_commute",
                  "product_module":"display_touch",
                  "severity":"major",
                  "sentiment":"negative",
                  "evidence_quote":"The screen freezes.",
                  "action_type":"firmware",
                  "improvement_action":"Fix rendering",
                  "return_risk":3,
                  "conversion_risk":3,
                  "confidence":0.8
                }]""");

        assertThrows(BusinessException.class, () -> parser.parse(json, List.of(source)));
    }

    @Test
    void parse_shouldRestoreExactSourceCasingForOtherwiseMatchingEvidence() {
        var source = review(9L, "CarPlay Disconnects every morning.");
        String json = validEmptyJson().replace("\"insights\": []", """
                "insights": [{
                  "user_problem":"Connection drops",
                  "usage_scenario":"daily_commute",
                  "product_module":"carplay",
                  "severity":"major",
                  "sentiment":"negative",
                  "evidence_quote":"carplay disconnects every morning.",
                  "action_type":"firmware",
                  "improvement_action":"Improve reconnect logic",
                  "return_risk":3,
                  "conversion_risk":3,
                  "confidence":0.8
                }]""");

        var parsed = parser.parse(json, List.of(source));

        assertEquals("CarPlay Disconnects every morning.",
                parsed.reviews().get(0).insights().get(0).evidenceQuote());
    }

    @Test
    void parse_shouldRejectMissingInputReviewAndUnknownTaxonomy() {
        assertThrows(BusinessException.class,
                () -> parser.parse("{\"schema_version\":\"review_insight_v1\",\"reviews\":[]}",
                        List.of(review(9L, "Text"))));
        String unknown = validEmptyJson().replace("\"insights\": []", """
                "insights": [{
                  "user_problem":"Issue",
                  "usage_scenario":"racing",
                  "product_module":"display_touch",
                  "severity":"minor",
                  "sentiment":"negative",
                  "evidence_quote":"Text",
                  "action_type":"qa",
                  "improvement_action":"Test",
                  "return_risk":1,
                  "conversion_risk":1,
                  "confidence":0.8
                }]""");
        assertThrows(BusinessException.class, () -> parser.parse(unknown, List.of(review(9L, "Text"))));
    }

    private String validEmptyJson() {
        return """
                {"schema_version":"review_insight_v1","reviews":[{
                  "review_id":9,"has_actionable_issue":false,"insights": []
                }]}
                """;
    }

    private ProductReview review(Long id, String text) {
        return ProductReview.builder().id(id).projectId(11L).asin("B0AAAA1111").reviewText(text).build();
    }
}
