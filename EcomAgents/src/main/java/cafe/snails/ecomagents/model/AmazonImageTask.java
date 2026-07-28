package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 亚马逊商品图片生成任务及其执行状态。
 */
@Entity
@Table(name = "amazon_image_tasks")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class AmazonImageTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "profile_version_id")
    private Long profileVersionId;

    @Column(length = 32)
    private String asin;

    @Column(length = 120)
    private String taskName;

    @Column(nullable = false, length = 32)
    private String marketplace;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(length = 80)
    private String subcategory;

    @Column(nullable = false, length = 50)
    private String imageType;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 30)
    private String sourceType;

    @Column(columnDefinition = "TEXT")
    private String sourceUrls;

    @Column(columnDefinition = "TEXT")
    private String referenceImageUrls;

    @Column(name = "product_facts_json", columnDefinition = "TEXT")
    private String productFactsJson;

    @Column(name = "image_expression_json", columnDefinition = "TEXT")
    private String imageExpressionJson;

    @Column(name = "source_material_facts_json", columnDefinition = "TEXT")
    private String sourceMaterialFactsJson;

    @Column(name = "selected_expression_id")
    private Long selectedExpressionId;

    @Column(name = "checked_material_fact_keys", length = 1000)
    private String checkedMaterialFactKeys;

    @Column(columnDefinition = "TEXT")
    private String promptJson;

    @Column(columnDefinition = "TEXT")
    private String promptText;

    @Column(columnDefinition = "TEXT")
    private String negativePrompt;

    @Column
    private Long modelId;

    @Column
    private Long generationRecordId;

    @Column(name = "image_job_id")
    private Long imageJobId;

    @Column(columnDefinition = "TEXT")
    private String resultPaths;

    @Column(length = 120)
    private String brightDataJobId;

    @Column(length = 30)
    private String collectionStatus;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
