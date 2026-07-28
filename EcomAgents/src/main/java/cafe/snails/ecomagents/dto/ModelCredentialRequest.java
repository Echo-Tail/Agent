package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建模型访问凭证的请求。
 */
public record ModelCredentialRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String provider,
        @NotBlank String secret) {
}
