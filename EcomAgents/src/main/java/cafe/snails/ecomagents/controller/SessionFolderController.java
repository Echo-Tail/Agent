package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.SessionFolder;
import cafe.snails.ecomagents.repository.SessionFolderRepository;
import cafe.snails.ecomagents.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话文件夹控制器，支持文件夹的 CRUD。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SessionFolderController {

    private final SessionFolderRepository folderRepository;

    /** 获取所有文件夹（当前用户） */
    @GetMapping("/session-folders")
    public ApiResponse<List<SessionFolder>> listFolders(@CurrentUserId Long userId) {
        return ApiResponse.success(folderRepository.findByUserIdOrderByOrderNum(userId));
    }

    /** 创建文件夹 */
    @PostMapping("/session-folders")
    public ApiResponse<SessionFolder> createFolder(
            @RequestBody Map<String, Object> body,
            @CurrentUserId Long userId) {
        SessionFolder folder = SessionFolder.builder()
                .name((String) body.get("name"))
                .userId(userId)
                .build();
        SessionFolder saved = folderRepository.save(folder);
        return ApiResponse.success("文件夹创建成功", saved);
    }

    /** 更新文件夹名称 */
    @PutMapping("/session-folders/{id}")
    public ApiResponse<SessionFolder> updateFolder(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            @CurrentUserId Long userId) {
        return folderRepository.findByIdAndUserId(id, userId)
                .map(folder -> {
                    if (body.get("name") != null) folder.setName((String) body.get("name"));
                    SessionFolder saved = folderRepository.save(folder);
                    return ApiResponse.<SessionFolder>success("文件夹已更新", saved);
                })
                .orElse(ApiResponse.error(404, "文件夹不存在"));
    }

    /** 删除文件夹 */
    @Transactional
    @DeleteMapping("/session-folders/{id}")
    public ApiResponse<Void> deleteFolder(
            @PathVariable("id") Long id,
            @CurrentUserId Long userId) {
        return folderRepository.findByIdAndUserId(id, userId)
                .map(folder -> {
                    folderRepository.delete(folder);
                    return ApiResponse.<Void>success("文件夹已删除", null);
                })
                .orElse(ApiResponse.error(404, "文件夹不存在"));
    }
}
