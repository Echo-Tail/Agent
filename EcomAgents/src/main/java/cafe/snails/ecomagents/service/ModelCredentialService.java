package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.ModelCredential;
import cafe.snails.ecomagents.repository.ModelCredentialRepository;
import cafe.snails.ecomagents.security.CredentialCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCredentialService {
    private final ModelCredentialRepository repository;
    private final CredentialCrypto crypto;

    @Transactional(readOnly = true)
    public List<ModelCredentialResponse> list() {
        return repository.findAll().stream().map(ModelCredentialResponse::from).toList();
    }

    @Transactional
    public ModelCredential create(ModelCredentialRequest request) {
        ModelCredential credential = ModelCredential.builder()
                .name(request.name().trim()).provider(request.provider().trim().toLowerCase())
                .encryptedSecret(crypto.encrypt(request.secret())).maskedHint(mask(request.secret()))
                .encryptionVersion(1).build();
        ModelCredential saved = repository.save(credential);
        log.info("Model credential created: id={}, provider={}", saved.getId(), saved.getProvider());
        return saved;
    }

    @Transactional
    public ModelCredentialResponse rotate(Long id, String secret) {
        ModelCredential credential = require(id);
        credential.setEncryptedSecret(crypto.encrypt(secret));
        credential.setMaskedHint(mask(secret));
        credential.setLastRotatedAt(LocalDateTime.now());
        ModelCredential saved = repository.save(credential);
        log.info("Model credential rotated: id={}, provider={}", saved.getId(), saved.getProvider());
        return ModelCredentialResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        ModelCredential credential = require(id);
        repository.delete(credential);
        log.info("Model credential deleted: id={}, provider={}", id, credential.getProvider());
    }

    @Transactional(readOnly = true)
    public String resolveSecret(Long id) {
        return crypto.decrypt(require(id).getEncryptedSecret());
    }

    private ModelCredential require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型凭据不存在"));
    }

    static String mask(String secret) {
        if (secret == null || secret.length() <= 8) return "****";
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
