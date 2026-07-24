package cafe.snails.ecomagents.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    public record ProviderMetrics(String provider, long completed, long failed, double successRate,
            long errors, long timeouts, double averageRequestDurationMs, double p95RequestDurationMs) {}

    public record RecentFailure(Long id, Long modelId, String provider, String capability,
            String errorCode, String message, boolean retryable, LocalDateTime completedAt) {}
}
