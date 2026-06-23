package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_expression_cache", indexes = {
        @Index(name = "idx_iec_url_hash", columnList = "imageUrlHash", unique = true)
})
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class ImageExpressionCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url_hash", nullable = false, length = 64, unique = true)
    private String imageUrlHash;

    @Column(name = "image_url", columnDefinition = "TEXT", nullable = false)
    private String imageUrl;

    @Column(name = "expression_json", columnDefinition = "TEXT", nullable = false)
    private String expressionJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
