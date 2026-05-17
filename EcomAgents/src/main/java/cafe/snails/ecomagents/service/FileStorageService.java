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

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "md", "pdf", "png", "jpg", "jpeg", "gif", "json", "csv", "xml"
    );
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    private final FileRecordRepository fileRecordRepository;

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

            // 生成唯一文件名，防止覆盖
            String storedName = UUID.randomUUID() + "_" + originalName;
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
     * 根据记录 ID 获取文件元数据。
     */
    public ApiResponse<FileRecord> getFileRecord(Long id) {
        return fileRecordRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "文件不存在"));
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }
}
