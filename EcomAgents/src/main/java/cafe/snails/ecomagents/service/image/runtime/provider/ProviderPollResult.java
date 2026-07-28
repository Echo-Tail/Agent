package cafe.snails.ecomagents.service.image.runtime.provider;

import java.util.List;

/** 异步图片生成供应商的任务轮询结果。 */
public record ProviderPollResult(Status status, List<GeneratedProviderImage> images, String safeError) {
    public enum Status { PENDING, SUCCEEDED, FAILED }
    public ProviderPollResult { images = images == null ? List.of() : List.copyOf(images); }
    public static ProviderPollResult pending() { return new ProviderPollResult(Status.PENDING, List.of(), null); }
    public static ProviderPollResult succeeded(List<GeneratedProviderImage> images) { return new ProviderPollResult(Status.SUCCEEDED, images, null); }
    public static ProviderPollResult failed(String safeError) { return new ProviderPollResult(Status.FAILED, List.of(), safeError); }
}
