package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.ModelCapability;
import cafe.snails.ecomagents.model.ModelProtocol;

public record ResolvedModelCapability(Long modelId, ModelCapability capability, ModelProtocol protocol,
        String provider, String remoteModelName, String apiUrl, Long credentialId,
        String credentialSecret, String optionsJson) {
}
