package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统代理设置相关的数据传输对象集合。
 */
public final class ProxySettingsDtos {
    private ProxySettingsDtos() {
    }

    /** 更新系统代理配置的请求。 */
    public record UpdateRequest(
            @NotNull Boolean enabled,
            @Size(max = 500) String proxyUrl) {
    }

    /** 系统代理配置响应。 */
    public record SettingsResponse(
            boolean enabled,
            String proxyUrl,
            Long updatedBy,
            LocalDateTime updatedAt) {
    }

    /** 自动探测到的代理候选项。 */
    public record ProxyCandidate(
            String proxyUrl,
            String source,
            boolean reachable) {
    }

    /** 系统代理自动探测结果。 */
    public record DetectionResponse(
            boolean detected,
            String suggestedProxyUrl,
            List<ProxyCandidate> candidates) {
    }

    /** 代理连通性测试请求。 */
    public record TestRequest(@Size(max = 500) String proxyUrl) {
    }

    /** 代理连通性测试结果。 */
    public record TestResponse(
            boolean success,
            String message,
            Integer httpStatus,
            long durationMs) {
    }
}
