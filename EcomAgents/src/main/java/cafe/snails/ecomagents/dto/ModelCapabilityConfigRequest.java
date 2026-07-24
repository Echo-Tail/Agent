package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.ModelCapability;
import cafe.snails.ecomagents.model.ModelProtocol;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModelCapabilityConfigRequest(
        @NotNull ModelCapability capability,
        @NotNull ModelProtocol protocol,
        @Size(max = 100) String modelNameOverride,
        @Size(max = 500) String apiUrlOverride,
        Long credentialIdOverride,
        String optionsJson) {
}
