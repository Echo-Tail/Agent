package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import com.fasterxml.jackson.databind.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Component
@Order(100)
@Slf4j
public class OpenAiImageAdapter implements ImageGenerationProviderAdapter {
    private final ObjectMapper mapper;
    @Value("${file.upload-dir:./uploads}") private String uploadDir;
    @Value("${image.runtime.openai-timeout-seconds:300}") private int timeoutSeconds;

    public OpenAiImageAdapter(ObjectMapper mapper) { this.mapper = mapper; }

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
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构建图片生成请求失败"); }
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
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构建图片编辑请求失败"); }
    }

    private List<GeneratedProviderImage> callJson(ImageGenerationJob job, String url, byte[] body, String secret) {
        return call(job, url, body, secret, "application/json");
    }

    private List<GeneratedProviderImage> call(ImageGenerationJob job, String target, byte[] body, String secret,
            String contentType) {
        HttpURLConnection connection = null;
        long startedAt = System.nanoTime();
        try {
            connection = (HttpURLConnection) URI.create(target).toURL().openConnection(Proxy.NO_PROXY);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Authorization", "Bearer " + secret);
            connection.setRequestProperty("User-Agent", "EcomAgents-ImageRuntime/1.0");
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            log.info("OpenAI image provider responded: jobId={}, capability={}, remoteModel={}, httpStatus={}, durationMs={}",
                    job.getId(), job.getCapability(), job.getRemoteModelName(), status,
                    (System.nanoTime() - startedAt) / 1_000_000);
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response;
            if (stream == null) response = "";
            else try (stream) { response = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
            if (status < 200 || status >= 300) throw providerError(status, response);
            JsonNode data = mapper.readTree(response).path("data");
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
        catch (SocketTimeoutException e) {
            log.warn("OpenAI image provider timed out: jobId={}, capability={}, remoteModel={}, durationMs={}",
                    job.getId(), job.getCapability(), job.getRemoteModelName(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片供应商请求超时");
        }
        catch (Exception e) {
            log.warn("OpenAI image provider request failed: jobId={}, capability={}, remoteModel={}, errorType={}, durationMs={}",
                    job.getId(), job.getCapability(), job.getRemoteModelName(), e.getClass().getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片供应商请求失败");
        }
        finally { if (connection != null) connection.disconnect(); }
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

    private String endpoint(String apiUrl, String operation) {
        String base = apiUrl == null ? "" : apiUrl.replaceAll("/+$", "");
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
