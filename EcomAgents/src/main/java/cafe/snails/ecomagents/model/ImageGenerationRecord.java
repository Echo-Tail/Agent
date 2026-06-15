package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 图片生成记录实体，映射 image_generation_records 表。
 * <p>记录用户的每一次文生图/图生图操作，包括 prompt、参数、结果路径和耗时。</p>
 */
@Entity
@Table(name = "image_generation_records")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 生成模式：GENERATE（文生图） / EDIT（图生图） */
    @Column(nullable = false, length = 10)
    private String mode;

    /** 用户输入的提示词 */
    @Column(columnDefinition = "TEXT")
    private String prompt;

    /** API 返回的改写后提示词 */
    @Column(name = "revised_prompt", columnDefinition = "TEXT")
    private String revisedPrompt;

    /** 图片尺寸，如 1024x1024 */
    @Column(length = 20)
    private String size;

    /** 图片质量：low / medium / high / auto */
    @Column(length = 10)
    private String quality;

    /** 服务器端图片存储路径 */
    @Column(name = "result_path", nullable = false, length = 500)
    private String resultPath;

    /** API 调用耗时（毫秒） */
    @Column(name = "time_cost_ms")
    private Long timeCostMs;

    /** 图片宽度（像素） */
    @Column
    private Integer width;

    /** 图片高度（像素） */
    @Column
    private Integer height;

    /** 创建时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
