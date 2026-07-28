package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.ModelCapability;
import cafe.snails.ecomagents.model.ModelProtocol;
import cafe.snails.ecomagents.repository.AiModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 从 AiModel 的 EMBEDDING 能力中解析当前启用的向量模型。
 */
@Service
@RequiredArgsConstructor
public class EmbeddingModelResolver {
    private static final int DEFAULT_DIMENSION = 1024;

    private final AiModelRepository modelRepository;
    private final ModelCapabilityResolver capabilityResolver;
    private final ObjectMapper objectMapper;

    public Optional<EmbeddingModel> resolve() {
        return modelRepository.findEnabledByCapability(ModelCapability.EMBEDDING).stream()
                .findFirst()
                .map(model -> {
                    ResolvedModelCapability resolved =
                            capabilityResolver.resolve(model.getId(), ModelCapability.EMBEDDING);
                    return new EmbeddingModel(
                            resolved.protocol(),
                            resolved.remoteModelName(),
                            normalizeApiUrl(resolved.protocol(), resolved.apiUrl()),
                            resolved.credentialSecret(),
                            dimension(resolved.optionsJson()));
                });
    }

    private String normalizeApiUrl(ModelProtocol protocol, String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank() || protocol != ModelProtocol.OPENAI_EMBEDDING) {
            return apiUrl;
        }
        String normalized = apiUrl.replaceAll("/+$", "");
        if (normalized.endsWith("/embeddings")) return normalized;
        if (normalized.endsWith("/v1")) return normalized + "/embeddings";
        return normalized + "/v1/embeddings";
    }

    private int dimension(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return DEFAULT_DIMENSION;
        try {
            JsonNode value = objectMapper.readTree(optionsJson).get("dimension");
            return value == null ? DEFAULT_DIMENSION : Math.max(1, value.asInt(DEFAULT_DIMENSION));
        } catch (Exception ignored) {
            return DEFAULT_DIMENSION;
        }
    }

    public record EmbeddingModel(ModelProtocol protocol, String modelName, String apiUrl,
                                 String apiKey, int dimension) {
    }
}
