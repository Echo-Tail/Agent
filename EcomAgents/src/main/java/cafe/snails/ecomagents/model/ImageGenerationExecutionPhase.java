package cafe.snails.ecomagents.model;

/**
 * 图片生成任务当前所处的执行阶段。
 */
public enum ImageGenerationExecutionPhase {
    PREPARING, SUBMITTING, POLLING, DOWNLOADING, PERSISTING
}
