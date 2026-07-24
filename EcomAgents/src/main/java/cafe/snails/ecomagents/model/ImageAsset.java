package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_assets")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ImageAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long sessionId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 20) private String type;
    @Column(nullable = false, length = 500) private String storageKey;
    @Column(length = 255) private String originalName;
    @Column(nullable = false, length = 100) private String mimeType;
    @Column(nullable = false) private Integer width;
    @Column(nullable = false) private Integer height;
    @Column(nullable = false) private Long fileSize;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
