package cafe.snails.ecomagents.dto.image;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 图片工作区会话相关的数据传输对象集合。
 */
public final class ImageSessionDtos {
    private ImageSessionDtos() {}

    /** 创建图片会话的请求。 */
    public record CreateRequest(@NotBlank @Size(max = 100) String title) {}
    /** 更新图片会话标题的请求。 */
    public record UpdateRequest(@NotBlank @Size(max = 100) String title) {}
    /** 图片会话的摘要响应。 */
    public record SessionResponse(Long id, String title, String status, Long thumbnailAssetId,
                                  long assetCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    /** 保存画布快照的请求。 */
    public record SaveCanvasRequest(@NotNull @PositiveOrZero Long revision,
                                    @NotNull @Positive Integer schemaVersion,
                                    @NotNull Map<String, Object> snapshot) {}
    /** 图片会话画布的响应。 */
    public record CanvasResponse(Long sessionId, Long revision, Integer schemaVersion,
                                 Map<String, Object> snapshot, LocalDateTime updatedAt) {}
    /** 图片会话内素材的响应。 */
    public record AssetResponse(Long id, Long sessionId, String type, String mimeType, Integer width,
                                Integer height, Long fileSize, String url, LocalDateTime createdAt) {}
    /** 图片工作区的聚合响应。 */
    public record WorkspaceResponse(SessionResponse session, CanvasResponse canvas, List<AssetResponse> assets) {}
}
