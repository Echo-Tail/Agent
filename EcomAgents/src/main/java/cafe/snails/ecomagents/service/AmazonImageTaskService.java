package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.AmazonImageResult;
import cafe.snails.ecomagents.model.AmazonImageTask;
import cafe.snails.ecomagents.model.ProductProfileVersion;
import cafe.snails.ecomagents.model.ImageGenerationJob;
import cafe.snails.ecomagents.model.ImageGenerationJobStatus;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.repository.AmazonImageResultRepository;
import cafe.snails.ecomagents.repository.AmazonImageTaskRepository;
import cafe.snails.ecomagents.repository.ProductProfileVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import cafe.snails.ecomagents.service.image.runtime.ImageGenerationWorkflowService;
import java.time.Duration;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmazonImageTaskService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String RESULT_PENDING = "PENDING";
    public static final String RESULT_ACCEPTED = "ACCEPTED";
    public static final String RESULT_REJECTED = "REJECTED";

    private static final String DEFAULT_MARKETPLACE = "Amazon US";
    private static final String DEFAULT_CATEGORY = "car stereo";
    private static final String DEFAULT_MODE = "PRODUCTION";

    private final AmazonImageTaskRepository taskRepository;
    private final AmazonImageResultRepository resultRepository;
    private final ProductProfileVersionRepository versionRepository;
    private final ImageGenerationWorkflowService imageGenerationWorkflow;
    private final PromptCompositionService promptCompositionService;
    private final ObjectMapper objectMapper;

    public record CreateTaskRequest(
            Long profileVersionId,
            String taskName,
            String asin,
            String marketplace,
            String category,
            String subcategory,
            String imageType,
            String mode,
            String sourceType,
            String sourceUrls,
            String referenceImageUrls,
            String notes
    ) {}

    public record UpdateFactsRequest(
            String taskName,
            String subcategory,
            String productFactsJson,
            String notes
    ) {}

    public record UpdatePromptRequest(
            String promptJson,
            String promptText,
            String negativePrompt,
            Long modelId
    ) {}

    public record AnalyzeExpressionRequest(
            String imageExpressionJson
    ) {}

    public record UpdateMaterialFactsRequest(
            String sourceMaterialFactsJson,
            String checkedMaterialFactKeys
    ) {}

    public record MarkResultRequest(
            String status
    ) {}

    public record GenerateTaskResult(
            AmazonImageTask task,
            ImageGenerationWorkflowService.GenerationResult generation,
            List<AmazonImageResult> results
    ) {}

    @Transactional
    public AmazonImageTask create(CreateTaskRequest request, Long userId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (isBlank(request.imageType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片类型不能为空");
        }
        if (request.profileVersionId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "必须选择已确认的产品资料版本");
        }
        if (isBlank(request.taskName()) && isBlank(request.asin())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务名称和 ASIN 至少填写一个");
        }

        // Verify the profile version exists
        ProductProfileVersion version = versionRepository.findById(request.profileVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "产品资料版本不存在"));

        AmazonImageTask task = AmazonImageTask.builder()
                .userId(userId)
                .profileVersionId(request.profileVersionId())
                .asin(trimToNull(request.asin()))
                .taskName(defaultTaskName(request.taskName(), request.asin(), request.imageType()))
                .marketplace(defaultIfBlank(request.marketplace(), DEFAULT_MARKETPLACE))
                .category(defaultIfBlank(request.category(), DEFAULT_CATEGORY))
                .subcategory(trimToNull(request.subcategory()))
                .imageType(request.imageType().trim())
                .mode(defaultIfBlank(request.mode(), DEFAULT_MODE))
                .status(STATUS_DRAFT)
                .sourceType(trimToNull(request.sourceType()))
                .sourceUrls(trimToNull(request.sourceUrls()))
                .referenceImageUrls(trimToNull(request.referenceImageUrls()))
                .productFactsJson(version.getProductFactsJson())
                .collectionStatus("PENDING")
                .notes(trimToNull(request.notes()))
                .build();
        return taskRepository.save(task);
    }

    public Page<AmazonImageTask> list(Long userId, String asin, String imageType, String status, Pageable pageable) {
        Specification<AmazonImageTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (!isBlank(asin)) {
                predicates.add(cb.like(cb.lower(root.get("asin")), "%" + asin.trim().toLowerCase() + "%"));
            }
            if (!isBlank(imageType)) {
                predicates.add(cb.equal(root.get("imageType"), imageType.trim()));
            }
            if (!isBlank(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    public AmazonImageTask get(Long id, Long userId) {
        AmazonImageTask task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该任务");
        }
        return task;
    }

    @Transactional
    public AmazonImageTask updateFacts(Long id, UpdateFactsRequest request, Long userId) {
        AmazonImageTask task = get(id, userId);
        if (!isBlank(request.taskName())) {
            task.setTaskName(request.taskName().trim());
        }
        task.setSubcategory(trimToNull(request.subcategory()));
        if (request.productFactsJson() != null) {
            task.setProductFactsJson(request.productFactsJson());
        }
        task.setNotes(trimToNull(request.notes()));
        return taskRepository.save(task);
    }

    @Transactional
    public AmazonImageTask analyzeExpression(Long id, AnalyzeExpressionRequest request, Long userId) {
        AmazonImageTask task = get(id, userId);
        if (request == null || isBlank(request.imageExpressionJson())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片表达结构不能为空");
        }
        task.setImageExpressionJson(request.imageExpressionJson());
        return taskRepository.save(task);
    }

    @Transactional
    public AmazonImageTask updateMaterialFacts(Long id, UpdateMaterialFactsRequest request, Long userId) {
        AmazonImageTask task = get(id, userId);
        task.setSourceMaterialFactsJson(trimToNull(request.sourceMaterialFactsJson()));
        task.setCheckedMaterialFactKeys(trimToNull(request.checkedMaterialFactKeys()));
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        AmazonImageTask task = get(id, userId);
        resultRepository.deleteByTaskId(id);
        taskRepository.delete(task);
    }

    @Transactional
    public AmazonImageTask updatePrompt(Long id, UpdatePromptRequest request, Long userId) {
        AmazonImageTask task = get(id, userId);
        task.setPromptJson(trimToNull(request.promptJson()));
        task.setPromptText(trimToNull(request.promptText()));
        task.setNegativePrompt(trimToNull(request.negativePrompt()));
        task.setModelId(request.modelId());
        return taskRepository.save(task);
    }

    public GenerateTaskResult generate(Long id, String prompt, String size, String quality,
                                       List<MultipartFile> images, int n, Long modelId, Long userId) {
        AmazonImageTask task = get(id, userId);
        String finalPrompt = !isBlank(prompt) ? prompt : task.getPromptText();

        // Auto-compose prompt from three-layer inputs if no explicit prompt set
        if (isBlank(finalPrompt) && !isBlank(task.getImageExpressionJson())) {
            PromptCompositionService.PromptCompositionResult composed = promptCompositionService.compose(
                    task.getProductFactsJson(),
                    task.getImageExpressionJson(),
                    task.getSourceMaterialFactsJson(),
                    task.getCheckedMaterialFactKeys(),
                    task.getImageType()
            );
            finalPrompt = composed.finalPrompt();
            task.setPromptJson(composed.structuredBrief());
            task.setPromptText(finalPrompt);
            task = taskRepository.save(task);
        }

        if (isBlank(finalPrompt)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提示词不能为空，请先设置图片表达结构或手动输入提示词");
        }

        task.setStatus(STATUS_PENDING);
        task = taskRepository.save(task);

        ImageGenerationWorkflowService.AwaitResult awaited;
        try {
            ImageGenerationJob imageJob;
            if (images != null && !images.isEmpty()) {
                imageJob = imageGenerationWorkflow.submitImage(userId, modelId, finalPrompt, size, quality,
                        n, task.getNegativePrompt(), images, null);
            } else {
                imageJob = imageGenerationWorkflow.submitText(userId, modelId, finalPrompt, size, quality,
                        n, task.getNegativePrompt());
            }
            task.setImageJobId(imageJob.getId());
            task = taskRepository.save(task);
            awaited = imageGenerationWorkflow.await(imageJob.getId(), userId, Duration.ofMinutes(10));
            if (!awaited.completed()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "图片任务仍在执行，imageJobId=" + imageJob.getId());
            }
        } catch (Exception e) {
            if (task.getImageJobId() == null) task.setStatus(STATUS_FAILED);
            taskRepository.save(task);
            if (e instanceof BusinessException businessException) throw businessException;
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败: " + e.getMessage());
        }

        if (awaited.job().getStatus() == ImageGenerationJobStatus.FAILED
                || awaited.job().getStatus() == ImageGenerationJobStatus.CANCELLED) {
            task.setStatus(STATUS_FAILED);
            taskRepository.save(task);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    awaited.job().getSafeErrorMessage() != null ? awaited.job().getSafeErrorMessage() : "图片生成失败");
        }
        ImageGenerationWorkflowService.GenerationResult result = imageGenerationWorkflow.result(awaited);

        // Create result records for each generated image
        List<AmazonImageResult> results = new ArrayList<>();
        List<String> savedUrls = new ArrayList<>();
        List<ImageGenerationRecord> successfulRecords = awaited.successfulRecords();
        for (int i = 0; i < successfulRecords.size(); i++) {
            ImageGenerationRecord record = successfulRecords.get(i);
            String url = record.getResultPathNormalized();
            savedUrls.add(url);
            AmazonImageResult r = AmazonImageResult.builder()
                    .taskId(id)
                    .generationRecordId(record.getId())
                    .imagePath(url)
                    .status(RESULT_PENDING)
                    .imageIndex(record.getOutputIndex() != null ? record.getOutputIndex() : i)
                    .build();
            results.add(resultRepository.save(r));
        }

        task.setPromptText(finalPrompt);
        task.setModelId(awaited.job().getModelId());
        task.setGenerationRecordId(result.recordId());
        task.setResultPaths(String.join("\n", savedUrls));
        task.setStatus(STATUS_GENERATED);
        task = taskRepository.save(task);

        return new GenerateTaskResult(task, result, results);
    }

    public List<AmazonImageResult> getResults(Long taskId, Long userId) {
        AmazonImageTask task = get(taskId, userId);
        return resultRepository.findByTaskIdOrderByImageIndexAsc(taskId);
    }

    @Transactional
    public AmazonImageResult markResult(Long resultId, String status, Long userId) {
        AmazonImageResult r = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "生成结果不存在"));
        AmazonImageTask task = taskRepository.findById(r.getTaskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该结果");
        }
        if (!RESULT_PENDING.equals(status) && !RESULT_ACCEPTED.equals(status) && !RESULT_REJECTED.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的状态值: " + status);
        }
        r.setStatus(status);
        return resultRepository.save(r);
    }

    @Transactional
    public AmazonImageTask completeTask(Long id, Long userId) {
        AmazonImageTask task = get(id, userId);
        if (!STATUS_GENERATED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有已生成状态的任务可以标记为完成");
        }
        task.setStatus(STATUS_COMPLETED);
        return taskRepository.save(task);
    }

    // --- Internal helpers (same as before) ---

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String defaultTaskName(String taskName, String asin, String imageType) {
        if (!isBlank(taskName)) {
            return limit(taskName.trim(), 120);
        }
        if (isBlank(asin)) {
            return "Amazon image task - " + (isBlank(imageType) ? "image" : imageType.trim());
        }
        String type = isBlank(imageType) ? "image" : imageType.trim();
        return limit(asin.trim() + " - " + type, 120);
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
