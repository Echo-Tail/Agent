package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.dto.review.ReviewCollectionDtos.CollectionResponse;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import cafe.snails.ecomagents.service.BrightDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewCollectionService {
    public static final String AMAZON_REVIEWS_DATASET_ID = "gd_le8e811kzy4ggddlq";

    private final ReviewAnalysisProjectRepository projectRepository;
    private final ReviewProjectProductRepository projectProductRepository;
    private final ReviewCollectionBatchRepository batchRepository;
    private final ProductReviewRepository reviewRepository;
    private final BrightDataService brightDataService;
    private final ReviewNormalizationService normalizationService;

    public CollectionResponse start(Long projectId, String idempotencyKey, Long userId) {
        var project = requireProject(projectId, userId);
        String key = requireIdempotencyKey(idempotencyKey);
        var existing = batchRepository.findByProjectIdAndIdempotencyKey(projectId, key);
        if (existing.isPresent()) return toResponse(existing.get());

        var products = projectProductRepository.findByProjectIdOrderById(projectId);
        if (products.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评论分析项目没有配置商品");
        }
        int requestedCount = products.stream().mapToInt(ReviewProjectProduct::getReviewLimit).sum();
        var batch = batchRepository.save(ReviewCollectionBatch.builder()
                .projectId(projectId)
                .datasetId(AMAZON_REVIEWS_DATASET_ID)
                .idempotencyKey(key)
                .status("pending")
                .requestedCount(requestedCount)
                .collectedCount(0)
                .duplicateCount(0)
                .createdAt(LocalDateTime.now())
                .build());

        var request = new BrightDataTriggerRequest();
        request.setDatasetId(AMAZON_REVIEWS_DATASET_ID);
        request.setFormat("json");
        request.setIncludeErrors(true);
        request.setLimitPerInput(products.stream().mapToInt(ReviewProjectProduct::getReviewLimit).max().orElse(100));
        request.setInput(products.stream().map(product ->
                Map.<String, Object>of("url", "https://www.amazon.com/dp/" + product.getAsin())).toList());

        ApiResponse<BrightDataTriggerResponse> trigger = brightDataService.trigger(request, userId);
        batch.setStartedAt(LocalDateTime.now());
        if (trigger.getCode() != 200 || trigger.getData() == null
                || trigger.getData().getSnapshotId() == null || trigger.getData().getSnapshotId().isBlank()) {
            fail(batch, project, "Bright Data 触发失败: " + trigger.getMessage());
            return toResponse(batch);
        }
        batch.setSnapshotId(trigger.getData().getSnapshotId());
        batch.setBrightDataRecordId(trigger.getData().getRecordId());
        batch.setStatus("running");
        batchRepository.save(batch);
        project.setStatus("collecting");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        return toResponse(batch);
    }

    public CollectionResponse progress(Long projectId, Long batchId, Long userId) {
        var project = requireProject(projectId, userId);
        var batch = requireBatch(projectId, batchId);
        if (Set.of("success", "partial", "failed").contains(batch.getStatus())) return toResponse(batch);
        if (batch.getSnapshotId() == null) {
            fail(batch, project, "采集任务缺少 Bright Data snapshot ID");
            return toResponse(batch);
        }

        ApiResponse<BrightDataSnapshotStatus> progress = brightDataService.getProgress(batch.getSnapshotId());
        if (progress.getCode() != 200 || progress.getData() == null) {
            batch.setErrorMessage("查询 Bright Data 进度失败: " + progress.getMessage());
            batchRepository.save(batch);
            return toResponse(batch);
        }
        String remoteStatus = progress.getData().getStatus();
        if ("failed".equalsIgnoreCase(remoteStatus)) {
            fail(batch, project, "Bright Data 快照采集失败");
        } else if ("ready".equalsIgnoreCase(remoteStatus)) {
            complete(batch, project);
        } else {
            batch.setStatus("running");
            batchRepository.save(batch);
        }
        return toResponse(batch);
    }

    public CollectionResponse get(Long projectId, Long batchId, Long userId) {
        requireProject(projectId, userId);
        return toResponse(requireBatch(projectId, batchId));
    }

    public CollectionResponse retry(Long projectId, Long batchId, Long userId) {
        var project = requireProject(projectId, userId);
        var batch = requireBatch(projectId, batchId);
        if (!"failed".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only a failed collection can be retried");
        }
        if (batch.getSnapshotId() == null || batch.getSnapshotId().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Failed collection has no reusable snapshot");
        }
        batch.setStatus("running");
        batch.setErrorMessage(null);
        batch.setCompletedAt(null);
        batchRepository.save(batch);
        project.setStatus("collecting");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        return progress(projectId, batchId, userId);
    }

    private void complete(ReviewCollectionBatch batch, ReviewAnalysisProject project) {
        ApiResponse<Object> download = brightDataService.downloadSnapshot(batch.getSnapshotId(), "json");
        if (download.getCode() != 200 || !(download.getData() instanceof List<?> records)) {
            fail(batch, project, "下载 Bright Data 快照失败: " + download.getMessage());
            return;
        }

        Map<String, ReviewProjectProduct> configured = new HashMap<>();
        for (var product : projectProductRepository.findByProjectIdOrderById(project.getId())) {
            configured.put(product.getAsin(), product);
        }
        Map<String, Long> existingCounts = new HashMap<>();
        Map<String, Integer> acceptedCounts = new HashMap<>();
        Set<String> seenKeys = new HashSet<>();
        List<ProductReview> reviews = new ArrayList<>();
        int rejected = 0;
        int duplicates = 0;

        for (Object item : records) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                rejected++;
                continue;
            }
            Map<String, Object> source = stringKeyMap(rawMap);
            if (source.containsKey("error") && source.get("error") != null) {
                rejected++;
                continue;
            }
            var normalized = normalizationService.normalize(source);
            if (normalized.isEmpty() || !configured.containsKey(normalized.get().asin())) {
                rejected++;
                continue;
            }
            var value = normalized.get();
            var product = configured.get(value.asin());
            long existingCount = existingCounts.computeIfAbsent(value.asin(),
                    asin -> reviewRepository.countByProjectIdAndAsin(project.getId(), asin));
            int accepted = acceptedCounts.getOrDefault(value.asin(), 0);
            if (existingCount + accepted >= product.getReviewLimit()) continue;
            String localKey = value.asin() + ":" + (value.externalReviewId() != null
                    ? "id:" + value.externalReviewId() : "hash:" + value.contentHash());
            if (!seenKeys.add(localKey) || exists(project.getId(), value)) {
                duplicates++;
                continue;
            }

            reviews.add(ProductReview.builder()
                    .projectId(project.getId())
                    .collectionBatchId(batch.getId())
                    .asin(value.asin())
                    .externalReviewId(value.externalReviewId())
                    .rating(value.rating())
                    .title(value.title())
                    .reviewText(value.reviewText())
                    .reviewDate(value.reviewDate())
                    .verifiedPurchase(value.verifiedPurchase())
                    .helpfulCount(value.helpfulCount())
                    .reviewerName(value.reviewerName())
                    .sourceUrl(value.sourceUrl())
                    .contentHash(value.contentHash())
                    .rawJson(value.rawJson())
                    .collectedAt(LocalDateTime.now())
                    .build());
            acceptedCounts.put(value.asin(), accepted + 1);
        }
        if (!reviews.isEmpty()) reviewRepository.saveAll(reviews);
        batch.setCollectedCount(reviews.size());
        batch.setDuplicateCount(duplicates);
        batch.setCompletedAt(LocalDateTime.now());
        batch.setErrorMessage(rejected == 0 ? null : rejected + " 条记录无法标准化或由 Bright Data 标记为错误");
        batch.setStatus(rejected > 0 && !reviews.isEmpty() ? "partial" : rejected > 0 ? "failed" : "success");
        batchRepository.save(batch);
        project.setStatus("failed".equals(batch.getStatus()) ? "failed" : "collected");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
    }

    private boolean exists(Long projectId, ReviewNormalizationService.NormalizedReview value) {
        if (value.externalReviewId() != null && reviewRepository
                .findByProjectIdAndAsinAndExternalReviewId(projectId, value.asin(), value.externalReviewId()).isPresent()) {
            return true;
        }
        return reviewRepository.findByProjectIdAndAsinAndContentHash(
                projectId, value.asin(), value.contentHash()).isPresent();
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private ReviewAnalysisProject requireProject(Long projectId, Long userId) {
        return projectRepository.findByIdAndCreatedBy(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析项目不存在"));
    }

    private ReviewCollectionBatch requireBatch(Long projectId, Long batchId) {
        return batchRepository.findByIdAndProjectId(batchId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论采集任务不存在"));
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少有效的 Idempotency-Key");
        }
        return key.trim();
    }

    private void fail(ReviewCollectionBatch batch, ReviewAnalysisProject project, String message) {
        batch.setStatus("failed");
        batch.setErrorMessage(message);
        batch.setCompletedAt(LocalDateTime.now());
        batchRepository.save(batch);
        project.setStatus("failed");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        log.warn("Review collection failed: projectId={}, batchId={}, error={}", project.getId(), batch.getId(), message);
    }

    private CollectionResponse toResponse(ReviewCollectionBatch value) {
        return new CollectionResponse(value.getId(), value.getProjectId(), value.getSnapshotId(),
                value.getDatasetId(), value.getStatus(), value.getRequestedCount(), value.getCollectedCount(),
                value.getDuplicateCount(), value.getErrorMessage(), value.getStartedAt(), value.getCompletedAt(), value.getCreatedAt());
    }
}
