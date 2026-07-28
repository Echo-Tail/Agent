package cafe.snails.ecomagents.model;

/**
 * 图片生成任务的生命周期状态。
 */
public enum ImageGenerationJobStatus {
    PENDING, RUNNING, SUCCEEDED, PARTIALLY_SUCCEEDED, FAILED, CANCEL_REQUESTED, CANCELLED
}
