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
    /** 群主键 ID。 */
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

    /** 群创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 群资料最近更新时间。 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 首次持久化前自动补齐创建和更新时间。 */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    /** 每次更新实体前刷新更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
