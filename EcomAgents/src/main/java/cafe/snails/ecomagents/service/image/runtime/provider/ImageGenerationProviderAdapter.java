package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.model.*;
import java.util.List;

/** 定义不同供应商图片生成接口的统一适配规范。 */
public interface ImageGenerationProviderAdapter {
    boolean supports(ModelProtocol protocol, ModelCapability capability);
    default void validate(ImageGenerationJob job, List<ImageGenerationJobInput> inputs) {}
    List<GeneratedProviderImage> generate(ImageGenerationJob job, List<ImageGenerationJobInput> inputs,
            String credentialSecret);

    default ProviderSubmission submit(ImageGenerationJob job, List<ImageGenerationJobInput> inputs,
            String credentialSecret) {
        return ProviderSubmission.completed(generate(job, inputs, credentialSecret));
    }

    default ProviderPollResult poll(ImageGenerationJob job, String credentialSecret) {
        throw new UnsupportedOperationException("Adapter does not support polling");
    }

    default boolean cancel(ImageGenerationJob job, String credentialSecret) { return false; }
}
