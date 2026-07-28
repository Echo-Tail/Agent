package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 轮换模型访问凭证密钥的请求。
 */
public record RotateCredentialRequest(@NotBlank String secret) {
}
