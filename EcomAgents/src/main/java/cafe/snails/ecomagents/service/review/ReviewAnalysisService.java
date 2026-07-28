package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.review.ReviewAnalysisDtos.*;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.review.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
/** 负责评论分析任务的创建、确认与状态管理。 */
public class ReviewAnalysisService {
    public static final String PROMPT_VERSION = "review_extraction_v2_zh";
    static final int DEFAULT_BATCH_SIZE = 50;
    static final String DEFAULT_ROLE_PROMPT = """
            你是一名资深 Car Stereo 产品洞察分析师。
            评论原文和证据必须保持原语言；用户问题、改进建议及商业分析必须使用简体中文。
            只提取有原文证据支持的事实，不要虚构问题、根因或用户意图。
            """;
    private static final Set<String> STARTABLE_PROJECT_STATUSES = Set.of("collected", "review", "failed");

    private final ReviewAnalysisProjectRepository projectRepository;
    private final ReviewAnalysisRunRepository runRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewAnalysisFailureRepository failureRepository;
    private final AiModelRepository modelRepository;
    private final ReviewAnalysisWorkerRunner workerRunner;

    public AnalysisRunResponse start(
            Long projectId, String idempotencyKey, Long userId) {
        var project = requireProject(projectId, userId);
        String key = requireIdempotencyKey(idempotencyKey);
        var existing = runRepository.findByProjectIdAndIdempotencyKey(projectId, key);
        if (existing.isPresent()) return toResponse(existing.get());
        if (!STARTABLE_PROJECT_STATUSES.contains(project.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前项目状态不允许开始分析");
        }
        long reviewCount = reviewRepository.countByProjectId(projectId);
        if (reviewCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目没有可分析的评论");
        }
        var model = requireDefaultTextModel();
        int version = Math.toIntExact(runRepository.countByProjectId(projectId) + 1);
        var run = runRepository.save(ReviewAnalysisRun.builder()
                .projectId(projectId)
                .versionNumber(version)
                .status("pending")
                .taxonomyVersion(CarStereoReviewTaxonomy.VERSION)
                .promptVersion(PROMPT_VERSION)
                .rolePrompt(DEFAULT_ROLE_PROMPT)
                .idempotencyKey(key)
                .modelId(model.getId())
                .sourceReviewCount(Math.toIntExact(reviewCount))
                .processedReviewCount(0)
                .failedReviewCount(0)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build());
        project.setStatus("analyzing");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        workerRunner.run(run.getId(), DEFAULT_BATCH_SIZE);
        return toResponse(run);
    }

    public AnalysisRunResponse get(Long projectId, Long runId, Long userId) {
        requireProject(projectId, userId);
        return toResponse(runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析任务不存在")));
    }

    public List<AnalysisRunResponse> list(Long projectId, Long userId) {
        requireProject(projectId, userId);
        return runRepository.findByProjectIdOrderByVersionNumberDesc(projectId).stream()
                .map(ReviewAnalysisService::toResponse)
                .toList();
    }

    public AnalysisRunResponse retryFailures(Long projectId, Long runId, Long userId) {
        requireProject(projectId, userId);
        var run = runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Review analysis run not found"));
        if (!Set.of("draft", "failed").contains(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Analysis failures cannot be retried in the current state");
        }
        if (failureRepository.countByAnalysisRunId(runId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Analysis run has no checkpointed failures");
        }
        run.setStatus("pending");
        runRepository.save(run);
        workerRunner.retryFailures(runId);
        return toResponse(run);
    }

    private ReviewAnalysisProject requireProject(Long projectId, Long userId) {
        return projectRepository.findByIdAndCreatedBy(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析项目不存在"));
    }

    private AiModel requireDefaultTextModel() {
        var candidates = new java.util.ArrayList<AiModel>();
        modelRepository.findByIsDefaultTrue().ifPresent(candidates::add);
        candidates.addAll(modelRepository.findByModelTypeAndEnabled("TEXT", true));
        candidates.addAll(modelRepository.findByModelTypeAndEnabled("MULTIMODAL", true));
        var model = candidates.stream().filter(value -> Boolean.TRUE.equals(value.getEnabled())
                        && ("TEXT".equalsIgnoreCase(value.getModelType())
                        || "MULTIMODAL".equalsIgnoreCase(value.getModelType())))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "系统尚未配置可用的默认文本模型"));
        if (!Boolean.TRUE.equals(model.getEnabled())
                || (!"TEXT".equalsIgnoreCase(model.getModelType())
                && !"MULTIMODAL".equalsIgnoreCase(model.getModelType()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统尚未配置可用的默认文本模型");
        }
        return model;
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少有效的 Idempotency-Key");
        }
        return key.trim();
    }

    public static AnalysisRunResponse toResponse(ReviewAnalysisRun value) {
        return new AnalysisRunResponse(value.getId(), value.getProjectId(), value.getVersionNumber(),
                value.getStatus(), value.getTaxonomyVersion(), value.getPromptVersion(), value.getModelId(),
                value.getSourceReviewCount(), value.getProcessedReviewCount(), value.getFailedReviewCount(),
                value.getErrorMessage(), value.getCreatedAt(), value.getConfirmedAt());
    }
}
