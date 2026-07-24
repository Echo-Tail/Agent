package cafe.snails.ecomagents.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesGcmCredentialCrypto implements CredentialCrypto {
    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final String encodedMasterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCredentialCrypto(@Value("${model.credentials.master-key:}") String encodedMasterKey) {
        this.encodedMasterKey = encodedMasterKey;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("凭据不能为空");
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(new byte[]{FORMAT_VERSION});
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(1 + nonce.length + ciphertext.length)
                    .put(FORMAT_VERSION).put(nonce).put(ciphertext).array());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    @Override
    public String decrypt(String encryptedValue) {
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(encryptedValue));
            byte version = payload.get();
            if (version != FORMAT_VERSION) throw new IllegalArgumentException("不支持的凭据加密版本");
            byte[] nonce = new byte[NONCE_LENGTH];
            payload.get(nonce);
            byte[] ciphertext = new byte[payload.remaining()];
            payload.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(new byte[]{version});
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("凭据解密失败", e);
        }
    }

    private SecretKeySpec key() {
        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            throw new IllegalStateException("未配置 MODEL_CREDENTIAL_MASTER_KEY");
        }
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedMasterKey); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("凭据主密钥必须是 Base64", e); }
        if (bytes.length != 32) throw new IllegalStateException("凭据主密钥解码后必须为 32 字节");
        return new SecretKeySpec(bytes, "AES");
    }
}
