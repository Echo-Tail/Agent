package cafe.snails.ecomagents.service.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReviewNormalizationServiceTest {
    private final ReviewNormalizationService service = new ReviewNormalizationService(new ObjectMapper());

    @Test
    void normalize_shouldMapBrightDataAmazonReviewFields() {
        var result = service.normalize(new LinkedHashMap<>(Map.of(
                "url", "https://www.amazon.com/dp/B0AAAA1111",
                "review_id", "R-1",
                "rating", "2 out of 5 stars",
                "review_title", "Disconnects",
                "review_text", "CarPlay disconnects every few minutes.",
                "review_date", "2026-07-20T00:00:00.000Z",
                "verified_purchase", true,
                "helpful_count", 7)));

        assertTrue(result.isPresent());
        var review = result.get();
        assertEquals("B0AAAA1111", review.asin());
        assertEquals("R-1", review.externalReviewId());
        assertEquals(new BigDecimal("2"), review.rating());
        assertTrue(review.verifiedPurchase());
        assertEquals(7, review.helpfulCount());
        assertEquals(64, review.contentHash().length());
        assertTrue(review.rawJson().contains("CarPlay disconnects"));
    }

    @Test
    void normalize_shouldRejectRecordsWithoutAsinOrReviewText() {
        assertTrue(service.normalize(Map.of("review_text", "Useful review")).isEmpty());
        assertTrue(service.normalize(Map.of("asin", "B0AAAA1111", "rating", 5)).isEmpty());
    }

    @Test
    void normalize_shouldProduceStableContentHash() {
        var source = Map.<String, Object>of(
                "asin", "b0aaaa1111",
                "review_title", "Title",
                "review_text", "Same review");

        var first = service.normalize(source).orElseThrow();
        var second = service.normalize(source).orElseThrow();

        assertEquals(first.contentHash(), second.contentHash());
        assertEquals("B0AAAA1111", first.asin());
    }
}
