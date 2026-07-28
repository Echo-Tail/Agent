package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 可复用提示词模板及其版本配置。
 */
@Entity
@Table(name = "prompt_library")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class PromptLibrary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false, columnDefinition = "VARCHAR(100)")
    private String category;

    @Column(columnDefinition = "VARCHAR(500)")
    private String tags;

    @Column(name = "cover_path", columnDefinition = "VARCHAR(500)")
    private String coverPath;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
