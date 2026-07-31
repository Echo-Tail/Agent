package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.service.ProxySettingsService;
import com.fasterxml.jackson.databind.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Order(100)
@Slf4j
/** OpenAI 兼容图片生成接口适配器。 */
public class OpenAiImageAdapter implements ImageGenerationProviderAdapter {
    private final ObjectMapper mapper;
    private final ProxySettingsService proxySettingsService;
    @Value("${file.upload-dir:./uploads}") private String uploadDir;
    @Value("${image.runtime.openai-connect-timeout-seconds:15}") private int connectTimeoutSeconds;
    @Value("${image.runtime.openai-read-timeout-seconds:${image.runtime.openai-timeout-seconds:600}}")
    private int readTimeoutSeconds;

    public OpenAiImageAdapter(ObjectMapper mapper, ProxySettingsService proxySettingsService) {
        this.mapper = mapper;
        this.proxySettingsService = proxySettingsService;
    }

    @Override public boolean supports(ModelProtocol protocol, ModelCapability capability) {
        return protocol == ModelProtocol.OPENAI_IMAGE &&
                (capability == ModelCapability.TEXT_TO_IMAGE || capability == ModelCapability.IMAGE_TO_IMAGE);
    }

    @Override
    public List<GeneratedProviderImage> generate(ImageGenerationJob job, List<ImageGenerationJobInput> inputs,
            String credentialSecret) {
        List<GeneratedProviderImage> generated = new ArrayList<>();
        BusinessException lastFailure = null;
        for (int requestIndex = 0; requestIndex < job.getTargetCount(); requestIndex++) {
            try {
                List<GeneratedProviderImage> response = job.getCapability() == ModelCapability.TEXT_TO_IMAGE
                        ? textToImage(job, credentialSecret) : imageToImage(job, inputs, credentialSecret);
                generated.add(response.get(0));
                log.info("OpenAI image request completed: jobId={}, request={}/{}, collectedCount={}",
                        job.getId(), requestIndex + 1, job.getTargetCount(), generated.size());
            } catch (BusinessException error) {
                lastFailure = error;
                log.warn("OpenAI image request failed: jobId={}, request={}/{}, errorCode={}",
                        job.getId(), requestIndex + 1, job.getTargetCount(), error.getErrorCode());
            }
        }
        if (generated.isEmpty()) {
            throw lastFailure != null ? lastFailure
                    : new BusinessException(ErrorCode.INTERNAL_ERROR, "图片供应商未返回图片数据");
        }
        return generated;
    }

    private List<GeneratedProviderImage> textToImage(ImageGenerationJob job, String secret) {
        try {
            JsonNode options = options(job);
            var body = mapper.createObjectNode();
            body.put("model", job.getRemoteModelName());
            body.put("prompt", job.getPrompt());
            body.put("n", 1);
            body.put("size", option(options, "size", "1024x1024"));
            body.put("quality", option(options, "quality", "auto"));
            body.put("output_format", option(options, "outputFormat", "png"));
            return callJson(job, endpoint(job.getApiUrl(), "generations"), mapper.writeValueAsBytes(body), secret);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) {
            log.warn("Failed to build OpenAI text-to-image request: jobId={}, errorType={}, message={}",
                    job.getId(), e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构建图片生成请求失败");
        }
    }

    private List<GeneratedProviderImage> imageToImage(ImageGenerationJob job,
            List<ImageGenerationJobInput> inputs, String secret) {
        String boundary = "----EcomAgents" + UUID.randomUUID().toString().replace("-", "");
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            field(body, boundary, "model", job.getRemoteModelName());
            field(body, boundary, "prompt", job.getPrompt());
            field(body, boundary, "n", "1");
            JsonNode options = options(job);
            field(body, boundary, "size", option(options, "size", "1024x1024"));
            field(body, boundary, "quality", option(options, "quality", "auto"));
            field(body, boundary, "output_format", option(options, "outputFormat", "png"));
            for (ImageGenerationJobInput input : inputs) {
                String fieldName = input.getRole() == ImageJobInputRole.MASK ? "mask" : "image[]";
                file(body, boundary, fieldName, input);
            }
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return call(job, endpoint(job.getApiUrl(), "edits"), body.toByteArray(), secret,
                    "multipart/form-data; boundary=" + boundary);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) {
            log.warn("Failed to build OpenAI image-to-image request: jobId={}, errorType={}, message={}",
                    job.getId(), e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构建图片编辑请求失败");
        }
    }

    private List<GeneratedProviderImage> callJson(ImageGenerationJob job, String url, byte[] body, String secret) {
        return call(job, url, body, secret, "application/json");
    }

    private List<GeneratedProviderImage> call(ImageGenerationJob job, String target, byte[] body, String secret,
            String contentType) {
        long startedAt = System.nanoTime();
        AtomicReference<RequestPhase> phase = new AtomicReference<>(RequestPhase.CONNECTING);
        AtomicLong responseBytesRead = new AtomicLong();
        try {
            HttpClient client = proxySettingsService.createHttpClient(Duration.ofSeconds(connectTimeoutSeconds));
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(readTimeoutSeconds))
                    .header("Content-Type", contentType)
                    .header("Authorization", "Bearer " + secret)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            phase.set(RequestPhase.WAITING_RESPONSE_HEADERS);
            log.info("OpenAI image request dispatched: jobId={}, capability={}, remoteModel={}, requestBytes={}, durationMs={}",
                    job.getId(), job.getCapability(), job.getRemoteModelName(), body.length, elapsedMs(startedAt));
            HttpResponse.BodyHandler<byte[]> responseHandler = responseInfo -> {
                phase.set(RequestPhase.READING_RESPONSE_BODY);
                String headerContentType = responseInfo.headers().firstValue("Content-Type").orElse(null);
                log.info("OpenAI image response headers received: jobId={}, capability={}, remoteModel={}, httpStatus={}, contentType={}, durationMs={}",
                        job.getId(), job.getCapability(), job.getRemoteModelName(), responseInfo.statusCode(),
                        headerContentType, elapsedMs(startedAt));
                return new TrackingBodySubscriber(
                        HttpResponse.BodySubscribers.ofByteArray(), responseBytesRead);
            };
            HttpResponse<byte[]> response = client.sendAsync(request, responseHandler)
                    .orTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                    .join();
            int status = response.statusCode();
            String responseContentType = response.headers().firstValue("Content-Type").orElse(null);
            byte[] responseBytes = response.body();
            log.info("OpenAI image provider responded: jobId={}, capability={}, remoteModel={}, httpStatus={}, contentType={}, responseBytes={}, durationMs={}",
                    job.getId(), job.getCapability(), job.getRemoteModelName(), status, responseContentType,
                    responseBytes.length, elapsedMs(startedAt));
            if (status < 200 || status >= 300) {
                throw providerError(status, new String(responseBytes, StandardCharsets.UTF_8));
            }
            if (isDirectImage(responseContentType, responseBytes)) {
                return List.of(GeneratedProviderImage.inline(
                        responseBytes, detectMime(responseBytes), null));
            }
            if (isHtml(responseContentType, responseBytes)) {
                throw htmlResponseError(responseBytes);
            }
            JsonNode data = mapper.readTree(responseBytes).path("data");
            if (!data.isArray() || data.isEmpty())
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片供应商返回格式异常");
            List<GeneratedProviderImage> images = new ArrayList<>();
            for (JsonNode item : data) {
                String revised = item.path("revised_prompt").isMissingNode() ? null : item.path("revised_prompt").asText(null);
                if (item.hasNonNull("b64_json")) {
                    byte[] content = Base64.getDecoder().decode(item.get("b64_json").asText());
                    images.add(GeneratedProviderImage.inline(content, detectMime(content), revised));
                }
                else if (item.hasNonNull("url")) images.add(GeneratedProviderImage.remote(item.get("url").asText(), revised));
                else throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片供应商未返回图片数据");
            }
            return images;
        } catch (BusinessException e) { throw e; }
        catch (HttpTimeoutException e) {
            throw timeout(job, phase.get(), responseBytesRead.get(), startedAt);
        }
        catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException || e.getCause() instanceof HttpTimeoutException) {
                throw timeout(job, phase.get(), responseBytesRead.get(), startedAt);
            }
            throw e;
        }
        catch (Exception e) {
            log.warn("OpenAI image provider request failed: jobId={}, capability={}, remoteModel={}, errorType={}, durationMs={}",
                    job.getId(), job.getCapability(), job.getRemoteModelName(), e.getClass().getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片供应商请求失败");
        }
    }

    private BusinessException timeout(ImageGenerationJob job, RequestPhase phase, long responseBytesRead,
            long startedAt) {
        log.warn("OpenAI image provider timed out: jobId={}, capability={}, remoteModel={}, phase={}, responseBytesRead={}, connectTimeoutSeconds={}, readTimeoutSeconds={}, durationMs={}",
                job.getId(), job.getCapability(), job.getRemoteModelName(), phase.logValue, responseBytesRead,
                connectTimeoutSeconds, readTimeoutSeconds, elapsedMs(startedAt));
        return new BusinessException(ErrorCode.INTERNAL_ERROR,
                "图片供应商请求超时（" + phase.message + "）");
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private enum RequestPhase {
        CONNECTING("CONNECTING", "建立连接"),
        WAITING_RESPONSE_HEADERS("WAITING_RESPONSE_HEADERS", "等待响应头"),
        READING_RESPONSE_BODY("READING_RESPONSE_BODY", "读取响应数据");

        private final String logValue;
        private final String message;

        RequestPhase(String logValue, String message) {
            this.logValue = logValue;
            this.message = message;
        }
    }

    private static final class TrackingBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate;
        private final AtomicLong receivedBytes;

        private TrackingBodySubscriber(HttpResponse.BodySubscriber<byte[]> delegate, AtomicLong receivedBytes) {
            this.delegate = delegate;
            this.receivedBytes = receivedBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            buffers.forEach(buffer -> receivedBytes.addAndGet(buffer.remaining()));
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }

    private BusinessException providerError(int status, String response) {
        String message = status == 401 || status == 403 ? "图片供应商认证失败"
                : status == 429 ? "图片供应商请求过于频繁" : "图片供应商请求失败（" + status + "）";
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message);
    }

    private String detectMime(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片供应商返回了无效图片数据");
    }

    private boolean isDirectImage(String contentType, byte[] bytes) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        return bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47
                || bytes.length >= 3 && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff
                || bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private boolean isHtml(String contentType, byte[] bytes) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            return true;
        }
        String prefix = new String(bytes, 0, Math.min(bytes.length, 64), StandardCharsets.UTF_8)
                .stripLeading().toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html");
    }

    private BusinessException htmlResponseError(byte[] responseBytes) {
        String html = new String(responseBytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (html.contains("only available in certain regions")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "图片供应商在当前服务器出口地区不可用，请切换网络出口或更换供应商");
        }
        return new BusinessException(ErrorCode.INTERNAL_ERROR,
                "图片模型 API 地址或供应商状态异常：接口返回了 HTML 页面");
    }

    private String endpoint(String apiUrl, String operation) {
        String base = apiUrl == null ? "" : apiUrl.replaceAll("/+$", "");
        String operationPath = "/images/" + operation;
        if (base.endsWith(operationPath)) return base;
        if (base.matches(".*/v\\d+/images/(generations|edits)$"))
            return base.replaceFirst("/(generations|edits)$", "/" + operation);
        if (base.matches(".*/v\\d+/images$"))
            return base + "/" + operation;
        if (base.matches(".*/v\\d+$") || base.endsWith("/compatible-mode/v1"))
            return base + "/images/" + operation;
        return base + "/v1/images/" + operation;
    }

    private JsonNode options(ImageGenerationJob job) throws IOException {
        return job.getOptionsJson() == null || job.getOptionsJson().isBlank()
                ? mapper.createObjectNode() : mapper.readTree(job.getOptionsJson());
    }
    private String option(JsonNode options, String name, String fallback) {
        return options.hasNonNull(name) && !options.get(name).asText().isBlank() ? options.get(name).asText() : fallback;
    }
    private void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    private void file(ByteArrayOutputStream out, String boundary, String name, ImageGenerationJobInput input) throws IOException {
        Path path = resolveSnapshot(input.getSnapshotPath());
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"input-" + input.getInputIndex() + extension(input.getMimeType()) + "\"\r\nContent-Type: "
                + input.getMimeType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        Files.copy(path, out);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    private Path resolveSnapshot(String publicPath) {
        String normalized = publicPath == null ? "" : publicPath.replace('\\', '/');
        if (!normalized.startsWith("/uploads/image-jobs/")) throw new BusinessException(ErrorCode.BAD_REQUEST, "非法任务输入路径");
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path path = root.resolve(normalized.substring("/uploads/".length())).normalize();
        if (!path.startsWith(root.resolve("image-jobs"))) throw new BusinessException(ErrorCode.BAD_REQUEST, "非法任务输入路径");
        return path;
    }
    private String extension(String mime) { return "image/jpeg".equals(mime) ? ".jpg" : "image/webp".equals(mime) ? ".webp" : ".png"; }
}
