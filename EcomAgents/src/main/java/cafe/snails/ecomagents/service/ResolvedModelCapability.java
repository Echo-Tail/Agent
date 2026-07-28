package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.ModelCapability;
import cafe.snails.ecomagents.model.ModelProtocol;

/** 模型某项能力解析后的完整运行时配置。 */
public record ResolvedModelCapability(Long modelId, ModelCapability capability, ModelProtocol protocol,
        String provider, String remoteModelName, String apiUrl, Long credentialId,
        String credentialSecret, String optionsJson) {
}
