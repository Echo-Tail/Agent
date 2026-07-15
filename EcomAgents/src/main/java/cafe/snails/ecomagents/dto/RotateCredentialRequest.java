package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;

public record RotateCredentialRequest(@NotBlank String secret) {
}
