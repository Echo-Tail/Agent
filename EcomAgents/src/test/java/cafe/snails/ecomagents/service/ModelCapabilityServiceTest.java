package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ModelCapabilityConfigRequest;
import cafe.snails.ecomagents.model.ModelCapability;
import cafe.snails.ecomagents.model.ModelProtocol;
import cafe.snails.ecomagents.repository.AiModelCapabilityRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ModelCredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelCapabilityServiceTest {
    @Mock private AiModelRepository modelRepository;
    @Mock private AiModelCapabilityRepository capabilityRepository;
    @Mock private ModelCredentialRepository credentialRepository;

    @Test
    void replaceFlushesDeletedCapabilitiesBeforeInsertingReplacements() {
        when(modelRepository.existsById(6L)).thenReturn(true);
        var service = new ModelCapabilityService(
                modelRepository, capabilityRepository, credentialRepository);
        var request = new ModelCapabilityConfigRequest(
                ModelCapability.IMAGE_TO_IMAGE,
                ModelProtocol.OPENAI_IMAGE,
                null, null, null, null);

        service.replace(6L, List.of(request));

        InOrder order = inOrder(capabilityRepository);
        order.verify(capabilityRepository).deleteByModelId(6L);
        order.verify(capabilityRepository).flush();
        order.verify(capabilityRepository).saveAll(anyList());
    }
}
