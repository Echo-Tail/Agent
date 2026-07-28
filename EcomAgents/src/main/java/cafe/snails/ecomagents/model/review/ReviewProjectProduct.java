package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_project_products")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewProjectProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 20)
    private String asin;
    @Column(nullable = false, length = 20)
    private String role;
    @Column(name = "product_name", length = 300)
    private String productName;
    @Column(name = "review_limit", nullable = false)
    private Integer reviewLimit;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
