package cafe.snails.ecomagents.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 图片生成记录实体，映射 image_generation_records 表。
 * <p>记录用户的每一次文生图/图生图操作，包括 prompt、参数、结果路径和耗时。</p>
 */
@Entity
@Table(name = "image_generation_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_image_record_job_output", columnNames = {"job_id", "output_index"}))
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

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "output_index")
    private Integer outputIndex;

    @Column(length = 32)
    private String status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "safe_error_message", length = 500)
    private String safeErrorMessage;

    /** 操作用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 生成模式：GENERATE（文生图） / EDIT（图生图） / SUPER_RESOLUTION（超分） */
    @Column(nullable = false, length = 20)
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

    /** 参考图片路径（图生图模式）：多张以换行分隔 */
    @Column(name = "reference_image_paths", columnDefinition = "TEXT")
    private String referenceImagePaths;

    /** 遮罩图路径（图生图模式） */
    @Column(name = "mask_image_path", length = 500)
    private String maskImagePath;

    /** 原始图片记录 ID，仅超分记录使用 */
    @Column(name = "source_record_id")
    private Long sourceRecordId;

    /** 超分倍率，仅超分记录使用 */
    @Column(name = "upscale_factor")
    private Integer upscaleFactor;
    /** 创建时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 返回规范化后的 resultPath（始终以 /uploads/ 开头）。
     * 兼容旧数据（uploads/edit/xxx.png）和新数据（/uploads/edit/xxx.png）。
     */
    @JsonGetter("resultPath")
    public String getResultPathNormalized() {
        if (resultPath == null || resultPath.isBlank()) return resultPath;
        String[] lines = resultPath.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            String normalized = line.replace("\\", "/");
            // Remove any ./ pattern and ensure clean path
            normalized = normalized.replaceAll("\\./", "");
            if (!normalized.startsWith("/")) normalized = "/" + normalized;
            if (!normalized.startsWith("/uploads/")) normalized = "/uploads" + normalized;
            sb.append(normalized);
        }
        return sb.toString();
    }
}
