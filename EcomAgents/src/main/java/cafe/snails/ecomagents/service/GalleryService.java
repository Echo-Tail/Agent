package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.GalleryItemResponse;
import cafe.snails.ecomagents.dto.GalleryPublishRequest;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.GalleryItem;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.GalleryItemRepository;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 画廊服务 — 管理精选作品的发布、浏览、取消发布和管理员下架。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_REMOVED_BY_USER = "REMOVED_BY_USER";
    private static final String STATUS_REMOVED_BY_ADMIN = "REMOVED_BY_ADMIN";

    private final GalleryItemRepository galleryItemRepository;
    private final ImageGenerationRecordRepository recordRepository;
    private final UserRepository userRepository;

    /**
     * 发布作品到画廊。
     *
     * @param request 发布请求
     * @param userId  发布者用户 ID
     * @return 创建后的画廊作品
     */
    @Transactional
    public GalleryItem publish(GalleryPublishRequest request, Long userId) {
        // 验证记录存在且属于当前用户
        ImageGenerationRecord record = recordRepository.findById(request.getRecordId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权发布他人的图片记录");
        }

        // 检查是否已发布（防止重复发布）
        if (galleryItemRepository.existsByRecordIdAndStatus(request.getRecordId(), STATUS_PUBLISHED)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该图片已发布到画廊");
        }

        // 查找是否有之前取消发布/下架的行，有则复用（更新回 PUBLISHED）
        List<GalleryItem> removedItems = galleryItemRepository.findByRecordIdAndUserIdAndStatusNot(
                request.getRecordId(), userId, STATUS_PUBLISHED);
        if (!removedItems.isEmpty()) {
            GalleryItem existing = removedItems.get(0);
            existing.setTitle(request.getTitle() != null && !request.getTitle().isBlank()
                    ? request.getTitle() : "未命名作品");
            existing.setCategoryTags(request.getCategoryTags());
            existing.setStyleTags(request.getStyleTags());
            existing.setNegativePrompt(request.getNegativePrompt());
            existing.setStatus(STATUS_PUBLISHED);
            existing.setUpdatedAt(LocalDateTime.now());
            GalleryItem saved = galleryItemRepository.save(existing);
            log.info("Gallery item re-published: id={}, recordId={}, userId={}", saved.getId(), request.getRecordId(), userId);
            return saved;
        }

        GalleryItem item = GalleryItem.builder()
                .recordId(request.getRecordId())
                .userId(userId)
                .title(request.getTitle() != null && !request.getTitle().isBlank()
                        ? request.getTitle() : "未命名作品")
                .categoryTags(request.getCategoryTags())
                .styleTags(request.getStyleTags())
                .negativePrompt(request.getNegativePrompt())
                .status(STATUS_PUBLISHED)
                .viewCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        GalleryItem saved = galleryItemRepository.save(item);
        log.info("Gallery item published: id={}, recordId={}, userId={}", saved.getId(), request.getRecordId(), userId);
        return saved;
    }

    /**
     * 分页查询已发布的画廊作品（LEFT JOIN 过滤已被删除的记录）。
     */
    public Page<GalleryItemResponse> listPublished(Pageable pageable) {
        Page<GalleryItem> items = galleryItemRepository.findByStatusOrderByCreatedAtDesc(STATUS_PUBLISHED, pageable);

        List<GalleryItemResponse> responses = items.getContent().stream()
                .map(this::toResponse)
                .filter(r -> r != null) // 过滤掉关联记录已被删的条目
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, items.getTotalElements());
    }

    /**
     * 获取画廊作品详情。
     */
    public GalleryItemResponse getDetail(Long id) {
        GalleryItem item = galleryItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "画廊作品不存在"));
        return toResponse(item);
    }

    /**
     * 创作者取消发布（直接删除 gallery 记录，不影响历史记录）。
     */
    @Transactional
    public void unpublish(Long id, Long userId) {
        GalleryItem item = galleryItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "画廊作品不存在"));
        if (!item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权取消发布他人的作品");
        }
        galleryItemRepository.delete(item);
        log.info("Gallery item unpublished by user: id={}, userId={}", id, userId);
    }

    /**
     * 管理员下架作品（直接删除 gallery 记录）。
     */
    @Transactional
    public void adminRemove(Long id) {
        GalleryItem item = galleryItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "画廊作品不存在"));
        galleryItemRepository.delete(item);
        log.info("Gallery item removed by admin: id={}", id);
    }

    /**
     * 获取当前用户可发布的历史记录列表（排除已发布的）。
     */
    public List<ImageGenerationRecord> getPublishableRecords(Long userId) {
        List<Long> publishedRecordIds = galleryItemRepository
                .findPublishedRecordIdsByUserId(userId, STATUS_PUBLISHED);

        List<ImageGenerationRecord> allRecords = recordRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

        return allRecords.stream()
                .filter(r -> !publishedRecordIds.contains(r.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 将 GalleryItem 实体转换为带关联信息的响应 DTO。
     */
    private GalleryItemResponse toResponse(GalleryItem item) {
        // 检查关联记录是否存在（独立生命周期，可能已被删除）
        ImageGenerationRecord record = recordRepository.findById(item.getRecordId()).orElse(null);
        if (record == null) {
            return null; // 关联记录已被删除，过滤掉此条目
        }

        // 查询发布者用户名
        User author = userRepository.findById(item.getUserId()).orElse(null);
        String authorName = author != null ? author.getUsername() : "未知用户";

        return GalleryItemResponse.builder()
                .id(item.getId())
                .recordId(item.getRecordId())
                .userId(item.getUserId())
                .title(item.getTitle())
                .categoryTags(item.getCategoryTags())
                .styleTags(item.getStyleTags())
                .negativePrompt(item.getNegativePrompt())
                .status(item.getStatus())
                .viewCount(item.getViewCount())
                .imageUrl("/" + record.getResultPath().replace("\\", "/").replace("./", ""))
                .prompt(record.getPrompt())
                .revisedPrompt(record.getRevisedPrompt())
                .size(record.getSize())
                .quality(record.getQuality())
                .mode(record.getMode())
                .authorName(authorName)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
