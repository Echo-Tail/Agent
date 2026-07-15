package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ModelCapabilityConfigRequest;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.AiModelCapability;
import cafe.snails.ecomagents.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ModelCapabilityService {
    private final AiModelRepository modelRepository;
    private final AiModelCapabilityRepository capabilityRepository;
    private final ModelCredentialRepository credentialRepository;

    @Transactional(readOnly = true)
    public List<AiModelCapability> list(Long modelId) {
        requireModel(modelId);
        return capabilityRepository.findByModelIdOrderById(modelId);
    }

    @Transactional
    public List<AiModelCapability> replace(Long modelId, List<ModelCapabilityConfigRequest> requests) {
        requireModel(modelId);
        Set<cafe.snails.ecomagents.model.ModelCapability> unique = new HashSet<>();
        for (var request : requests) {
            if (!unique.add(request.capability())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "模型能力不能重复");
            }
            if (request.credentialIdOverride() != null &&
                    !credentialRepository.existsById(request.credentialIdOverride())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "能力覆盖凭据不存在");
            }
        }
        capabilityRepository.deleteByModelId(modelId);
        return capabilityRepository.saveAll(requests.stream().map(request -> AiModelCapability.builder()
                .modelId(modelId).capability(request.capability()).protocol(request.protocol())
                .modelNameOverride(blankToNull(request.modelNameOverride()))
                .apiUrlOverride(blankToNull(request.apiUrlOverride()))
                .credentialIdOverride(request.credentialIdOverride())
                .optionsJson(blankToNull(request.optionsJson())).build()).toList());
    }

    private void requireModel(Long modelId) {
        if (!modelRepository.existsById(modelId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型不存在");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
