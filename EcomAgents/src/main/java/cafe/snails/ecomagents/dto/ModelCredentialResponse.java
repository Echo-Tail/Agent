package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.ModelCredential;
import java.time.LocalDateTime;

public record ModelCredentialResponse(Long id, String name, String provider, String maskedHint,
        Integer encryptionVersion, LocalDateTime createdAt, LocalDateTime updatedAt,
        LocalDateTime lastRotatedAt) {
    public static ModelCredentialResponse from(ModelCredential credential) {
        return new ModelCredentialResponse(credential.getId(), credential.getName(), credential.getProvider(),
                credential.getMaskedHint(), credential.getEncryptionVersion(), credential.getCreatedAt(),
                credential.getUpdatedAt(), credential.getLastRotatedAt());
    }
}
