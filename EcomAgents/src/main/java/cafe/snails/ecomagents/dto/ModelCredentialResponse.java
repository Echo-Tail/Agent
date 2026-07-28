package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.ModelCredential;
import java.time.LocalDateTime;

/**
 * 脱敏后的模型访问凭证响应。
 */
public record ModelCredentialResponse(Long id, String name, String provider, String maskedHint,
        Integer encryptionVersion, LocalDateTime createdAt, LocalDateTime updatedAt,
        LocalDateTime lastRotatedAt) {
    /**
     * 将模型凭证实体转换为脱敏响应对象。
     */
    public static ModelCredentialResponse from(ModelCredential credential) {
        return new ModelCredentialResponse(credential.getId(), credential.getName(), credential.getProvider(),
                credential.getMaskedHint(), credential.getEncryptionVersion(), credential.getCreatedAt(),
                credential.getUpdatedAt(), credential.getLastRotatedAt());
    }
}
