package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModelCredentialRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String provider,
        @NotBlank String secret) {
}
