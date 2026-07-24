package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import cafe.snails.ecomagents.security.CredentialCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelCapabilityResolver {
    private final AiModelRepository modelRepository;
    private final AiModelCapabilityRepository capabilityRepository;
    private final ModelCredentialRepository credentialRepository;
    private final CredentialCrypto credentialCrypto;

    public ResolvedModelCapability resolve(Long modelId, ModelCapability requestedCapability) {
        AiModel model = modelRepository.findById(modelId)
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型不存在或未启用"));
        AiModelCapability config = capabilityRepository.findByModelIdAndCapability(modelId, requestedCapability)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "模型未配置所需能力"));
        Long credentialId = config.getCredentialIdOverride() != null
                ? config.getCredentialIdOverride() : model.getDefaultCredentialId();
        String secret = credentialId == null ? model.getApiKey() : credentialRepository.findById(credentialId)
                .map(c -> credentialCrypto.decrypt(c.getEncryptedSecret()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型凭据不存在"));
        return new ResolvedModelCapability(modelId, requestedCapability, config.getProtocol(), model.getProvider(),
                firstNonBlank(config.getModelNameOverride(), model.getModelName()),
                firstNonBlank(config.getApiUrlOverride(), model.getApiUrl()), credentialId, secret,
                config.getOptionsJson());
    }

    private String firstNonBlank(String override, String fallback) {
        return override != null && !override.isBlank() ? override : fallback;
    }
}
