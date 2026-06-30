package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_profiles")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class ProductProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String sku;

    @Column(name = "model_number", length = 100)
    private String modelNumber;

    @Column(name = "target_asin", length = 32)
    private String targetAsin;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "markdown_content", columnDefinition = "TEXT")
    private String markdownContent;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_asin", length = 32)
    private String sourceAsin;

    @Column(name = "source_raw_json", columnDefinition = "TEXT")
    private String sourceRawJson;

    @Column(name = "product_facts_json", columnDefinition = "TEXT")
    private String productFactsJson;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "parse_error", columnDefinition = "TEXT")
    private String parseError;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    /** 该产品是否有可用的 Bright Data 快照（非持久化，仅用于前端展示） */
    @Transient
    @Builder.Default
    private boolean snapshotExists = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
