package cafe.snails.ecomagents.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 图片生成运行时监控指标响应。
 */
public record ImageRuntimeMonitoringResponse(
        LocalDateTime generatedAt,
        Map<String, Long> jobsByStatus,
        double workerActive,
        long completed,
        double successRate,
        double failureRate,
        long timeouts,
        long retries,
        long recovered,
        double averageJobDurationMs,
        double p95JobDurationMs,
        List<ProviderMetrics> providers,
        List<RecentFailure> recentFailures) {

    /**
     * 单个模型供应商的图片生成指标。
     */
    public record ProviderMetrics(String provider, long completed, long failed, double successRate,
            long errors, long timeouts, double averageRequestDurationMs, double p95RequestDurationMs) {}

    /**
     * 最近一次图片生成失败的摘要信息。
     */
    public record RecentFailure(Long id, Long modelId, String provider, String capability,
            String errorCode, String message, boolean retryable, LocalDateTime completedAt) {}
}
