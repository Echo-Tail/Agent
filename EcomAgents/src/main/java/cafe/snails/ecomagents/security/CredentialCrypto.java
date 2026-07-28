package cafe.snails.ecomagents.security;

/**
 * 模型访问凭证的加密与解密抽象。
 */
public interface CredentialCrypto {
    /** 将凭证明文加密为可持久化的密文。 */
    String encrypt(String plaintext);
    /** 将持久化密文解密为凭证明文。 */
    String decrypt(String encryptedValue);
}
