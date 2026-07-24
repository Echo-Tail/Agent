package cafe.snails.ecomagents.dto.image;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ImageSessionDtos {
    private ImageSessionDtos() {}

    public record CreateRequest(@NotBlank @Size(max = 100) String title) {}
    public record UpdateRequest(@NotBlank @Size(max = 100) String title) {}
    public record SessionResponse(Long id, String title, String status, Long thumbnailAssetId,
                                  long assetCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record SaveCanvasRequest(@NotNull @PositiveOrZero Long revision,
                                    @NotNull @Positive Integer schemaVersion,
                                    @NotNull Map<String, Object> snapshot) {}
    public record CanvasResponse(Long sessionId, Long revision, Integer schemaVersion,
                                 Map<String, Object> snapshot, LocalDateTime updatedAt) {}
    public record AssetResponse(Long id, Long sessionId, String type, String mimeType, Integer width,
                                Integer height, Long fileSize, String url, LocalDateTime createdAt) {}
    public record WorkspaceResponse(SessionResponse session, CanvasResponse canvas, List<AssetResponse> assets) {}
}
