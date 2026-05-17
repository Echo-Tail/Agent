package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件上传与访问控制器。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * 上传文件，支持 TXT / MD / PDF / PNG / JPG / JSON / CSV / XML 等格式。
     */
    @PostMapping("/files/upload")
    public ApiResponse<FileRecord> uploadFile(@RequestParam("file") MultipartFile file) {
        // TODO: get userId from auth context
        return fileStorageService.uploadFile(file, 1L);
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
                            "inline; filename=\"" + record.getOriginalName() + "\"")
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
