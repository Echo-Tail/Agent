package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.service.ProxySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BailianImageAdapterTest {
    HttpServer server;
    BailianImageAdapter adapter;
    @TempDir Path tempDir;

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ProxySettingsService proxySettingsService = mock(ProxySettingsService.class);
        try {
            when(proxySettingsService.openConnection(any())).thenAnswer(
                    invocation -> invocation.<java.net.URL>getArgument(0).openConnection(Proxy.NO_PROXY));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        adapter = new BailianImageAdapter(new ObjectMapper(), proxySettingsService);
        ReflectionTestUtils.setField(adapter, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(adapter, "timeoutSeconds", 5);
    }
    @AfterEach void tearDown() { server.stop(0); }

    @Test
    void qwenImageShouldUseSynchronousMultimodalEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/v1/services/aigc/multimodal-generation/generation", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertNull(exchange.getRequestHeaders().getFirst("X-DashScope-Async"));
            respond(exchange, 200, "{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://cdn.example/qwen.png\"}]}}]}}");
        });
        server.start();

        ProviderSubmission submission = adapter.submit(job("qwen-image-2.0", ModelCapability.TEXT_TO_IMAGE), List.of(), "key");

        assertFalse(submission.asynchronous());
        assertEquals("https://cdn.example/qwen.png", submission.images().get(0).remoteUrl());
        JsonNodeAssert.assertModelAndPrompt(requestBody.get(), "qwen-image-2.0", "a product");
    }

    @Test
    void wanModelShouldSubmitAsyncAndPollTaskResults() throws Exception {
        AtomicReference<String> asyncHeader = new AtomicReference<>();
        server.createContext("/api/v1/services/aigc/image-generation/generation", exchange -> {
            asyncHeader.set(exchange.getRequestHeaders().getFirst("X-DashScope-Async"));
            respond(exchange, 200, "{\"output\":{\"task_id\":\"task-123\"}}");
        });
        server.createContext("/api/v1/tasks/task-123", exchange ->
                respond(exchange, 200, "{\"output\":{\"task_status\":\"SUCCEEDED\",\"results\":[{\"url\":\"https://cdn.example/wan.png\"}]}}"));
        server.start();
        ImageGenerationJob job = job("wan2.6-t2i", ModelCapability.TEXT_TO_IMAGE);

        ProviderSubmission submission = adapter.submit(job, List.of(), "key");
        job.setProviderTaskToken(submission.taskToken());
        ProviderPollResult poll = adapter.poll(job, "key");

        assertEquals("enable", asyncHeader.get());
        assertEquals("task-123", submission.taskToken());
        assertEquals(ProviderPollResult.Status.SUCCEEDED, poll.status());
        assertEquals("https://cdn.example/wan.png", poll.images().get(0).remoteUrl());
    }

    @Test
    void customCompatibleAddressShouldBeConvertedToNativeOrigin() throws Exception {
        server.createContext("/api/v1/services/aigc/multimodal-generation/generation", exchange ->
                respond(exchange, 200, "{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://cdn.example/a.png\"}]}}]}}"));
        server.start();
        ImageGenerationJob job = job("qwen-image-2.0", ModelCapability.TEXT_TO_IMAGE);
        job.setApiUrl(baseUrl() + "/compatible-mode/v1");
        assertDoesNotThrow(() -> adapter.submit(job, List.of(), "key"));
    }

    @Test
    void imageEditShouldEmbedImmutableSnapshotInMultimodalContent() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/v1/services/aigc/multimodal-generation/generation", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://cdn.example/edit.png\"}]}}]}}");
        });
        server.start();
        Path snapshotFile = tempDir.resolve("image-jobs/1/inputs/ref.png");
        Files.createDirectories(snapshotFile.getParent());
        Files.write(snapshotFile, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        var snapshot = ImageGenerationJobInput.builder().jobId(1L).inputIndex(0)
                .role(ImageJobInputRole.REFERENCE).sourceType(ImageJobInputSourceType.UPLOAD)
                .snapshotPath("/uploads/image-jobs/1/inputs/ref.png").mimeType("image/png")
                .fileSize(4L).sha256("0".repeat(64)).build();

        adapter.submit(job("qwen-image-2.0", ModelCapability.IMAGE_TO_IMAGE), List.of(snapshot), "key");

        var content = new ObjectMapper().readTree(requestBody.get()).path("input").path("messages").get(0).path("content");
        assertTrue(content.get(0).path("image").asText().startsWith("data:image/png;base64,"));
        assertEquals("a product", content.get(1).path("text").asText());
    }

    @Test
    void providerTimeoutShouldReturnSafeBusinessError() throws Exception {
        server.createContext("/api/v1/services/aigc/multimodal-generation/generation", exchange -> {
            try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, "{\"output\":{\"choices\":[]}}");
        });
        server.start();
        ReflectionTestUtils.setField(adapter, "timeoutSeconds", 1);

        assertThrows(cafe.snails.ecomagents.exception.BusinessException.class,
                () -> adapter.submit(job("qwen-image-2.0", ModelCapability.TEXT_TO_IMAGE), List.of(), "key"));
    }

    private ImageGenerationJob job(String model, ModelCapability capability) {
        return ImageGenerationJob.builder().id(1L).userId(7L).modelId(2L)
                .mode(capability == ModelCapability.TEXT_TO_IMAGE ? ImageGenerationMode.TEXT_TO_IMAGE : ImageGenerationMode.IMAGE_TO_IMAGE)
                .prompt("a product").targetCount(1).provider("qwen").protocol(ModelProtocol.BAILIAN_IMAGE)
                .remoteModelName(model).apiUrl(baseUrl() + "/compatible-mode/v1")
                .capability(capability).optionsJson("{\"size\":\"2048*2048\",\"watermark\":false}").build();
    }
    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static class JsonNodeAssert {
        static void assertModelAndPrompt(String json, String model, String prompt) throws Exception {
            var root = new ObjectMapper().readTree(json);
            assertEquals(model, root.path("model").asText());
            assertEquals(prompt, root.path("input").path("messages").get(0).path("content").get(0).path("text").asText());
        }
    }
}
