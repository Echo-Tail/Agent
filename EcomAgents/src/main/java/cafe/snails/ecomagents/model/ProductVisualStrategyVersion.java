package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_visual_strategy_versions")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class ProductVisualStrategyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "profile_version_id")
    private Long profileVersionId;

    @Column(name = "cognition_version_id", nullable = false)
    private Long cognitionVersionId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "content_scope", nullable = false, length = 64)
    private String contentScope;

    @Column(name = "strategy_json", columnDefinition = "TEXT")
    private String strategyJson;

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