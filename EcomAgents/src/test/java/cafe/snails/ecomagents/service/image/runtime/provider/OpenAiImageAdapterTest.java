package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiImageAdapterTest {
    HttpServer server;
    OpenAiImageAdapter adapter;
    @TempDir Path tempDir;

    @BeforeEach void setUp() {
        adapter = new OpenAiImageAdapter(new ObjectMapper());
        ReflectionTestUtils.setField(adapter, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(adapter, "timeoutSeconds", 5);
    }
    @AfterEach void tearDown() { if (server != null) server.stop(0); }

    @Test
    void textToImageShouldUseResolvedModelAndExistingVersionPath() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        start("/compatible-mode/v1/images/generations", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"data\":[{\"b64_json\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=\",\"revised_prompt\":\"done\"}]}");
        });
        ImageGenerationJob job = job(ModelCapability.TEXT_TO_IMAGE, "/compatible-mode/v1");

        var result = adapter.generate(job, List.of(), "secret");

        var request = new ObjectMapper().readTree(body.get());
        assertEquals("configured-image-model", request.path("model").asText());
        assertEquals(2, request.path("n").asInt());
        assertEquals("1536x1024", request.path("size").asText());
        assertEquals("Bearer secret", auth.get());
        assertEquals("image/png", result.get(0).mimeType());
        assertTrue(result.get(0).content().length > 20);
        assertEquals("done", result.get(0).revisedPrompt());
    }

    @Test
    void imageToImageShouldSendImmutableSnapshotsAsMultipart() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start("/v1/images/edits", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            respond(exchange, 200, "{\"data\":[{\"url\":\"https://cdn.example/result.png\"}]}");
        });
        Path input = tempDir.resolve("image-jobs/1/inputs/ref.png");
        Files.createDirectories(input.getParent());
        Files.write(input, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        var snapshot = ImageGenerationJobInput.builder().jobId(1L).inputIndex(0)
                .role(ImageJobInputRole.REFERENCE).sourceType(ImageJobInputSourceType.UPLOAD)
                .snapshotPath("/uploads/image-jobs/1/inputs/ref.png").mimeType("image/png")
                .fileSize(4L).sha256("0".repeat(64)).build();

        var result = adapter.generate(job(ModelCapability.IMAGE_TO_IMAGE, ""), List.of(snapshot), "secret");

        assertTrue(body.get().contains("name=\"model\""));
        assertTrue(body.get().contains("configured-image-model"));
        assertTrue(body.get().contains("name=\"image[]\""));
        assertEquals("https://cdn.example/result.png", result.get(0).remoteUrl());
    }

    @Test
    void providerErrorsShouldNotExposeRawResponse() throws Exception {
        start("/v1/images/generations", exchange -> respond(exchange, 401, "secret provider diagnostic"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.generate(job(ModelCapability.TEXT_TO_IMAGE, ""), List.of(), "bad"));
        assertTrue(error.getMessage().contains("认证失败"));
        assertFalse(error.getMessage().contains("secret provider diagnostic"));
    }

    private ImageGenerationJob job(ModelCapability capability, String basePath) {
        return ImageGenerationJob.builder().id(1L).userId(7L).modelId(2L)
                .mode(capability == ModelCapability.TEXT_TO_IMAGE ? ImageGenerationMode.TEXT_TO_IMAGE : ImageGenerationMode.IMAGE_TO_IMAGE)
                .prompt("a product").targetCount(2).provider("openai").protocol(ModelProtocol.OPENAI_IMAGE)
                .remoteModelName("configured-image-model")
                .apiUrl("http://127.0.0.1:" + server.getAddress().getPort() + basePath)
                .capability(capability).optionsJson("{\"size\":\"1536x1024\",\"quality\":\"high\"}").build();
    }

    private void start(String path, com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        server.start();
    }
    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
