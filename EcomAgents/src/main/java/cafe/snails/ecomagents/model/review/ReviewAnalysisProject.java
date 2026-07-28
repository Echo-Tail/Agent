package cafe.snails.ecomagents.model.review;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_analysis_projects")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewAnalysisProject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "profile_id")
    private Long profileId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 20)
    private String marketplace;
    @Column(nullable = false, length = 50)
    private String category;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        var now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
