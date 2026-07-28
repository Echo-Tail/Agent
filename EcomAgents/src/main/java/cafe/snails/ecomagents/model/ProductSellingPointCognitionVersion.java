package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 商品卖点认知分析结果的版本快照。
 */
@Entity
@Table(name = "product_selling_point_cognition_versions")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class ProductSellingPointCognitionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "profile_version_id")
    private Long profileVersionId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "cognition_json", columnDefinition = "TEXT")
    private String cognitionJson;

    @Column(name = "source_facts_hash", length = 64)
    private String sourceFactsHash;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
