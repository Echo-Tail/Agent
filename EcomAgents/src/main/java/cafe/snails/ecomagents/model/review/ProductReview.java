package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_reviews")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "collection_batch_id")
    private Long collectionBatchId;
    @Column(nullable = false, length = 20)
    private String asin;
    @Column(name = "external_review_id", length = 100)
    private String externalReviewId;
    @Column(precision = 2, scale = 1)
    private BigDecimal rating;
    @Column(columnDefinition = "TEXT")
    private String title;
    @Column(name = "review_text", nullable = false, columnDefinition = "TEXT")
    private String reviewText;
    @Column(name = "review_date")
    private LocalDateTime reviewDate;
    @Column(name = "verified_purchase", nullable = false)
    private Boolean verifiedPurchase;
    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount;
    @Column(name = "reviewer_name", length = 200)
    private String reviewerName;
    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "raw_json", nullable = false, columnDefinition = "TEXT")
    private String rawJson;
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @PrePersist void onCreate() {
        if (verifiedPurchase == null) verifiedPurchase = false;
        if (helpfulCount == null) helpfulCount = 0;
        if (collectedAt == null) collectedAt = LocalDateTime.now();
    }
}
