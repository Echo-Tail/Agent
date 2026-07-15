package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.service.image.runtime.command.TextToImageCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageGenerationWorkflowServiceTest {
    @Test
    void resolvesCapabilityModelAndMapsAllSuccessfulOutputs() {
        ImageGenerationRuntime runtime = mock(ImageGenerationRuntime.class);
        AiModelRepository models = mock(AiModelRepository.class);
        when(models.findEnabledByCapability(ModelCapability.TEXT_TO_IMAGE))
                .thenReturn(List.of(AiModel.builder().id(8L).build()));
        when(runtime.submit(any())).thenReturn(ImageGenerationJob.builder().id(11L).build());
        var service = new ImageGenerationWorkflowService(runtime, models, new ObjectMapper());

        service.submitText(7L, null, "product", "2048x2048", "high", 2, null);

        ArgumentCaptor<TextToImageCommand> command = ArgumentCaptor.forClass(TextToImageCommand.class);
        verify(runtime).submit(command.capture());
        assertEquals(8L, command.getValue().modelId());
        assertTrue(command.getValue().optionsJson().contains("2048x2048"));

        ImageGenerationJob completed = ImageGenerationJob.builder().id(11L).failureCount(0)
                .startedAt(LocalDateTime.now().minusSeconds(1)).completedAt(LocalDateTime.now()).build();
        var awaited = new ImageGenerationWorkflowService.AwaitResult(completed, List.of(
                ImageGenerationRecord.builder().id(1L).status("SUCCEEDED").resultPath("/uploads/a.png").build(),
                ImageGenerationRecord.builder().id(2L).status("SUCCEEDED").resultPath("/uploads/b.png").build()), true);
        assertEquals(2, service.result(awaited).urls().size());
    }
}
