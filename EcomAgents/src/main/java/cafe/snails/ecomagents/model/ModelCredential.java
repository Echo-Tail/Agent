package cafe.snails.ecomagents.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "model_credentials")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ModelCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 50)
    private String provider;
    @JsonIgnore
    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedSecret;
    @Column(name = "encryption_version", nullable = false)
    @Builder.Default
    private Integer encryptionVersion = 1;
    @Column(name = "masked_hint", nullable = false, length = 32)
    private String maskedHint;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "last_rotated_at", nullable = false)
    private LocalDateTime lastRotatedAt;

    @PrePersist
    void createTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (lastRotatedAt == null) lastRotatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = LocalDateTime.now(); }
}
