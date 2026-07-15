package cafe.snails.ecomagents.security;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class AesGcmCredentialCryptoTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void shouldRoundTripWithoutExposingPlaintext() {
        var crypto = new AesGcmCredentialCrypto(key);
        String encrypted = crypto.encrypt("sk-sensitive-value");
        assertNotEquals("sk-sensitive-value", encrypted);
        assertFalse(encrypted.contains("sensitive"));
        assertEquals("sk-sensitive-value", crypto.decrypt(encrypted));
    }

    @Test
    void shouldUseRandomNonceForEveryEncryption() {
        var crypto = new AesGcmCredentialCrypto(key);
        assertNotEquals(crypto.encrypt("same-secret"), crypto.encrypt("same-secret"));
    }

    @Test
    void shouldFailClosedWhenMasterKeyIsMissing() {
        var crypto = new AesGcmCredentialCrypto("");
        var error = assertThrows(IllegalStateException.class, () -> crypto.encrypt("secret"));
        assertTrue(error.getMessage().contains("MODEL_CREDENTIAL_MASTER_KEY"));
    }

    @Test
    void shouldRejectTamperedCiphertext() {
        var crypto = new AesGcmCredentialCrypto(key);
        byte[] payload = Base64.getDecoder().decode(crypto.encrypt("secret"));
        payload[payload.length - 1] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(Base64.getEncoder().encodeToString(payload)));
    }
}
