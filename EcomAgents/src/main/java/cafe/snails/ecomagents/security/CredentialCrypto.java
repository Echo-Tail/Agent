package cafe.snails.ecomagents.security;

public interface CredentialCrypto {
    String encrypt(String plaintext);
    String decrypt(String encryptedValue);
}
