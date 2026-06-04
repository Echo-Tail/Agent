package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 画廊作品响应 DTO（包含关联的图片记录信息）。
 */
@Data
@Builder
@AllArgsConstructor
public class GalleryItemResponse {
    private Long id;
    private Long recordId;
    private Long userId;

    // 画廊元数据
    private String title;
    private String categoryTags;
    private String styleTags;
    private String negativePrompt;
    private String status;
    private Integer viewCount;

    // 关联的图片记录信息
    private String imageUrl;
    private String prompt;
    private String revisedPrompt;
    private String size;
    private String quality;
    private String mode;

    // 用户信息
    private String authorName;

    // 时间
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
