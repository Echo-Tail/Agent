package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "public_assets")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
public class PublicAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, columnDefinition = "VARCHAR(255)")
    private String fileName;

    @Column(name = "file_path", nullable = false, columnDefinition = "VARCHAR(500)")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", columnDefinition = "VARCHAR(50)")
    private String mimeType;

    @Column(name = "content_hash", columnDefinition = "VARCHAR(64)")
    private String contentHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private AssetSpace space;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
