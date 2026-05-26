package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.GroupFile;
import cafe.snails.ecomagents.repository.GroupFileRepository;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.FileStorageService;
import cafe.snails.ecomagents.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 群文件控制器。
 */
@RestController
@RequestMapping("/v1/groups/{groupId}/files")
@RequiredArgsConstructor
public class GroupFileController {

    private final GroupFileRepository groupFileRepository;
    private final GroupService groupService;
    private final FileStorageService fileStorageService;

    /** 上传文件到群 */
    @PostMapping
    public ApiResponse<GroupFile> uploadFile(@PathVariable Long groupId,
                                             @RequestParam("file") MultipartFile file,
                                             @CurrentUserId Long userId) {
        if (!groupService.isMember(groupId, userId)) {
            return ApiResponse.error(403, "你不是群成员");
        }

        // 复用 FileStorageService 的文件校验和存储逻辑
        var uploadResult = fileStorageService.uploadFile(file, userId);
        if (uploadResult.getCode() != 200) {
            return ApiResponse.error(uploadResult.getCode(), uploadResult.getMessage());
        }
        var fileRecord = uploadResult.getData();

        // 创建群文件记录
        GroupFile gf = GroupFile.builder()
                .groupId(groupId)
                .uploaderId(userId)
                .originalName(fileRecord.getOriginalName())
                .fileSize(fileRecord.getFileSize())
                .mimeType(fileRecord.getMimeType())
                .storagePath(fileRecord.getStoredPath())
                .build();
        gf = groupFileRepository.save(gf);

        return ApiResponse.success("文件上传成功", gf);
    }

    /** 群文件列表 */
    @GetMapping
    public ApiResponse<List<GroupFile>> listFiles(@PathVariable Long groupId) {
        return ApiResponse.success(groupFileRepository.findByGroupIdOrderByUploadedAtDesc(groupId));
    }

    /** 下载群文件 */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        var opt = groupFileRepository.findById(fileId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        GroupFile gf = opt.get();
        try {
            Path filePath = Paths.get(gf.getStoragePath());
            byte[] data = Files.readAllBytes(filePath);
            ByteArrayResource resource = new ByteArrayResource(data);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(gf.getMimeType() != null ? gf.getMimeType() : "application/octet-stream"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + gf.getOriginalName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
