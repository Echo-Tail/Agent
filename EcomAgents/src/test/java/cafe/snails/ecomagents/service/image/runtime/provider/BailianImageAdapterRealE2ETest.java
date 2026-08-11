package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.service.ProxySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("bailian-e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BailianImageAdapterRealE2ETest {
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-image-2.0-pro";

    @TempDir Path tempDir;
    private BailianImageAdapter adapter;
    private String apiKey;
    private String baseUrl;
    private String model;

    @BeforeAll
    void requireExplicitOptIn() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("BAILIAN_E2E_ENABLED")),
                "Set BAILIAN_E2E_ENABLED=true to acknowledge that this test calls a billable API");
        apiKey = System.getenv("BAILIAN_E2E_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "BAILIAN_E2E_API_KEY is required");
        baseUrl = environmentOrDefault("BAILIAN_E2E_BASE_URL", DEFAULT_BASE_URL);
        model = environmentOrDefault("BAILIAN_E2E_MODEL", DEFAULT_MODEL);
    }

    @BeforeEach
    void setUp() throws Exception {
        ProxySettingsService proxySettingsService = mock(ProxySettingsService.class);
        when(proxySettingsService.openConnection(any())).thenAnswer(
                invocation -> invocation.<java.net.URL>getArgument(0).openConnection(Proxy.NO_PROXY));
        adapter = new BailianImageAdapter(new ObjectMapper(), proxySettingsService);
        ReflectionTestUtils.setField(adapter, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(adapter, "timeoutSeconds", 600);
    }

    @Test
    void realTextToImageReturnsDownloadableImage() throws Exception {
        ImageGenerationJob job = job(ModelCapability.TEXT_TO_IMAGE,
                "A minimal blue cube centered on a clean white background, automated integration test");

        ProviderSubmission submission = adapter.submit(job, List.of(), apiKey);
        List<GeneratedProviderImage> images = complete(submission, job);

        assertEquals(1, images.size());
        assertDownloadableImage(images.get(0).remoteUrl());
    }

    @Test
    void realTextToImageSupportsMultipleOutputs() throws Exception {
        ImageGenerationJob job = job(ModelCapability.TEXT_TO_IMAGE,
                "A simple red circle centered on a clean white background, automated integration test");
        job.setTargetCount(2);

        ProviderSubmission submission = adapter.submit(job, List.of(), apiKey);
        List<GeneratedProviderImage> images = complete(submission, job);

        assertEquals(2, images.size());
        for (GeneratedProviderImage image : images) assertDownloadableImage(image.remoteUrl());
    }

    @Test
    void realImageToImageUsesImmutableSnapshotAndReturnsDownloadableImage() throws Exception {
        Path fixture = tempDir.resolve("image-jobs/1/inputs/reference.png");
        Files.createDirectories(fixture.getParent());
        writeFixture(fixture);
        ImageGenerationJobInput input = ImageGenerationJobInput.builder()
                .jobId(1L).inputIndex(0).role(ImageJobInputRole.REFERENCE)
                .sourceType(ImageJobInputSourceType.UPLOAD)
                .snapshotPath("/uploads/image-jobs/1/inputs/reference.png")
                .mimeType("image/png").fileSize(Files.size(fixture)).sha256("0".repeat(64)).build();
        ImageGenerationJob job = job(ModelCapability.IMAGE_TO_IMAGE,
                "Change the blue square to red while keeping the plain white background");

        ProviderSubmission submission = adapter.submit(job, List.of(input), apiKey);
        List<GeneratedProviderImage> images = complete(submission, job);

        assertEquals(1, images.size());
        assertDownloadableImage(images.get(0).remoteUrl());
    }

    private List<GeneratedProviderImage> complete(ProviderSubmission submission, ImageGenerationJob job) throws Exception {
        if (!submission.asynchronous()) return submission.images();
        job.setProviderTaskToken(submission.taskToken());
        long deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
        while (System.nanoTime() < deadline) {
            ProviderPollResult result = adapter.poll(job, apiKey);
            if (result.status() == ProviderPollResult.Status.SUCCEEDED) return result.images();
            if (result.status() == ProviderPollResult.Status.FAILED) fail(result.safeError());
            Thread.sleep(3000);
        }
        fail("Bailian image task did not complete within 10 minutes");
        return List.of();
    }

    private void assertDownloadableImage(String url) throws Exception {
        assertNotNull(url);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().length > 1024, "Generated image response is unexpectedly small");
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("image/"));
    }

    private ImageGenerationJob job(ModelCapability capability, String prompt) {
        return ImageGenerationJob.builder().id(1L).userId(1L).modelId(1L)
                .mode(capability == ModelCapability.TEXT_TO_IMAGE
                        ? ImageGenerationMode.TEXT_TO_IMAGE : ImageGenerationMode.IMAGE_TO_IMAGE)
                .prompt(prompt).targetCount(1).provider("ALIYUN_BAILIAN")
                .protocol(ModelProtocol.BAILIAN_IMAGE).remoteModelName(model).apiUrl(baseUrl)
                .capability(capability).optionsJson("{\"size\":\"2048*2048\",\"watermark\":false}")
                .build();
    }

    private void writeFixture(Path path) throws Exception {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 512, 512);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(156, 156, 200, 200);
        } finally {
            graphics.dispose();
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }

    private String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
