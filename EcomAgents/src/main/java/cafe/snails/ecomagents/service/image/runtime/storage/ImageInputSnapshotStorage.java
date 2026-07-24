package cafe.snails.ecomagents.service.image.runtime.storage;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.service.image.runtime.command.ImageInputSnapshotSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class ImageInputSnapshotStorage {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    @Value("${file.upload-dir:./uploads}") private String uploadDir;

    public StoredInput store(Long jobId, int index, ImageInputSnapshotSource source) {
        byte[] content = source.content();
        if (content == null || content.length == 0) bad("参考图片不能为空");
        if (content.length > MAX_BYTES) bad("单张参考图片不能超过 10MB");
        DetectedImage detected = detect(content);
        if (source.mimeType() != null && !source.mimeType().isBlank() &&
                !source.mimeType().equalsIgnoreCase(detected.mimeType())) bad("图片内容与 MIME 类型不一致");
        try {
            Path directory = Paths.get(uploadDir, "image-jobs", jobId.toString(), "inputs")
                    .toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String filename = UUID.randomUUID() + detected.extension();
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) bad("非法的图片存储路径");
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            String publicPath = "/uploads/image-jobs/" + jobId + "/inputs/" + filename;
            return new StoredInput(publicPath, detected.mimeType(), content.length, sha256(content));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存任务输入快照失败");
        }
    }

    private DetectedImage detect(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47)
            return new DetectedImage("image/png", ".png");
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff)
            return new DetectedImage("image/jpeg", ".jpg");
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P')
            return new DetectedImage("image/webp", ".webp");
        bad("仅支持 PNG、JPEG 或 WebP 图片");
        throw new IllegalStateException();
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 不可用", e); }
    }
    private void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private record DetectedImage(String mimeType, String extension) {}
    public record StoredInput(String path, String mimeType, long fileSize, String sha256) {}
}
