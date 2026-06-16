package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.PromptLibrary;
import cafe.snails.ecomagents.repository.PromptLibraryRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptLibraryService {

    private static final long MAX_COVER_SIZE = 5 * 1024 * 1024; // 5MB

    private final PromptLibraryRepository promptLibraryRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public Page<PromptLibrary> list(String category, Long createdBy, Long excludeUser, String keyword, Pageable pageable) {
        return promptLibraryRepository.search(category, createdBy, excludeUser, keyword, pageable);
    }

    public PromptLibrary getById(Long id) {
        return promptLibraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提示词不存在"));
    }

    @Transactional
    public ApiResponse<PromptLibrary> create(String prompt, String category, String tags,
                                              MultipartFile coverFile, Long userId) {
        if (prompt == null || prompt.isBlank()) {
            return ApiResponse.error(400, "提示词不能为空");
        }
        if (category == null || category.isBlank()) {
            return ApiResponse.error(400, "分类不能为空");
        }

        String coverPath = null;
        if (coverFile != null && !coverFile.isEmpty()) {
            coverPath = saveCoverFile(coverFile);
        }

        PromptLibrary entity = PromptLibrary.builder()
                .prompt(prompt.trim())
                .category(category.trim())
                .tags(tags != null ? tags.trim() : null)
                .coverPath(coverPath)
                .createdBy(userId)
                .build();
        promptLibraryRepository.save(entity);
        log.info("[提示词库] 创建成功 id={} userId={} category={}", entity.getId(), userId, category);
        return ApiResponse.success("创建成功", entity);
    }

    @Transactional
    public ApiResponse<PromptLibrary> update(Long id, String prompt, String category, String tags,
                                              MultipartFile coverFile, Long userId) {
        PromptLibrary entity = promptLibraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提示词不存在"));

        if (!entity.getCreatedBy().equals(userId) && !isAdmin(userId)) {
            return ApiResponse.error(403, "没有权限修改此提示词");
        }

        if (prompt != null && !prompt.isBlank()) {
            entity.setPrompt(prompt.trim());
        }
        if (category != null && !category.isBlank()) {
            entity.setCategory(category.trim());
        }
        if (tags != null) {
            entity.setTags(tags.trim());
        }
        if (coverFile != null && !coverFile.isEmpty()) {
            // 删除旧封面文件
            deleteCoverFile(entity.getCoverPath());
            entity.setCoverPath(saveCoverFile(coverFile));
        }

        promptLibraryRepository.save(entity);
        log.info("[提示词库] 更新成功 id={} userId={}", id, userId);
        return ApiResponse.success("更新成功", entity);
    }

    @Transactional
    public ApiResponse<Void> delete(Long id, Long userId) {
        PromptLibrary entity = promptLibraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提示词不存在"));

        if (!entity.getCreatedBy().equals(userId) && !isAdmin(userId)) {
            return ApiResponse.error(403, "没有权限删除此提示词");
        }

        deleteCoverFile(entity.getCoverPath());
        promptLibraryRepository.delete(entity);
        log.info("[提示词库] 删除成功 id={} userId={}", id, userId);
        return ApiResponse.success("删除成功", null);
    }

    // ── 仅保存封面路径引用（素材库/生图历史来源） ──

    @Transactional
    public ApiResponse<PromptLibrary> setCoverRef(Long id, String coverPath, Long userId) {
        PromptLibrary entity = promptLibraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提示词不存在"));

        if (!entity.getCreatedBy().equals(userId) && !isAdmin(userId)) {
            return ApiResponse.error(403, "没有权限修改此提示词");
        }

        deleteCoverFile(entity.getCoverPath());
        entity.setCoverPath(coverPath);
        promptLibraryRepository.save(entity);
        return ApiResponse.success("封面已设置", entity);
    }

    // ── 文件操作 ──

    private String saveCoverFile(MultipartFile file) {
        if (file.getSize() > MAX_COVER_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "封面图片不能超过 5MB");
        }
        String ext = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            int dot = originalName.lastIndexOf(".");
            if (dot >= 0) ext = originalName.substring(dot);
        }
        String storedName = UUID.randomUUID() + ext;
        String subDir = "prompts";
        Path targetDir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storedName);
            file.transferTo(targetPath);
            log.debug("Cover file saved: {}", targetPath);
            return subDir + "/" + storedName;
        } catch (IOException e) {
            log.error("Cover file save failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "封面上传失败");
        }
    }

    private void deleteCoverFile(String coverPath) {
        if (coverPath == null || coverPath.isBlank()) return;
        try {
            Path filePath = Paths.get(uploadDir, coverPath).toAbsolutePath().normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete cover file {}: {}", coverPath, e.getMessage());
        }
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> "admin".equals(u.getRole()))
                .orElse(false);
    }
}
