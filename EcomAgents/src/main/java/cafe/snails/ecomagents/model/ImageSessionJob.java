package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_session_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_image_session_job", columnNames = "job_id"),
        @UniqueConstraint(name = "uk_image_session_idempotency", columnNames = {"session_id", "idempotency_key"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ImageSessionJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long sessionId;
    @Column(nullable = false) private Long jobId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ImageSessionOperation operation;
    private Long parentJobId;
    @Column(nullable = false, length = 100) private String idempotencyKey;
    @Column(nullable = false) private LocalDateTime createdAt;
}
