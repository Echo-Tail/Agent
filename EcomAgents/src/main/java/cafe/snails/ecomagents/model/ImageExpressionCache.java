package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_expression_cache", indexes = {
        @Index(name = "idx_iec_url_prompt", columnList = "imageUrlHash, promptHash")
})
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class ImageExpressionCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url_hash", nullable = false, length = 64)
    private String imageUrlHash;

    @Column(name = "image_url", columnDefinition = "TEXT", nullable = false)
    private String imageUrl;

    /** MD5 of 分析提示词 — 同图不同提示词时区分记录 */
    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "expression_json", columnDefinition = "TEXT", nullable = false)
    private String expressionJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
