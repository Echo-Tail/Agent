package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
/** 根据模型协议选择对应的图片生成供应商适配器。 */
public class ImageAdapterRegistry {
    private final List<ImageGenerationProviderAdapter> adapters;
    public ImageAdapterRegistry(List<ImageGenerationProviderAdapter> adapters) { this.adapters = List.copyOf(adapters); }

    public ImageGenerationProviderAdapter require(ModelProtocol protocol, ModelCapability capability) {
        return adapters.stream().filter(adapter -> adapter.supports(protocol, capability)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "没有可用的图片生成适配器: " + protocol + "/" + capability));
    }
}
