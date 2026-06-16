package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.AssetSpace;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.model.PublicAsset;
import cafe.snails.ecomagents.repository.AssetSpaceRepository;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import cafe.snails.ecomagents.repository.PublicAssetRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    private final AssetSpaceRepository assetSpaceRepository;
    private final PublicAssetRepository publicAssetRepository;
    private final UserRepository userRepository;
    private final ImageGenerationRecordRepository imageGenerationRecordRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @PostConstruct
    public void initDefaultSpace() {
        if (!assetSpaceRepository.existsByName("未分类")) {
            AssetSpace space = AssetSpace.builder()
                    .name("未分类")
                    .description("系统默认素材空间")
                    .createdBy(0L)
                    .build();
            assetSpaceRepository.save(space);
            log.info("Created default asset space: 未分类");
        }
    }

    // ========== Asset Spaces ==========

    public List<AssetSpace> listSpaces() {
        return assetSpaceRepository.findAll();
    }

    @Transactional
    public ApiResponse<AssetSpace> createSpace(String name, String description, Long userId) {
        if (name == null || name.isBlank()) {
            return ApiResponse.error(400, "空间名称不能为空");
        }
        if (assetSpaceRepository.existsByName(name.trim())) {
            return ApiResponse.error(400, "空间名称已存在");
        }
        AssetSpace space = AssetSpace.builder()
                .name(name.trim())
                .description(description != null ? description.trim() : null)
                .createdBy(userId)
                .build();
        assetSpaceRepository.save(space);
        return ApiResponse.success("创建成功", space);
    }

    @Transactional
    public ApiResponse<AssetSpace> updateSpace(Long id, String name, String description, Long userId) {
        AssetSpace space = assetSpaceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "素材空间不存在"));
        if (!space.getCreatedBy().equals(userId) && !isAdmin(userId)) {
            return ApiResponse.error(403, "没有权限修改此素材空间");
        }
        if (name != null && !name.isBlank() && !name.trim().equals(space.getName())) {
            if (assetSpaceRepository.existsByName(name.trim())) {
                return ApiResponse.error(400, "空间名称已存在");
            }
            space.setName(name.trim());
        }
        if (description != null) {
            space.setDescription(description.trim());
        }
        assetSpaceRepository.save(space);
        return ApiResponse.success("修改成功", space);
    }

    @Transactional
    public ApiResponse<Void> deleteSpace(Long id, Long userId) {
        AssetSpace space = assetSpaceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "素材空间不存在"));
        // 保护系统默认空间
        if ("未分类".equals(space.getName())) {
            return ApiResponse.error(403, "系统默认空间不能删除");
        }
        if (!space.getCreatedBy().equals(userId) && !isAdmin(userId)) {
            return ApiResponse.error(403, "没有权限删除此素材空间");
        }
        assetSpaceRepository.delete(space);
        return ApiResponse.success("删除成功", null);
    }

    // ========== Assets ==========

    public Page<PublicAsset> listAssets(Long spaceId, String keyword, Long uploadedBy, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return publicAssetRepository.search(spaceId, keyword, uploadedBy, startDate, endDate, pageable);
    }

    @Transactional
    public ApiResponse<PublicAsset> uploadAsset(MultipartFile file, Long spaceId, Long userId) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "请选择要上传的图片");
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            return ApiResponse.error(400, "仅支持 JPEG、PNG、WebP 格式");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "图片大小不能超过 20MB");
        }

        AssetSpace space = null;
        if (spaceId != null) {
            space = assetSpaceRepository.findById(spaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "素材空间不存在"));
        }

        // Compute SHA-256 hash for deduplication
        String contentHash;
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fileBytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            contentHash = hex.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to compute file hash: {}", e.getMessage());
            return ApiResponse.error(500, "文件处理失败");
        }

        // Check for existing asset with same content hash
        var existing = publicAssetRepository.findByContentHash(contentHash);
        if (existing.isPresent()) {
            log.info("Duplicate upload skipped (hash={}), returning existing asset id={}", contentHash, existing.get().getId());
            return ApiResponse.success("上传成功（检测到重复，使用已有素材）", existing.get());
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) originalName = "asset.png";

        String ext = "";
        int dot = originalName.lastIndexOf(".");
        if (dot >= 0) ext = originalName.substring(dot);
        String storedName = UUID.randomUUID() + ext;
        String subDir = "assets";
        Path targetDir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storedName);
            Files.write(targetPath, fileBytes);

            PublicAsset asset = PublicAsset.builder()
                    .fileName(originalName)
                    .filePath(subDir + "/" + storedName)
                    .fileSize(file.getSize())
                    .mimeType(mimeType)
                    .space(space)
                    .uploadedBy(userId)
                    .contentHash(contentHash)
                    .build();
            // 读取图片尺寸
            int[] dims = readImageSize(targetPath);
            if (dims != null) {
                asset.setWidth(dims[0]);
                asset.setHeight(dims[1]);
            }
            publicAssetRepository.save(asset);
            return ApiResponse.success("上传成功", asset);
        } catch (IOException e) {
            log.error("Asset upload failed: {}", e.getMessage());
            return ApiResponse.error(500, "图片上传失败");
        }
    }

    @Transactional
    public ApiResponse<PublicAsset> moveAsset(Long id, Long spaceId, Long userId) {
        PublicAsset asset = publicAssetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "素材不存在"));
        if (!asset.getUploadedBy().equals(userId) && !isAdmin(userId)) {
            return ApiResponse.error(403, "没有权限移动此素材");
        }
        AssetSpace targetSpace = null;
        if (spaceId != null) {
            targetSpace = assetSpaceRepository.findById(spaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "目标素材空间不存在"));
        }
        asset.setSpace(targetSpace);
        publicAssetRepository.save(asset);
        return ApiResponse.success("移动成功", asset);
    }

    @Transactional
    public ApiResponse<Void> deleteAsset(Long id, Long userId) {
        PublicAsset asset = publicAssetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "素材不存在"));
        if (!asset.getUploadedBy().equals(userId) && !isAdmin(userId)) {
            return ApiResponse.error(403, "没有权限删除此素材");
        }
        try {
            Path filePath = Paths.get(uploadDir, asset.getFilePath()).toAbsolutePath().normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete asset file: {}", e.getMessage());
        }
        publicAssetRepository.delete(asset);
        return ApiResponse.success("删除成功", null);
    }

    @Transactional
    public ApiResponse<PublicAsset> importFromRecord(Long recordId, Long spaceId, Long userId) {
        ImageGenerationRecord record = imageGenerationRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "生成记录不存在"));

        AssetSpace space = null;
        if (spaceId != null) {
            space = assetSpaceRepository.findById(spaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "素材空间不存在"));
        }

        String sourcePath = record.getResultPath();
        log.info("importFromRecord: recordId={}, resultPath='{}'", recordId, sourcePath);

        // 尝试多种可能的路径找到源文件
        Path sourceFile = tryResolveSource(sourcePath);
        if (sourceFile == null) {
            String normalized = sourcePath.replace("\\", "/");
            String stripped = normalized.contains("uploads/")
                    ? normalized.substring(normalized.indexOf("uploads/") + 8)
                    : normalized;
            log.info("importFromRecord: retry with stripped path='{}'", stripped);
            sourceFile = tryResolveSource(stripped);
        }
        if (sourceFile == null) {
            log.error("importFromRecord: source file not found for recordId={}, resultPath='{}'", recordId, sourcePath);
            return ApiResponse.error(404, "原始图片文件不存在");
        }
        log.info("importFromRecord: resolved source file at '{}'", sourceFile);

        String ext = "";
        String name = sourceFile.getFileName().toString();
        int dot = name.lastIndexOf(".");
        if (dot >= 0) ext = name.substring(dot);
        String storedName = UUID.randomUUID() + ext;
        String subDir = "assets";
        Path targetDir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storedName);
            Files.copy(sourceFile, targetPath);

            PublicAsset asset = PublicAsset.builder()
                    .fileName(name)
                    .filePath(subDir + "/" + storedName)
                    .fileSize(Files.size(targetPath))
                    .mimeType("image/png")
                    .space(space)
                    .uploadedBy(userId)
                    .build();
            int[] dims = readImageSize(targetPath);
            if (dims != null) {
                asset.setWidth(dims[0]);
                asset.setHeight(dims[1]);
            }
            publicAssetRepository.save(asset);
            return ApiResponse.success("导入成功", asset);
        } catch (IOException e) {
            log.error("Import from record failed: {}", e.getMessage());
            return ApiResponse.error(500, "导入失败");
        }
    }

    /**
     * 尝试多种路径解析源图片文件。
     * 返回存在的文件路径，全部失败则返回 null。
     */
    private Path tryResolveSource(String path) {
        // 尝试 1: 相对于 uploadDir
        Path p1 = Paths.get(uploadDir, path).toAbsolutePath().normalize();
        log.debug("tryResolveSource: attempt 1 (uploadDir + path) -> {} exists={}", p1, Files.exists(p1));
        if (Files.exists(p1)) return p1;
        // 尝试 2: 作为绝对/相对路径直接解析
        Path p2 = Paths.get(path).toAbsolutePath().normalize();
        log.debug("tryResolveSource: attempt 2 (path as-is) -> {} exists={}", p2, Files.exists(p2));
        if (Files.exists(p2)) return p2;
        // 尝试 3: 去掉前导目录后的 path（兼容部分历史数据）
        String clean = path.replace("\\", "/").replaceFirst("^.*?uploads/", "");
        if (!clean.equals(path.replace("\\", "/"))) {
            Path p3 = Paths.get(uploadDir, clean).toAbsolutePath().normalize();
            log.debug("tryResolveSource: attempt 3 (stripped) -> {} exists={}", p3, Files.exists(p3));
            if (Files.exists(p3)) return p3;
        }
        return null;
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> "admin".equals(u.getRole()))
                .orElse(false);
    }

    /** 从图片文件读取宽高，失败时返回 null */
    private int[] readImageSize(Path path) {
        try (var in = javax.imageio.ImageIO.createImageInputStream(path.toFile())) {
            var readers = javax.imageio.ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to read image size from {}: {}", path, e.getMessage());
        }
        return null;
    }
}
