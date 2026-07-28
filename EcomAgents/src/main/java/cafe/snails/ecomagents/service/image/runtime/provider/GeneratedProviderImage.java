package cafe.snails.ecomagents.service.image.runtime.provider;

/** 图片供应商返回的单张生成结果。 */
public record GeneratedProviderImage(byte[] content, String remoteUrl, String mimeType, String revisedPrompt) {
    public GeneratedProviderImage {
        content = content == null ? null : content.clone();
    }
    @Override public byte[] content() { return content == null ? null : content.clone(); }

    public static GeneratedProviderImage inline(byte[] content, String mimeType, String revisedPrompt) {
        return new GeneratedProviderImage(content, null, mimeType, revisedPrompt);
    }

    public static GeneratedProviderImage remote(String url, String revisedPrompt) {
        return new GeneratedProviderImage(null, url, null, revisedPrompt);
    }
}
