package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.model.ImageSuperResolutionJob;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import cafe.snails.ecomagents.repository.ImageSuperResolutionJobRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@Slf4j
/** 负责图片超分辨率任务的提交、查询与状态维护。 */
public class ImageSuperResolutionJobService {
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String SOURCE_HISTORY = "HISTORY";
    public static final String SOURCE_UPLOAD = "UPLOAD";
    public static final String ORIGIN_GENERATION = "IMAGE_GENERATION";
    public static final String ORIGIN_PAGE = "SUPER_RESOLUTION_PAGE";

    private static final List<String> ACTIVE_STATUSES = List.of(PENDING, RUNNING);
    private static final List<String> TERMINAL_STATUSES = List.of(SUCCEEDED, FAILED);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp");
    private static final int MAX_ACTIVE_JOBS_PER_USER = 3;

    private final ImageSuperResolutionJobRepository jobRepository;
    private final ImageGenerationRecordRepository recordRepository;
    private final ImageSuperResolutionService superResolutionService;
    private final Executor executor;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public ImageSuperResolutionJobService(
            ImageSuperResolutionJobRepository jobRepository,
            ImageGenerationRecordRepository recordRepository,
            ImageSuperResolutionService superResolutionService,
            @Qualifier("imageSuperResolutionExecutor") Executor executor) {
        this.jobRepository = jobRepository;
        this.recordRepository = recordRepository;
        this.superResolutionService = superResolutionService;
        this.executor = executor;
    }

    public JobResponse submit(CreateJobRequest request, Long userId) {
        if (request == null || request.recordId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "recordId is required");
        }
        int factor = validateFactor(request.upscaleFactor());
        String origin = normalizeOrigin(request.origin());
        ImageGenerationRecord source = superResolutionService.validateSourceRecord(request.recordId(), userId);
        if (!"GENERATE".equals(source.getMode()) && !"EDIT".equals(source.getMode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only generated or edited images can be upscaled");
        }
        int[] dimensions = source.getWidth() != null && source.getHeight() != null
                ? new int[]{source.getWidth(), source.getHeight()}
                : superResolutionService.validateSourcePath(source.getResultPathNormalized());
        return createJob(userId, source.getId(), SOURCE_HISTORY, origin, source.getResultPathNormalized(),
                dimensions[0], dimensions[1], "record:" + source.getId(), factor);
    }

    public JobResponse submitUpload(MultipartFile file, Integer upscaleFactor, String origin, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Image file is required");
        }
        int factor = validateFactor(upscaleFactor);
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Image format must be PNG, JPG, JPEG, or BMP");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to read uploaded image");
        }
        int[] dimensions = superResolutionService.validateUploadedSource(bytes);
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path sourceDir = uploadRoot.resolve("super-resolution/source").normalize();
        Path target = sourceDir.resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!target.startsWith(sourceDir)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid upload path");
        }
        try {
            Files.createDirectories(sourceDir);
            Files.write(target, bytes);
            String sourcePath = "/uploads/super-resolution/source/" + target.getFileName();
            try {
                return createJob(userId, null, SOURCE_UPLOAD, normalizeOrigin(origin), sourcePath,
                        dimensions[0], dimensions[1], sha256(bytes), factor);
            } catch (RuntimeException e) {
                Files.deleteIfExists(target);
                throw e;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to save uploaded image");
        }
    }

    public JobResponse retry(Long jobId, Long userId) {
        ImageSuperResolutionJob failed = getOwnedJob(jobId, userId);
        if (!FAILED.equals(failed.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only failed jobs can be retried");
        }
        if (SOURCE_HISTORY.equals(sourceTypeOf(failed))) {
            return submit(new CreateJobRequest(failed.getSourceRecordId(), failed.getUpscaleFactor(), failed.getOrigin()), userId);
        }
        int[] dimensions = superResolutionService.validateSourcePath(failed.getSourcePath());
        return createJob(userId, null, SOURCE_UPLOAD, originOf(failed), failed.getSourcePath(),
                dimensions[0], dimensions[1], failed.getSourceFingerprint(), failed.getUpscaleFactor());
    }

    public List<JobResponse> list(Long userId, String origin) {
        List<ImageSuperResolutionJob> jobs = origin == null || origin.isBlank()
                ? jobRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                : jobRepository.findTop50ByUserIdAndOriginOrderByCreatedAtDesc(userId, normalizeOrigin(origin));
        return jobs.stream().map(this::toResponse).toList();
    }

    public long activeCount(Long userId) {
        return jobRepository.countByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
    }

    public Page<ImageGenerationRecord> eligibleHistorySources(
            Long userId, LocalDate startDate, LocalDate endDate, String prompt, Pageable pageable) {
        Specification<ImageGenerationRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(root.get("mode").in("GENERATE", "EDIT"));
            predicates.add(cb.isNotNull(root.get("width")));
            predicates.add(cb.isNotNull(root.get("height")));
            predicates.add(cb.lessThanOrEqualTo(root.get("width"), 1920));
            predicates.add(cb.lessThanOrEqualTo(root.get("height"), 1920));
            predicates.add(cb.or(
                    cb.lessThanOrEqualTo(root.get("width"), 1080),
                    cb.lessThanOrEqualTo(root.get("height"), 1080)));
            if (startDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            if (endDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            if (prompt != null && !prompt.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("prompt")), "%" + prompt.toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ImageGenerationRecord> page = recordRepository.findAll(spec, pageable);
        List<ImageGenerationRecord> available = page.getContent().stream()
                .filter(record -> sourceFileExists(record.getResultPathNormalized()))
                .toList();
        return new PageImpl<>(available, pageable, page.getTotalElements());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeIncompleteJobs() {
        jobRepository.findByStatusIn(ACTIVE_STATUSES).forEach(job -> {
            job.setStatus(PENDING);
            job.setStartedAt(null);
            jobRepository.save(job);
            executor.execute(() -> process(job.getId()));
        });
    }

    @Scheduled(fixedDelay = 3_600_000L)
    public void cleanupExpiredUploads() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        jobRepository.findBySourceTypeAndStatusInAndCompletedAtBefore(SOURCE_UPLOAD, TERMINAL_STATUSES, cutoff)
                .stream()
                .filter(job -> !jobRepository.existsByUserIdAndSourcePathAndStatusIn(
                        job.getUserId(), job.getSourcePath(), ACTIVE_STATUSES))
                .forEach(job -> deleteSourceFile(job.getSourcePath()));
    }

    private JobResponse createJob(Long userId, Long sourceRecordId, String sourceType, String origin,
                                  String sourcePath, int sourceWidth, int sourceHeight,
                                  String sourceFingerprint, int factor) {
        if (activeCount(userId) >= MAX_ACTIVE_JOBS_PER_USER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You can have at most 3 active super-resolution jobs");
        }
        String fingerprint = sourceFingerprint != null ? sourceFingerprint
                : sourceRecordId != null ? "record:" + sourceRecordId : "path:" + sourcePath;
        String dedupKey = userId + ":" + fingerprint + ":x" + factor;
        ImageSuperResolutionJob job = ImageSuperResolutionJob.builder()
                .userId(userId)
                .sourceRecordId(sourceRecordId)
                .sourceType(sourceType)
                .origin(origin)
                .sourcePath(sourcePath)
                .sourceWidth(sourceWidth)
                .sourceHeight(sourceHeight)
                .sourceFingerprint(fingerprint)
                .upscaleFactor(factor)
                .status(PENDING)
                .activeDedupKey(dedupKey)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            job = jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "An identical super-resolution job is already active");
        }
        Long jobId = job.getId();
        executor.execute(() -> process(jobId));
        return toResponse(job);
    }

    private void process(Long jobId) {
        ImageSuperResolutionJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !PENDING.equals(job.getStatus())) return;
        job.setStatus(RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            ImageGenerationRecord source = job.getSourceRecordId() == null ? null
                    : recordRepository.findById(job.getSourceRecordId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Source image record not found"));
            var request = new ImageSuperResolutionService.SuperResolutionRequest(
                    job.getSourceRecordId(), job.getSourceRecordId() == null ? job.getSourcePath() : null,
                    "base", job.getUpscaleFactor(), "png", null, true);
            var result = superResolutionService.upscale(request, job.getUserId());
            if (result.savedPath() == null || result.width() == null || result.height() == null) {
                throw new IllegalStateException("Super-resolution output was not saved correctly");
            }

            ImageGenerationRecord history = recordRepository.save(ImageGenerationRecord.builder()
                    .userId(job.getUserId())
                    .mode("SUPER_RESOLUTION")
                    .prompt(source != null ? source.getPrompt() : null)
                    .revisedPrompt(source != null ? source.getRevisedPrompt() : null)
                    .size(result.width() + "x" + result.height())
                    .quality(source != null ? source.getQuality() : "auto")
                    .resultPath(result.savedPath())
                    .timeCostMs(result.timeCostMs())
                    .width(result.width())
                    .height(result.height())
                    .sourceRecordId(job.getSourceRecordId())
                    .upscaleFactor(job.getUpscaleFactor())
                    .createdAt(LocalDateTime.now())
                    .build());

            job.setStatus(SUCCEEDED);
            job.setResultPath(result.savedPath());
            job.setResultRecordId(history.getId());
            job.setWidth(result.width());
            job.setHeight(result.height());
            job.setTimeCostMs(result.timeCostMs());
            job.setCompletedAt(LocalDateTime.now());
            job.setActiveDedupKey(null);
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("Super-resolution job {} failed", jobId, e);
            job.setStatus(FAILED);
            job.setErrorMessage(truncate(e.getMessage(), 1000));
            job.setCompletedAt(LocalDateTime.now());
            job.setActiveDedupKey(null);
            jobRepository.save(job);
        }
    }

    private ImageSuperResolutionJob getOwnedJob(Long jobId, Long userId) {
        ImageSuperResolutionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Super-resolution job not found"));
        if (!job.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to access this job");
        }
        return job;
    }

    private boolean sourceFileExists(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) return false;
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return true;
        return Files.isRegularFile(toLocalUploadPath(sourcePath));
    }

    private void deleteSourceFile(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) return;
        try {
            Path path = toLocalUploadPath(sourcePath);
            Path sourceRoot = Paths.get(uploadDir, "super-resolution/source").toAbsolutePath().normalize();
            if (path.startsWith(sourceRoot)) Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Failed to clean super-resolution source {}: {}", sourcePath, e.getMessage());
        }
    }

    private Path toLocalUploadPath(String path) {
        String relative = path.replace('\\', '/').replaceFirst("^/?uploads/+", "");
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(relative).normalize();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
    private int validateFactor(Integer factor) {
        int value = factor == null ? 2 : factor;
        if (value < 2 || value > 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Upscale factor must be 2, 3, or 4");
        }
        return value;
    }

    private String normalizeOrigin(String origin) {
        if (origin == null || origin.isBlank()) return ORIGIN_PAGE;
        if (!ORIGIN_GENERATION.equals(origin) && !ORIGIN_PAGE.equals(origin)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid super-resolution job origin");
        }
        return origin;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private JobResponse toResponse(ImageSuperResolutionJob job) {
        return new JobResponse(job.getId(), job.getSourceRecordId(), sourceTypeOf(job), originOf(job),
                job.getUpscaleFactor(), job.getStatus(), job.getSourcePath(), job.getSourceWidth(), job.getSourceHeight(),
                job.getResultPath(), job.getResultRecordId(), job.getWidth(), job.getHeight(), job.getTimeCostMs(),
                job.getErrorMessage(), sourceFileExists(job.getSourcePath()), job.getCreatedAt(), job.getCompletedAt());
    }

    private String sourceTypeOf(ImageSuperResolutionJob job) {
        return job.getSourceType() != null ? job.getSourceType()
                : job.getSourceRecordId() != null ? SOURCE_HISTORY : SOURCE_UPLOAD;
    }

    private String originOf(ImageSuperResolutionJob job) {
        return job.getOrigin() != null ? job.getOrigin() : ORIGIN_GENERATION;
    }
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    public record CreateJobRequest(Long recordId, Integer upscaleFactor, String origin) {}

    public record JobResponse(
            Long id, Long sourceRecordId, String sourceType, String origin, Integer upscaleFactor, String status,
            String sourcePath, Integer sourceWidth, Integer sourceHeight, String resultPath, Long resultRecordId,
            Integer width, Integer height, Long timeCostMs, String errorMessage, Boolean sourceAvailable,
            LocalDateTime createdAt, LocalDateTime completedAt
    ) {}
}
