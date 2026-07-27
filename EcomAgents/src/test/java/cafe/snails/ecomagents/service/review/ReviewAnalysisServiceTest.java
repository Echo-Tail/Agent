package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.review.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewAnalysisServiceTest {
    @Mock ReviewAnalysisProjectRepository projectRepository;
    @Mock ReviewAnalysisRunRepository runRepository;
    @Mock ProductReviewRepository reviewRepository;
    @Mock ReviewAnalysisFailureRepository failureRepository;
    @Mock AiModelRepository modelRepository;
    @Mock ReviewAnalysisWorkerRunner workerRunner;
    @InjectMocks ReviewAnalysisService service;

    @BeforeEach
    void assignRunId() {
        lenient().when(runRepository.save(any())).thenAnswer(invocation -> {
            ReviewAnalysisRun run = invocation.getArgument(0);
            if (run.getId() == null) run.setId(41L);
            return run;
        });
    }

    @Test
    void start_shouldUseDefaultModelChinesePromptAndFiftyReviewBatches() {
        var project = project("collected");
        var model = AiModel.builder().id(5L).enabled(true).isDefault(true)
                .modelType("TEXT").modelName("deepseek-chat").build();
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project));
        when(runRepository.findByProjectIdAndIdempotencyKey(11L, "analysis-1")).thenReturn(Optional.empty());
        when(reviewRepository.countByProjectId(11L)).thenReturn(75L);
        when(runRepository.countByProjectId(11L)).thenReturn(0L);
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(model));
        when(modelRepository.findByModelTypeAndEnabled("TEXT", true)).thenReturn(List.of(model));
        when(modelRepository.findByModelTypeAndEnabled("MULTIMODAL", true)).thenReturn(List.of());

        var result = service.start(11L, "analysis-1", 3L);

        assertEquals(5L, result.modelId());
        assertEquals("pending", result.status());
        verify(runRepository).save(argThat(run -> run.getRolePrompt().contains("简体中文")
                && "review_extraction_v2_zh".equals(run.getPromptVersion())));
        verify(workerRunner).run(41L, 50);
    }

    @Test
    void start_shouldRejectWhenNoTextModelIsConfigured() {
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project("collected")));
        when(runRepository.findByProjectIdAndIdempotencyKey(11L, "analysis-1")).thenReturn(Optional.empty());
        when(reviewRepository.countByProjectId(11L)).thenReturn(1L);
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
        when(modelRepository.findByModelTypeAndEnabled("TEXT", true)).thenReturn(List.of());
        when(modelRepository.findByModelTypeAndEnabled("MULTIMODAL", true)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.start(11L, "analysis-1", 3L));
        verifyNoInteractions(workerRunner);
    }

    private ReviewAnalysisProject project(String status) {
        return ReviewAnalysisProject.builder().id(11L).createdBy(3L).name("Reviews").status(status).build();
    }
}
