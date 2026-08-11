package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 系统出站 HTTP 代理配置。
 */
@Entity
@Table(name = "proxy_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxySetting {
    @Id
    private Long id;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "proxy_url", length = 500)
    private String proxyUrl;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (id == null) id = 1L;
        if (enabled == null) enabled = false;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
