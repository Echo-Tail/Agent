package cafe.snails.ecomagents.service.image.runtime.command;

import cafe.snails.ecomagents.model.ImageJobInputRole;
import cafe.snails.ecomagents.model.ImageJobInputSourceType;

public record ImageInputSnapshotSource(ImageJobInputRole role, ImageJobInputSourceType sourceType,
        Long sourceId, String originalFilename, String mimeType, byte[] content) {
    public ImageInputSnapshotSource {
        content = content == null ? null : content.clone();
    }
    @Override public byte[] content() { return content == null ? null : content.clone(); }
}
