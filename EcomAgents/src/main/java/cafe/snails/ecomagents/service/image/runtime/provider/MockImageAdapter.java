package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.model.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.util.*;

@Component
@Order(0)
@ConditionalOnProperty(name="image.runtime.mock-adapter-enabled", havingValue="true")
/** 用于本地开发和测试的模拟图片生成适配器。 */
public class MockImageAdapter implements ImageGenerationProviderAdapter {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    @Override public boolean supports(ModelProtocol protocol, ModelCapability capability) {
        return (protocol == ModelProtocol.OPENAI_IMAGE || protocol == ModelProtocol.BAILIAN_IMAGE) &&
                (capability == ModelCapability.TEXT_TO_IMAGE || capability == ModelCapability.IMAGE_TO_IMAGE);
    }
    @Override public List<GeneratedProviderImage> generate(ImageGenerationJob job,
            List<ImageGenerationJobInput> inputs, String credentialSecret) {
        List<GeneratedProviderImage> result = new ArrayList<>();
        for (int i = 0; i < job.getTargetCount(); i++)
            result.add(GeneratedProviderImage.inline(PNG, "image/png", job.getPrompt()));
        return result;
    }
}
