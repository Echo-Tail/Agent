package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import cafe.snails.ecomagents.repository.FileRecordRepository;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文件上传与访问控制器。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FileController {

    /** 文件存储服务，处理文件读写和元数据存储 */
    private final FileStorageService fileStorageService;
    /** 文件记录仓库，用于查询和更新 file_records 表 */
    private final FileRecordRepository fileRecordRepository;

    /**
     * 上传文件，支持 TXT / MD / PDF / PNG / JPG / JSON / CSV / XML 等格式。
     *
     * @param contextType 对话上下文类型（PRIVATE / AGENT），可选
     * @param contextId   对话上下文 ID（对方用户 ID 或 Agent ID），可选
     */
    @PostMapping("/files/upload")
    public ApiResponse<FileRecord> uploadFile(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "contextType", required = false) String contextType,
                                              @RequestParam(value = "contextId", required = false) Long contextId,
                                              @CurrentUserId Long userId) {
        var result = fileStorageService.uploadFile(file, userId);
        if (result.getCode() == 200 && result.getData() != null && contextType != null) {
            FileRecord record = result.getData();
            record.setContextType(contextType);
            record.setContextId(contextId);
            fileRecordRepository.save(record);
        }
        return result;
    }

    /**
     * 获取指定对话上下文中当前用户上传的文件列表。
     */
    @GetMapping("/files")
    public ApiResponse<List<FileRecord>> listMyFiles(@RequestParam("contextType") String contextType,
                                                     @RequestParam("contextId") Long contextId,
                                                     @CurrentUserId Long userId) {
        return ApiResponse.success(
                fileRecordRepository.findByUploadedByAndContextTypeAndContextIdOrderByUploadedAtDesc(
                        userId, contextType, contextId));
    }

    /**
     * 获取文件元数据。
     */
    @GetMapping("/files/{id}")
    public ApiResponse<FileRecord> getFileRecord(@PathVariable("id") Long id) {
        return fileStorageService.getFileRecord(id);
    }

    /**
     * 下载文件内容（供前端引用）。
     */
    @GetMapping("/files/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable("id") Long id) {
        var result = fileStorageService.getFileRecord(id);
        if (result.getCode() != 200 || result.getData() == null) {
            return ResponseEntity.notFound().build();
        }
        FileRecord record = result.getData();
        try {
            Path filePath = Paths.get(record.getStoredPath());
            byte[] content = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            record.getMimeType() != null ? record.getMimeType() : "application/octet-stream"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            buildContentDisposition(record.getOriginalName()))
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 构建 RFC 5987 兼容的 Content-Disposition header 值，
     * 支持中文等非 ASCII 文件名。
     */
    private static String buildContentDisposition(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", " ");
        return "inline; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }
}
