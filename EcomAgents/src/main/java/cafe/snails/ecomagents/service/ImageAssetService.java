package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.image.ImageSessionDtos.AssetResponse;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.ImageAsset;
import cafe.snails.ecomagents.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
/** 负责图片会话素材的查询、保存与删除。 */
public class ImageAssetService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/bmp");
    private final ImageAssetRepository assetRepository;
    private final ImageSessionRepository sessionRepository;

    @Transactional
    public AssetResponse upload(Long sessionId, MultipartFile file, String type, Long userId) {
        requireSession(sessionId, userId);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择图片文件");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能超过 20MB");
        String mimeType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(mimeType)) throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 JPEG、PNG、BMP 图片");

        try {
            byte[] bytes = file.getBytes();
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容无效或格式不匹配");
            String extension = switch (mimeType) { case "image/png" -> ".png"; case "image/bmp" -> ".bmp"; default -> ".jpg"; };
            String filename = UUID.randomUUID() + extension;
            Path root = Paths.get("uploads", "image-sessions", sessionId.toString()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(filename).normalize();
            if (!target.startsWith(root)) throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文件路径");
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);

            try {
                var asset = ImageAsset.builder().sessionId(sessionId).userId(userId)
                        .type(normalizeType(type)).storageKey("/uploads/image-sessions/" + sessionId + "/" + filename)
                        .originalName(file.getOriginalFilename()).mimeType(mimeType).width(image.getWidth()).height(image.getHeight())
                        .fileSize((long) bytes.length).sha256(sha256(bytes)).createdAt(LocalDateTime.now()).build();
                return toResponse(assetRepository.save(asset));
            } catch (RuntimeException error) {
                Files.deleteIfExists(target);
                throw error;
            }
        } catch (BusinessException error) {
            throw error;
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片保存失败");
        }
    }

    public AssetResponse get(Long id, Long userId) { return toResponse(requireAsset(id, userId)); }

    @Transactional
    public AssetResponse delete(Long id, Long userId) {
        var asset = requireAsset(id, userId);
        asset.setDeletedAt(LocalDateTime.now());
        return toResponse(assetRepository.save(asset));
    }

    public List<AssetResponse> list(Long sessionId) {
        return assetRepository.findBySessionIdAndDeletedAtIsNullOrderByCreatedAt(sessionId).stream().map(this::toResponse).toList();
    }

    private void requireSession(Long id, Long userId) {
        if (sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId).isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "图像会话不存在");
    }
    private ImageAsset requireAsset(Long id, Long userId) {
        return assetRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片资产不存在"));
    }
    private String normalizeType(String type) {
        String value = Optional.ofNullable(type).orElse("ORIGINAL").toUpperCase(Locale.ROOT);
        if (!Set.of("ORIGINAL", "GENERATED", "MASK", "UPSCALED").contains(value))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的图片资产类型");
        return value;
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public AssetResponse toResponse(ImageAsset value) {
        return new AssetResponse(value.getId(), value.getSessionId(), value.getType(), value.getMimeType(), value.getWidth(),
                value.getHeight(), value.getFileSize(), value.getStorageKey(), value.getCreatedAt());
    }
}
