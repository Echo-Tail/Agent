package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import cafe.snails.ecomagents.security.CredentialCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelCapabilityResolverTest {
    @Mock AiModelRepository models;
    @Mock AiModelCapabilityRepository capabilities;
    @Mock ModelCredentialRepository credentials;
    @Mock CredentialCrypto crypto;
    ModelCapabilityResolver resolver;

    @BeforeEach
    void setUp() { resolver = new ModelCapabilityResolver(models, capabilities, credentials, crypto); }

    @Test
    void capabilityOverridesShouldWinOverModelDefaults() {
        var model = AiModel.builder().id(1L).provider("qwen").modelName("default-model")
                .apiUrl("https://default.example").defaultCredentialId(10L).enabled(true).build();
        var capability = AiModelCapability.builder().modelId(1L).capability(ModelCapability.TEXT_TO_IMAGE)
                .protocol(ModelProtocol.BAILIAN_IMAGE).modelNameOverride("wanx-v1")
                .apiUrlOverride("https://override.example").credentialIdOverride(11L).build();
        var credential = ModelCredential.builder().id(11L).encryptedSecret("ciphertext").build();
        when(models.findById(1L)).thenReturn(Optional.of(model));
        when(capabilities.findByModelIdAndCapability(1L, ModelCapability.TEXT_TO_IMAGE))
                .thenReturn(Optional.of(capability));
        when(credentials.findById(11L)).thenReturn(Optional.of(credential));
        when(crypto.decrypt("ciphertext")).thenReturn("secret");

        var resolved = resolver.resolve(1L, ModelCapability.TEXT_TO_IMAGE);

        assertEquals("wanx-v1", resolved.remoteModelName());
        assertEquals("https://override.example", resolved.apiUrl());
        assertEquals(11L, resolved.credentialId());
        assertEquals("secret", resolved.credentialSecret());
        assertEquals(ModelProtocol.BAILIAN_IMAGE, resolved.protocol());
    }

    @Test
    void shouldFallbackToModelDefaultsAndLegacySecret() {
        var model = AiModel.builder().id(1L).provider("openai").modelName("image-model")
                .apiUrl("https://api.example").apiKey("legacy-secret").enabled(true).build();
        var capability = AiModelCapability.builder().modelId(1L).capability(ModelCapability.IMAGE_TO_IMAGE)
                .protocol(ModelProtocol.OPENAI_IMAGE).build();
        when(models.findById(1L)).thenReturn(Optional.of(model));
        when(capabilities.findByModelIdAndCapability(1L, ModelCapability.IMAGE_TO_IMAGE))
                .thenReturn(Optional.of(capability));

        var resolved = resolver.resolve(1L, ModelCapability.IMAGE_TO_IMAGE);

        assertEquals("image-model", resolved.remoteModelName());
        assertEquals("legacy-secret", resolved.credentialSecret());
        verifyNoInteractions(credentials, crypto);
    }

    @Test
    void shouldRejectMissingCapability() {
        var model = AiModel.builder().id(1L).enabled(true).build();
        when(models.findById(1L)).thenReturn(Optional.of(model));
        when(capabilities.findByModelIdAndCapability(1L, ModelCapability.TEXT_TO_IMAGE))
                .thenReturn(Optional.empty());
        assertThrows(BusinessException.class,
                () -> resolver.resolve(1L, ModelCapability.TEXT_TO_IMAGE));
    }
}
