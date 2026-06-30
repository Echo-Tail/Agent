package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用户收藏的表情包，映射 user_emoji_favorites 表。
 * <p>用户可以将内置表情包加入收藏以便快速使用（延后实现）。</p>
 */
@Entity
@Table(name = "user_emoji_favorites",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "emoji_id"}))
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEmojiFavorite {
    /** 收藏记录主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 表情包 ID。 */
    @Column(name = "emoji_id", nullable = false)
    private Long emojiId;

    /** 收藏添加时间。 */
    @Column(nullable = false)
    private LocalDateTime addedAt;

    /** 首次持久化前自动补齐收藏时间。 */
    @PrePersist
    protected void onCreate() {
        if (addedAt == null) addedAt = LocalDateTime.now();
    }
}
