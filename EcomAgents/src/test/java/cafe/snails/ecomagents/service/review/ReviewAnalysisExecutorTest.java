package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import cafe.snails.ecomagents.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.GenerateOptions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewAnalysisExecutorTest {
    @Mock ReviewAnalysisRunRepository runRepository;
    @Mock ReviewAnalysisProjectRepository projectRepository;
    @Mock ProductReviewRepository reviewRepository;
    @Mock ReviewInsightRepository insightRepository;
    @Mock ReviewAnalysisFailureRepository failureRepository;
    @Mock AiModelService aiModelService;
    @Mock LlmService llmService;
    @Mock ReviewOpportunityService opportunityService;
    ReviewAnalysisExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ReviewAnalysisExecutor(runRepository, projectRepository, reviewRepository,
                insightRepository, failureRepository, aiModelService, llmService,
                new ReviewInsightParser(new ObjectMapper()), opportunityService, new ObjectMapper());
        lenient().when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void execute_shouldCallConfiguredModelAndPersistValidatedInsights() {
        var run = ReviewAnalysisRun.builder().id(41L).projectId(11L).modelId(5L)
                .rolePrompt("You are a car stereo analyst.")
                .processedReviewCount(0).failedReviewCount(0).build();
        var project = ReviewAnalysisProject.builder().id(11L).status("analyzing").build();
        var review = ProductReview.builder().id(9L).projectId(11L).asin("B0AAAA1111")
                .reviewText("CarPlay disconnects.").verifiedPurchase(true).helpfulCount(2).build();
        var options = GenerateOptions.builder().modelName("configured-model").build();
        when(runRepository.findById(41L)).thenReturn(Optional.of(run));
        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectIdOrderById(11L)).thenReturn(List.of(review));
        when(aiModelService.buildModelOptions(5L)).thenReturn(options);
        when(llmService.syncChat(anyString(), anyList(), same(options))).thenReturn("""
                {"schema_version":"review_insight_v1","reviews":[{
                  "review_id":9,"insights":[{
                    "user_problem":"CarPlay disconnects",
                    "usage_scenario":"daily_commute",
                    "product_module":"carplay",
                    "severity":"major",
                    "sentiment":"negative",
                    "evidence_quote":"CarPlay disconnects.",
                    "action_type":"firmware",
                    "improvement_action":"Improve reconnection",
                    "return_risk":4,
                    "conversion_risk":4,
                    "confidence":0.9
                  }]
                }]}
                """);

        executor.execute(41L, 15);

        assertEquals("draft", run.getStatus());
        assertEquals(1, run.getProcessedReviewCount());
        assertEquals(0, run.getFailedReviewCount());
        assertEquals("review", project.getStatus());
        verify(insightRepository).saveAll(argThat(values -> values.iterator().hasNext()));
        verify(llmService).syncChat(contains("You are a car stereo analyst."), anyList(), same(options));
        verify(opportunityService).generate(41L);
    }

    @Test
    void execute_shouldIsolateBadReviewAndCheckpointOnlyItsFailure() {
        var run = ReviewAnalysisRun.builder().id(41L).projectId(11L).modelId(5L)
                .rolePrompt("Analyst").processedReviewCount(0).failedReviewCount(0).build();
        var project = ReviewAnalysisProject.builder().id(11L).status("analyzing").build();
        var first = ProductReview.builder().id(1L).projectId(11L).asin("B0AAAA1111")
                .reviewText("Works well.").verifiedPurchase(false).helpfulCount(0).build();
        var second = ProductReview.builder().id(2L).projectId(11L).asin("B0AAAA1111")
                .reviewText("Bad Bluetooth.").verifiedPurchase(false).helpfulCount(0).build();
        var options = GenerateOptions.builder().modelName("configured-model").build();
        when(runRepository.findById(41L)).thenReturn(Optional.of(run));
        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectIdOrderById(11L)).thenReturn(List.of(first, second));
        when(aiModelService.buildModelOptions(5L)).thenReturn(options);
        when(llmService.syncChat(anyString(), anyList(), same(options)))
                .thenThrow(new IllegalArgumentException("bad batch"))
                .thenReturn("""
                        {"schema_version":"review_insight_v1","reviews":[{"review_id":1,"insights":[]}]}
                        """)
                .thenThrow(new IllegalArgumentException("bad review"));
        when(failureRepository.findByAnalysisRunIdAndReviewId(41L, 2L)).thenReturn(Optional.empty());

        executor.execute(41L, 10);

        assertEquals(1, run.getProcessedReviewCount());
        assertEquals(1, run.getFailedReviewCount());
        verify(failureRepository).save(argThat(value -> value.getReviewId() == 2L && value.getAttemptCount() == 1));
    }

    @Test
    void execute_shouldNeverSendMoreThanFiftyReviewsPerLlmRequest() throws Exception {
        var run = ReviewAnalysisRun.builder().id(41L).projectId(11L).modelId(5L)
                .rolePrompt("Analyst").processedReviewCount(0).failedReviewCount(0).build();
        var project = ReviewAnalysisProject.builder().id(11L).status("analyzing").build();
        List<ProductReview> reviews = new ArrayList<>();
        for (long id = 1; id <= 51; id++) {
            reviews.add(ProductReview.builder().id(id).projectId(11L).asin("B0AAAA1111")
                    .reviewText("Review " + id).verifiedPurchase(false).helpfulCount(0).build());
        }
        var options = GenerateOptions.builder().modelName("configured-model").build();
        when(runRepository.findById(41L)).thenReturn(Optional.of(run));
        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectIdOrderById(11L)).thenReturn(reviews);
        when(aiModelService.buildModelOptions(5L)).thenReturn(options);
        ObjectMapper mapper = new ObjectMapper();
        when(llmService.syncChat(anyString(), anyList(), same(options))).thenAnswer(invocation -> {
            List<Map<String, Object>> messages = invocation.getArgument(1);
            String content = String.valueOf(messages.get(0).get("content"));
            var input = mapper.readTree(content.substring(content.indexOf('\n') + 1));
            var output = mapper.createObjectNode().put("schema_version", "review_insight_v1");
            var rows = output.putArray("reviews");
            input.forEach(value -> rows.addObject().put("review_id", value.path("review_id").asLong()).putArray("insights"));
            return mapper.writeValueAsString(output);
        });

        executor.execute(41L, 500);

        assertEquals(51, run.getProcessedReviewCount());
        verify(llmService, times(2)).syncChat(anyString(), anyList(), same(options));
    }
}
