package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 群基本信息，映射 chat_groups 表。
 */
@Entity
@Table(name = "chat_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 群名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 群头像图片 URL */
    @Column(length = 500)
    private String avatar;

    /** 创建者用户 ID */
    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
