package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发布到画廊的请求体。
 */
@Data
public class GalleryPublishRequest {
    /** 关联的图片生成记录 ID */
    @NotNull
    private Long recordId;

    /** 作品标题（选填，默认"未命名作品"） */
    private String title;

    /** 品类标签，逗号分隔 */
    private String categoryTags;

    /** 风格标签，逗号分隔 */
    private String styleTags;

    /** 负面提示词（选填） */
    private String negativePrompt;
}
