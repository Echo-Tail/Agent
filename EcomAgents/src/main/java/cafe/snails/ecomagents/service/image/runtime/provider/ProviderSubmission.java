package cafe.snails.ecomagents.service.image.runtime.provider;

import java.util.List;

/** 图片生成请求提交给供应商后的响应。 */
public record ProviderSubmission(String taskToken, List<GeneratedProviderImage> images) {
    public ProviderSubmission { images = images == null ? List.of() : List.copyOf(images); }
    public static ProviderSubmission completed(List<GeneratedProviderImage> images) { return new ProviderSubmission(null, images); }
    public static ProviderSubmission accepted(String taskToken) { return new ProviderSubmission(taskToken, List.of()); }
    public boolean asynchronous() { return taskToken != null && !taskToken.isBlank(); }
}
