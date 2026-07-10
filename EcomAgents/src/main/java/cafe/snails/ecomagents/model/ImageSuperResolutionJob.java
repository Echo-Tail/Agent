package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "image_super_resolution_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageSuperResolutionJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long sourceRecordId;

    @Column(length = 20)
    private String sourceType;

    @Column(length = 30)
    private String origin;

    @Column(nullable = false)
    private Integer upscaleFactor;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String sourcePath;

    private Integer sourceWidth;
    private Integer sourceHeight;

    @Column(length = 64)
    private String sourceFingerprint;

    @Column(length = 500)
    private String resultPath;

    private Long resultRecordId;
    private Integer width;
    private Integer height;
    private Long timeCostMs;

    @Column(length = 1000)
    private String errorMessage;

    @Column(unique = true, length = 250)
    private String activeDedupKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}