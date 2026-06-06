package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 画廊作品实体，映射 gallery_items 表。
 * <p>用户从个人历史记录精选发布到公共画廊的作品。独立于 image_generation_records 的生命周期。</p>
 */
@Entity
@Table(name = "gallery_items")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的图片生成记录 ID */
    @Column(name = "record_id", nullable = false)
    private Long recordId;

    /** 发布者用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 作品标题 */
    @Column(length = 200)
    @Builder.Default
    private String title = "未命名作品";

    /** 品类标签，逗号分隔，如 "汽车用品,车载音频" */
    @Column(name = "category_tags", length = 500)
    private String categoryTags;

    /** 风格标签，逗号分隔，如 "科技风,写实" */
    @Column(name = "style_tags", length = 500)
    private String styleTags;

    /** 负面提示词（发布时选填） */
    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    private String negativePrompt;

    /** 状态：PUBLISHED / REMOVED_BY_USER / REMOVED_BY_ADMIN */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PUBLISHED";

    /** 浏览次数 */
    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
