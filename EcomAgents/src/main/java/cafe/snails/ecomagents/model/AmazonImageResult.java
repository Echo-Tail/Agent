package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "amazon_image_results")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class AmazonImageResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "image_path", nullable = false, length = 500)
    private String imagePath;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "image_index", nullable = false)
    private Integer imageIndex;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
