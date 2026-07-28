package cafe.snails.ecomagents.service.image.runtime.provider;

import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.service.image.runtime.ImageSizePolicy;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Component
@Order(100)
/** 阿里云百炼图片生成接口适配器。 */
public class BailianImageAdapter implements ImageGenerationProviderAdapter {
    private final ObjectMapper mapper;
    @Value("${file.upload-dir:./uploads}") private String uploadDir;
    @Value("${image.runtime.bailian-timeout-seconds:600}") private int timeoutSeconds;

    public BailianImageAdapter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public boolean supports(ModelProtocol protocol, ModelCapability capability) {
        return protocol == ModelProtocol.BAILIAN_IMAGE &&
                (capability == ModelCapability.TEXT_TO_IMAGE || capability == ModelCapability.IMAGE_TO_IMAGE);
    }

    @Override public void validate(ImageGenerationJob job, List<ImageGenerationJobInput> inputs) {
        if (job.getTargetCount() > 6) throw new BusinessException(ErrorCode.BAD_REQUEST, "百炼单任务最多生成 6 张图片");
        if (job.getCapability() == ModelCapability.IMAGE_TO_IMAGE && inputs.stream().noneMatch(i -> i.getRole() == ImageJobInputRole.REFERENCE))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "百炼图生图至少需要一张参考图");
        JsonNode configuredOptions = options(job);
        ImageSizePolicy.resolve(job.getRemoteModelName(), textOption(configuredOptions, "size", null), "2048*2048");
    }

    @Override
    public ProviderSubmission submit(ImageGenerationJob job, List<ImageGenerationJobInput> inputs, String secret) {
        ObjectNode request = request(job, inputs);
        boolean synchronous = isSynchronousModel(job.getRemoteModelName());
        JsonNode response = request("POST", nativeBase(job.getApiUrl()) + (synchronous
                        ? "/api/v1/services/aigc/multimodal-generation/generation"
                        : "/api/v1/services/aigc/image-generation/generation"),
                request, secret, !synchronous);
        if (synchronous) return ProviderSubmission.completed(extractImages(response));
        String taskId = response.path("output").path("task_id").asText(null);
        if (taskId == null || taskId.isBlank())
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "百炼未返回异步任务 ID");
        return ProviderSubmission.accepted(taskId);
    }

    @Override
    public ProviderPollResult poll(ImageGenerationJob job, String secret) {
        JsonNode response = request("GET", nativeBase(job.getApiUrl()) + "/api/v1/tasks/"
                + urlEncode(job.getProviderTaskToken()), null, secret, false);
        JsonNode output = response.path("output");
        String status = output.path("task_status").asText("");
        return switch (status) {
            case "SUCCEEDED" -> ProviderPollResult.succeeded(extractImages(response));
            case "FAILED", "CANCELED", "CANCELLED" -> ProviderPollResult.failed("百炼图片任务执行失败");
            default -> ProviderPollResult.pending();
        };
    }

    @Override
    public List<GeneratedProviderImage> generate(ImageGenerationJob job, List<ImageGenerationJobInput> inputs,
            String credentialSecret) {
        ProviderSubmission submission = submit(job, inputs, credentialSecret);
        if (submission.asynchronous()) throw new UnsupportedOperationException("异步百炼模型必须由运行时轮询");
        return submission.images();
    }

    private ObjectNode request(ImageGenerationJob job, List<ImageGenerationJobInput> inputs) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", job.getRemoteModelName());
        ArrayNode content = root.putObject("input").putArray("messages").addObject()
                .put("role", "user").putArray("content");
        for (ImageGenerationJobInput input : inputs) {
            if (input.getRole() == ImageJobInputRole.REFERENCE)
                content.addObject().put("image", dataUri(input));
        }
        content.addObject().put("text", job.getPrompt());
        ObjectNode parameters = root.putObject("parameters");
        JsonNode options = options(job);
        parameters.put("size", ImageSizePolicy.resolve(job.getRemoteModelName(),
                textOption(options, "size", null), "2048*2048"));
        parameters.put("n", job.getTargetCount());
        if (job.getNegativePrompt() != null) parameters.put("negative_prompt", job.getNegativePrompt());
        copyBoolean(options, parameters, "promptExtend", "prompt_extend");
        copyBoolean(options, parameters, "watermark", "watermark");
        if (options.has("seed")) parameters.put("seed", options.get("seed").asLong());
        return root;
    }

    private List<GeneratedProviderImage> extractImages(JsonNode response) {
        List<GeneratedProviderImage> images = new ArrayList<>();
        JsonNode output = response.path("output");
        for (JsonNode choice : iterable(output.path("choices"))) {
            for (JsonNode content : iterable(choice.path("message").path("content")))
                if (content.hasNonNull("image")) images.add(GeneratedProviderImage.remote(content.get("image").asText(), null));
        }
        if (images.isEmpty()) for (JsonNode result : iterable(output.path("results")))
            if (result.hasNonNull("url")) images.add(GeneratedProviderImage.remote(result.get("url").asText(), null));
        if (images.isEmpty()) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "百炼任务未返回图片结果");
        return images;
    }

    private JsonNode request(String method, String target, JsonNode body, String secret, boolean async) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(target).toURL().openConnection(Proxy.NO_PROXY);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + secret);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "EcomAgents-ImageRuntime/1.0");
            if (async) connection.setRequestProperty("X-DashScope-Async", "enable");
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) { mapper.writeValue(out, body); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            JsonNode response;
            if (stream == null) response = mapper.createObjectNode();
            else try (stream) { response = mapper.readTree(stream); }
            if (status < 200 || status >= 300) throw providerError(status);
            return response;
        } catch (BusinessException e) { throw e; }
        catch (SocketTimeoutException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "百炼图片接口请求超时"); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "百炼图片接口请求失败"); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private BusinessException providerError(int status) {
        String message = status == 401 || status == 403 ? "百炼 API Key 无效或无权访问模型"
                : status == 429 ? "百炼图片接口请求过于频繁" : "百炼图片接口请求失败（" + status + "）";
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message);
    }
    private String nativeBase(String configuredUrl) {
        try {
            URI uri = URI.create(configuredUrl);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toString().replaceAll("/+$", "");
        } catch (Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "百炼请求地址格式不正确"); }
    }
    private boolean isSynchronousModel(String model) {
        return model != null && (model.startsWith("qwen-image-2.0") || model.startsWith("qwen-image-max"));
    }
    private String dataUri(ImageGenerationJobInput input) {
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String path = input.getSnapshotPath().replace('\\', '/');
            if (!path.startsWith("/uploads/image-jobs/")) throw new IllegalArgumentException();
            Path file = root.resolve(path.substring("/uploads/".length())).normalize();
            if (!file.startsWith(root.resolve("image-jobs"))) throw new IllegalArgumentException();
            return "data:" + input.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取图生图输入快照"); }
    }
    private JsonNode options(ImageGenerationJob job) {
        try { return job.getOptionsJson() == null || job.getOptionsJson().isBlank() ? mapper.createObjectNode() : mapper.readTree(job.getOptionsJson()); }
        catch (Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "图片任务参数格式不正确"); }
    }
    private String textOption(JsonNode options, String name, String fallback) { return options.hasNonNull(name) ? options.get(name).asText(fallback) : fallback; }
    private void copyBoolean(JsonNode from, ObjectNode to, String source, String target) { if (from.has(source)) to.put(target, from.get(source).asBoolean()); }
    private Iterable<JsonNode> iterable(JsonNode node) { return node != null && node.isArray() ? node : List.of(); }
    private String urlEncode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
