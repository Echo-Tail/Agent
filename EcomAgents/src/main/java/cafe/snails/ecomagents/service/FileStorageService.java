package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储服务，处理上传文件的磁盘写入和元数据持久化。
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    /** 允许上传或由 Agent 生成的文件扩展名白名单。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "md", "pdf", "docx", "xlsx", "csv", "json",
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg"
    );
    /** 单个上传文件大小上限。 */
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    /** 文件元数据仓库。 */
    private final FileRecordRepository fileRecordRepository;

    /** 文件上传目录，可通过 file.upload-dir 配置覆盖。 */
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 上传文件，保存到磁盘并记录元数据。
     *
     * @param file   上传的文件
     * @param userId 上传者 ID
     * @return ApiResponse 包含 FileRecord 或错误信息
     */
    public ApiResponse<FileRecord> uploadFile(MultipartFile file, Long userId) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        String ext = getExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ApiResponse.error(400, "不支持的文件类型: ." + ext
                    + "，允许的类型: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "文件大小超出限制（最大 20MB）");
        }

        try {
            // 确保上传目录存在
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // 生成唯一文件名（清洗原始文件名防止路径穿越）
            String safeName = sanitizeFileName(originalName);
            String storedName = UUID.randomUUID() + "_" + safeName;
            Path targetPath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 保存元数据
            FileRecord record = FileRecord.builder()
                    .originalName(originalName)
                    .storedPath(targetPath.toString())
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .uploadedAt(LocalDateTime.now())
                    .uploadedBy(userId)
                    .build();

            FileRecord saved = fileRecordRepository.save(record);
            log.info("File uploaded: {} -> {} ({} bytes)", originalName, targetPath, file.getSize());

            return ApiResponse.success("文件上传成功", saved);
        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage());
            return ApiResponse.error(500, "文件存储失败: " + e.getMessage());
        }
    }

    /**
     * 保存字符串内容为文件（供 Agent 生成的文件使用，非 MultipartFile 上传）。
     *
     * @param content      文件内容字符串
     * @param originalName 原始文件名
     * @param userId       上传者用户 ID
     * @return FileRecord 或 null（失败时）
     */
    public FileRecord saveContentAsFile(String content, String originalName, Long userId) {
        if (content == null || content.isBlank()) return null;

        String ext = getExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            log.warn("Unsupported file type for agent-generated file: .{}", ext);
            return null;
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // 生成唯一文件名（清洗原始文件名防止路径穿越）
            String safeName = sanitizeFileName(originalName);
            String storedName = UUID.randomUUID() + "_" + safeName;
            Path targetPath = uploadPath.resolve(storedName);
            Files.writeString(targetPath, content);

            String mimeType = switch (ext) {
                case "md" -> "text/markdown";
                case "txt" -> "text/plain";
                case "json" -> "application/json";
                case "csv" -> "text/csv";
                case "xml" -> "application/xml";
                case "html" -> "text/html";
                default -> "application/octet-stream";
            };

            FileRecord record = FileRecord.builder()
                    .originalName(originalName)
                    .storedPath(targetPath.toString())
                    .fileSize((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .mimeType(mimeType)
                    .uploadedAt(LocalDateTime.now())
                    .uploadedBy(userId)
                    .build();

            FileRecord saved = fileRecordRepository.save(record);
            log.info("Agent-generated file saved: {} -> {} ({} bytes)", originalName, targetPath, record.getFileSize());
            return saved;
        } catch (IOException e) {
            log.error("Failed to save agent-generated file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据记录 ID 获取文件元数据。
     */
    public ApiResponse<FileRecord> getFileRecord(Long id) {
        return fileRecordRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "文件不存在"));
    }

    /**
     * 清洗文件名，去除路径分隔符和「..」穿越，仅保留纯文件名部分。
     */
    private String sanitizeFileName(String fileName) {
        // 取最后一个 / 或 \ 之后的部分（纯文件名）
        int lastSep = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSep >= 0) {
            fileName = fileName.substring(lastSep + 1);
        }
        // 替换残留的可能有问题的字符
        return fileName.replaceAll("[\0<>:\"|?*]", "_");
    }

    /**
     * 提取文件扩展名；无扩展名时返回空字符串。
     */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }
}
