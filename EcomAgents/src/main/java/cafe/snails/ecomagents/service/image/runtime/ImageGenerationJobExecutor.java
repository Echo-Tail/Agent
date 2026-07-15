package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import cafe.snails.ecomagents.service.ModelCredentialService;
import cafe.snails.ecomagents.service.image.runtime.provider.*;
import cafe.snails.ecomagents.service.image.runtime.storage.ImageOutputStorage;
import cafe.snails.ecomagents.service.image.runtime.storage.ImageRemoteDownloader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageGenerationJobExecutor {
    private final ImageGenerationJobRepository jobs;
    private final ImageGenerationJobInputRepository inputs;
    private final ImageGenerationRecordRepository records;
    private final AiModelRepository models;
    private final ModelCredentialService credentials;
    private final ImageAdapterRegistry adapters;
    private final ImageOutputStorage outputStorage;
    private final ImageRemoteDownloader remoteDownloader;
    private final ImageGenerationUsageRecorder usageRecorder;
    private final ImageGenerationMetrics metrics;
    @Value("${image.runtime.max-attempts:3}") private int maxAttempts = 3;
    @Value("${image.runtime.retry-base-seconds:5}") private long retryBaseSeconds = 5;
    @Value("${image.runtime.provider-poll-seconds:3}") private long providerPollSeconds = 3;

    public ImageGenerationJob execute(Long jobId) {
        return executeInternal(jobId, null, false);
    }

    public ImageGenerationJob executeClaimed(Long jobId, String workerId) {
        return executeInternal(jobId, workerId, true);
    }

    private ImageGenerationJob executeInternal(Long jobId, String workerId, boolean alreadyClaimed) {
        ImageGenerationJob job = jobs.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片生成任务不存在"));
        if (alreadyClaimed && (job.getStatus() != ImageGenerationJobStatus.RUNNING ||
                !java.util.Objects.equals(workerId, job.getWorkerId())))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务租约不属于当前 Worker");
        if (!alreadyClaimed && job.getStatus() != ImageGenerationJobStatus.PENDING)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务当前状态不可执行");
        if (!alreadyClaimed) {
            job.setStatus(ImageGenerationJobStatus.RUNNING);
            job.setExecutionPhase(ImageGenerationExecutionPhase.PREPARING);
            job.setStartedAt(LocalDateTime.now());
            job.setAttemptCount(job.getAttemptCount() + 1);
            job = jobs.save(job);
        }
        try {
            List<ImageGenerationJobInput> jobInputs = inputs.findByJobIdOrderByInputIndex(jobId);
            String secret = resolveSecret(job);
            ImageGenerationProviderAdapter adapter = adapters.require(job.getProtocol(), job.getCapability());
            adapter.validate(job, jobInputs);
            List<GeneratedProviderImage> generated;
            if (job.getProviderTaskToken() != null && !job.getProviderTaskToken().isBlank()) {
                job.setExecutionPhase(ImageGenerationExecutionPhase.POLLING);
                job = jobs.save(job);
                PollOutcome outcome = pollUntilComplete(job, adapter, secret);
                job = outcome.job();
                generated = outcome.images();
            } else {
                job.setExecutionPhase(ImageGenerationExecutionPhase.SUBMITTING);
                job = jobs.save(job);
                ImageGenerationJob submittedJob = job;
                ProviderSubmission submission = metrics.timeProviderCall(job, "submit",
                        () -> adapter.submit(submittedJob, jobInputs, secret));
                if (submission.asynchronous()) {
                    job.setProviderTaskToken(submission.taskToken());
                    job.setProviderStatus("SUBMITTED");
                    job.setExecutionPhase(ImageGenerationExecutionPhase.POLLING);
                    job = jobs.save(job);
                    PollOutcome outcome = pollUntilComplete(job, adapter, secret);
                    job = outcome.job();
                    generated = outcome.images();
                } else {
                    generated = submission.images();
                }
            }
            if (generated == null) {
                if (job.getStatus() == ImageGenerationJobStatus.CANCELLED) metrics.terminal(job);
                return job;
            }
            job.setExecutionPhase(ImageGenerationExecutionPhase.PERSISTING);
            job = jobs.save(job);
            for (int index = 0; index < generated.size(); index++) {
                GeneratedProviderImage image = generated.get(index);
                byte[] content = image.content();
                String mimeType = image.mimeType();
                if (content == null && image.remoteUrl() != null) {
                    var downloaded = remoteDownloader.download(image.remoteUrl());
                    content = downloaded.content();
                    mimeType = downloaded.mimeType();
                }
                String path = outputStorage.store(jobId, index, content, mimeType);
                records.save(ImageGenerationRecord.builder().jobId(jobId).outputIndex(index).status("SUCCEEDED")
                        .userId(job.getUserId()).mode(job.getMode() == ImageGenerationMode.TEXT_TO_IMAGE ? "GENERATE" : "EDIT")
                        .prompt(job.getPrompt()).revisedPrompt(image.revisedPrompt()).resultPath(path)
                        .createdAt(LocalDateTime.now()).build());
            }
            job.setSuccessCount(generated.size());
            job.setFailureCount(Math.max(0, job.getTargetCount() - generated.size()));
            job.setStatus(generated.size() == job.getTargetCount() ? ImageGenerationJobStatus.SUCCEEDED
                    : generated.isEmpty() ? ImageGenerationJobStatus.FAILED : ImageGenerationJobStatus.PARTIALLY_SUCCEEDED);
            job.setRetryable(job.getStatus() != ImageGenerationJobStatus.SUCCEEDED);
            job.setCompletedAt(LocalDateTime.now());
            job.setExecutionPhase(null);
            job.setWorkerId(null);
            job.setLeaseUntil(null);
            ImageGenerationJob saved = jobs.save(job);
            usageRecorder.record(saved);
            metrics.terminal(saved);
            return saved;
        } catch (RuntimeException error) {
            ImageGenerationExecutionPhase failedPhase = job.getExecutionPhase();
            job.setExecutionPhase(null);
            job.setWorkerId(null);
            job.setLeaseUntil(null);
            boolean invalidConfiguration = error instanceof BusinessException business
                    && business.getErrorCode() == ErrorCode.BAD_REQUEST;
            if (invalidConfiguration) {
                job.setStatus(ImageGenerationJobStatus.FAILED);
                job.setFailureCount(job.getTargetCount());
                job.setErrorCode("INVALID_JOB_CONFIGURATION");
                job.setSafeErrorMessage(error.getMessage());
                job.setRetryable(false);
                job.setCompletedAt(LocalDateTime.now());
            } else if (failedPhase == ImageGenerationExecutionPhase.PREPARING && job.getAttemptCount() < maxAttempts) {
                long exponent = Math.max(0, job.getAttemptCount() - 1);
                long delay = Math.min(300, retryBaseSeconds * (1L << Math.min(exponent, 10)));
                job.setStatus(ImageGenerationJobStatus.PENDING);
                job.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay));
                job.setErrorCode("PREPARATION_RETRY_SCHEDULED");
                job.setSafeErrorMessage("任务准备失败，系统将自动重试");
                job.setRetryable(true);
                job.setCompletedAt(null);
                metrics.retryScheduled(job);
            } else {
                job.setStatus(ImageGenerationJobStatus.FAILED);
                job.setFailureCount(job.getTargetCount());
                boolean submissionUnknown = failedPhase == ImageGenerationExecutionPhase.SUBMITTING
                        && job.getProviderTaskToken() == null;
                job.setErrorCode(submissionUnknown ? "SUBMISSION_OUTCOME_UNKNOWN" : "ADAPTER_EXECUTION_FAILED");
                job.setSafeErrorMessage(submissionUnknown
                        ? "供应商提交结果不确定，请人工确认后重试" : "图片生成失败，请稍后重试");
                job.setRetryable(!submissionUnknown);
                job.setCompletedAt(LocalDateTime.now());
            }
            ImageGenerationJob saved = jobs.save(job);
            if (saved.getStatus() == ImageGenerationJobStatus.FAILED) {
                usageRecorder.record(saved);
                metrics.terminal(saved);
            }
            return saved;
        }
    }

    private String resolveSecret(ImageGenerationJob job) {
        if (job.getCredentialId() != null) return credentials.resolveSecret(job.getCredentialId());
        return models.findById(job.getModelId()).map(AiModel::getApiKey).orElse(null);
    }

    private PollOutcome pollUntilComplete(ImageGenerationJob job,
            ImageGenerationProviderAdapter adapter, String secret) {
        while (true) {
            ImageGenerationJobStatus currentStatus = jobs.findStatusById(job.getId()).orElse(job.getStatus());
            if (currentStatus == ImageGenerationJobStatus.CANCEL_REQUESTED) {
                ImageGenerationJob cancelJob = job;
                metrics.timeProviderCall(cancelJob, "cancel", () -> {
                    adapter.cancel(cancelJob, secret);
                    return null;
                });
                job.setStatus(ImageGenerationJobStatus.CANCELLED);
                job.setExecutionPhase(null);
                job.setWorkerId(null);
                job.setLeaseUntil(null);
                job.setCompletedAt(LocalDateTime.now());
                job = jobs.save(job);
                return new PollOutcome(job, null);
            }
            ImageGenerationJob pollJob = job;
            ProviderPollResult result = metrics.timeProviderCall(pollJob, "poll", () -> adapter.poll(pollJob, secret));
            job.setProviderStatus(result.status().name());
            job = jobs.save(job);
            if (result.status() == ProviderPollResult.Status.SUCCEEDED) return new PollOutcome(job, result.images());
            if (result.status() == ProviderPollResult.Status.FAILED)
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        result.safeError() == null ? "图片供应商任务失败" : result.safeError());
            try { Thread.sleep(Math.max(1, providerPollSeconds) * 1000); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片任务轮询被中断");
            }
        }
    }

    private record PollOutcome(ImageGenerationJob job, List<GeneratedProviderImage> images) {}
}
