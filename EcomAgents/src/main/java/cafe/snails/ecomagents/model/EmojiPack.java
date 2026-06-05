package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 内置表情包，映射 emoji_packs 表。
 */
@Entity
@Table(name = "emoji_packs")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmojiPack {
    /** 表情包主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 表情包名称（如"黄脸""动物"） */
    @Column(nullable = false, length = 50)
    private String name;

    /** 表情图片 URL */
    @Column(nullable = false, length = 500)
    private String imageUrl;

    /** 分类标签 */
    @Column(length = 50)
    private String category;

    /** 表情包创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 首次持久化前自动补齐创建时间。 */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
