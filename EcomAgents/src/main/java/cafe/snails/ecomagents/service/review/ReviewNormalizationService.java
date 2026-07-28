package cafe.snails.ecomagents.service.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

@Service
@RequiredArgsConstructor
/** 对采集的商品评论进行清洗、去重和标准化。 */
public class ReviewNormalizationService {
    private static final Pattern ASIN_IN_URL = Pattern.compile("(?i)/(?:dp|product-reviews)/([A-Z0-9]{10})(?:[/?]|$)");
    private final ObjectMapper objectMapper;

    public Optional<NormalizedReview> normalize(Map<String, Object> source) {
        String asin = upper(firstText(source, "asin", "product_asin"));
        String url = firstText(source, "url", "product_url", "source_url");
        if (asin == null && url != null) {
            Matcher matcher = ASIN_IN_URL.matcher(url);
            if (matcher.find()) asin = matcher.group(1).toUpperCase(Locale.ROOT);
        }
        String text = firstText(source, "review_text", "review", "body", "content");
        if (asin == null || !asin.matches("[A-Z0-9]{10}") || text == null || text.isBlank()) {
            return Optional.empty();
        }
        String title = firstText(source, "review_title", "title", "headline");
        String externalId = firstText(source, "review_id", "id", "reviewId");
        String normalizedText = text.trim();
        return Optional.of(new NormalizedReview(
                asin,
                blankToNull(externalId),
                decimal(first(source, "rating", "review_rating", "stars")),
                blankToNull(title),
                normalizedText,
                date(first(source, "review_date", "date", "created_at")),
                bool(first(source, "verified_purchase", "verified")),
                integer(first(source, "helpful_count", "helpful", "helpful_votes")),
                firstText(source, "reviewer_name", "author_name", "author"),
                url,
                sha256(asin + "\n" + Objects.toString(title, "").trim() + "\n" + normalizedText),
                json(source)));
    }

    private Object first(Map<String, Object> source, String... keys) {
        for (String key : keys) if (source.containsKey(key) && source.get(key) != null) return source.get(key);
        return null;
    }

    private String firstText(Map<String, Object> source, String... keys) {
        Object value = first(source, keys);
        return value == null ? null : blankToNull(String.valueOf(value));
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        try {
            Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(String.valueOf(value));
            return matcher.find() ? new BigDecimal(matcher.group()) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int integer(Object value) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        if (value == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value).replaceAll("[^0-9\\-]", "")));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        return value != null && Set.of("true", "yes", "1", "verified").contains(
                String.valueOf(value).trim().toLowerCase(Locale.ROOT));
    }

    private LocalDateTime date(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        try { return OffsetDateTime.parse(text).toLocalDateTime(); } catch (DateTimeParseException ignored) {}
        try { return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDateTime(); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(text); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(text).atStartOfDay(); } catch (DateTimeParseException ignored) {}
        return null;
    }

    private String json(Map<String, Object> source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record NormalizedReview(
            String asin,
            String externalReviewId,
            BigDecimal rating,
            String title,
            String reviewText,
            LocalDateTime reviewDate,
            boolean verifiedPurchase,
            int helpfulCount,
            String reviewerName,
            String sourceUrl,
            String contentHash,
            String rawJson) {}
}
