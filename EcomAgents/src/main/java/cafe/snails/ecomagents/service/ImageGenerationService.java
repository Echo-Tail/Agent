package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 图片生成服务 — 通过 PackyAPI 调用 gpt-image-2 模型实现文生图和图生图。
 * <p>非 Agent 工具，独立页面使用。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationService {

    private static final String DEFAULT_SIZE = "1024x1024";
    private static final String DEFAULT_QUALITY = "auto";
    private static final String MODEL_NAME = "gpt-image-2";

    private final AiModelRepository aiModelRepository;
    private final ImageGenerationRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    @Value("${image.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /** Fallback 生图 API 地址（OpenAI 兼容 Chat Completions） */
    @Value("${image.fallback.api-url:}")
    private String fallbackApiUrl;

    /** Fallback API Key */
    @Value("${image.fallback.api-key:}")
    private String fallbackApiKey;

    /** Fallback 模型名称 */
    @Value("${image.fallback.model:gpt-image-2}")
    private String fallbackModel;

    /**
     * 文生图 — 根据描述生成图片。
     *
     * @param prompt  图片描述
     * @param size    图片尺寸，为空则使用默认值
     * @param quality 图片质量，为空则使用默认值
     * @param userId  操作用户 ID
     * @return 生成结果（图片 URL、改写后提示词、耗时）
     */
    public ImageGenerationResult generate(String prompt, String size, String quality, Long userId) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片描述不能为空");
        }

        AiModel model = getImageModel();
        WebClient client = buildWebClient(model);

        String finalSize = (size != null && !size.isBlank()) ? size : DEFAULT_SIZE;
        String finalQuality = (quality != null && !quality.isBlank()) ? quality : DEFAULT_QUALITY;

        long startTime = System.currentTimeMillis();
        try {
            // 构建请求体（n=1, output_format=png, response_format=url）
            String requestJson = objectMapper.writeValueAsString(
                    new GenerationRequest(MODEL_NAME, prompt, finalSize, finalQuality, 1, "png", "url"));

            log.debug("PackyAPI generate request: {}", requestJson);

            String responseJson = client.post()
                    .uri(model.getApiUrl() + "/v1/images/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            long timeCostMs = System.currentTimeMillis() - startTime;

            if (responseJson == null || responseJson.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：API 返回了空响应");
            }

            // 解析响应
            JsonNode response = objectMapper.readTree(responseJson);
            JsonNode dataArray = response.get("data");
            if (dataArray == null || !dataArray.isArray() || dataArray.size() == 0) {
                log.error("PackyAPI unexpected response: {}", responseJson);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：API 返回格式异常");
            }
            JsonNode dataNode = dataArray.get(0);
            JsonNode urlNode = dataNode.get("url");
            if (urlNode == null) {
                log.error("PackyAPI response missing url field: {}", responseJson);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：API 未返回图片地址");
            }
            String imageUrl = urlNode.asText();
            String revisedPrompt = dataNode.has("revised_prompt") ? dataNode.get("revised_prompt").asText() : null;

            // 使用 HttpURLConnection + Chrome UA 下载（已修复 CloudFront SSL 重新协商问题）
            String resultPath = downloadImage(imageUrl, "generate", model.getApiKey());

            // 保存历史记录
            ImageGenerationRecord record = ImageGenerationRecord.builder()
                    .userId(userId)
                    .mode("GENERATE")
                    .prompt(prompt)
                    .revisedPrompt(revisedPrompt)
                    .size(finalSize)
                    .quality(finalQuality)
                    .resultPath(resultPath)
                    .timeCostMs(timeCostMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            recordRepository.save(record);

            log.info("Image generated for user {}: {} ({}ms)", userId, resultPath, timeCostMs);

            return new ImageGenerationResult("/" + resultPath.replace("\\", "/").replace("./", ""), revisedPrompt, timeCostMs, record.getId());

        } catch (WebClientResponseException e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            String body = e.getResponseBodyAsString();
            log.error("PackyAPI generate failed ({}ms): status={}, body={}", timeCostMs, e.getStatusCode(), body);
            // 检测模型下架：走 fallback 兼容 API 重试（优先使用显式配置，否则用同模型 API）
            if (isModelNotFoundError(body) && isFallbackConfigured(model)) {
                log.info("PackyAPI model unavailable, falling back to Chat Completions API");
                return callChatCompletionsFallback(prompt, finalSize, finalQuality, userId, startTime, model);
            }
            // 其他可识别错误，给出明确提示
            String message = extractPackyErrorMessage(body);
            if (message != null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败（" + e.getStatusCode() + "），请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (SocketException e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            log.error("PackyAPI connection reset ({}ms): {}", timeCostMs, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：服务连接被重置，请稍后重试");
        } catch (Exception e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            log.error("Image generation failed ({}ms): {}", timeCostMs, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：" + e.getMessage());
        }
    }

    /**
     * 图生图 — 上传参考图进行编辑。
     *
     * @param prompt  修改描述
     * @param size    图片尺寸
     * @param quality 图片质量
     * @param images  参考图片（最多 4 张）
     * @param userId  操作用户 ID
     * @return 生成结果
     */
    public ImageGenerationResult edit(String prompt, String size, String quality, List<MultipartFile> images, Long userId) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "修改描述不能为空");
        }
        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少上传一张参考图片");
        }
        if (images.size() > 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参考图片最多 4 张");
        }

        AiModel model = getImageModel();
        WebClient client = buildWebClient(model);

        String finalSize = (size != null && !size.isBlank()) ? size : DEFAULT_SIZE;
        String finalQuality = (quality != null && !quality.isBlank()) ? quality : DEFAULT_QUALITY;

        long startTime = System.currentTimeMillis();
        try {
            // 构建 multipart 请求体
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("model", MODEL_NAME);
            bodyBuilder.part("prompt", prompt);
            bodyBuilder.part("size", finalSize);
            bodyBuilder.part("quality", finalQuality);
            bodyBuilder.part("output_format", "png");
            bodyBuilder.part("response_format", "url");
            bodyBuilder.part("n", 1);

            // 添加图片，使用多个同名 "image" 字段
            for (MultipartFile file : images) {
                String name = file.getOriginalFilename();
                if (name == null || name.isBlank()) {
                    name = "image.png";
                }
                final String finalName = name;
                bodyBuilder.part("image", new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return finalName;
                    }
                }, MediaType.IMAGE_PNG);
            }

            String responseJson = client.post()
                    .uri(model.getApiUrl() + "/v1/images/edits")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            long timeCostMs = System.currentTimeMillis() - startTime;

            if (responseJson == null || responseJson.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：API 返回了空响应");
            }

            // 解析响应
            JsonNode response = objectMapper.readTree(responseJson);
            JsonNode dataArray = response.get("data");
            if (dataArray == null || !dataArray.isArray() || dataArray.size() == 0) {
                log.error("PackyAPI edit unexpected response: {}", responseJson);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：API 返回格式异常");
            }
            JsonNode dataNode = dataArray.get(0);
            JsonNode urlNode = dataNode.get("url");
            if (urlNode == null) {
                log.error("PackyAPI edit response missing url: {}", responseJson);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：API 未返回图片地址");
            }
            String imageUrl = urlNode.asText();
            String revisedPrompt = dataNode.has("revised_prompt") ? dataNode.get("revised_prompt").asText() : null;

            // 下载图片到本地
            String resultPath = downloadImage(imageUrl, "edit", model.getApiKey());

            // 保存历史记录
            ImageGenerationRecord record = ImageGenerationRecord.builder()
                    .userId(userId)
                    .mode("EDIT")
                    .prompt(prompt)
                    .revisedPrompt(revisedPrompt)
                    .size(finalSize)
                    .quality(finalQuality)
                    .resultPath(resultPath)
                    .timeCostMs(timeCostMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            recordRepository.save(record);

            log.info("Image edited for user {}: {} ({}ms)", userId, resultPath, timeCostMs);

            return new ImageGenerationResult("/" + resultPath.replace("\\", "/").replace("./", ""), revisedPrompt, timeCostMs, record.getId());

        } catch (WebClientResponseException e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            String body = e.getResponseBodyAsString();
            log.error("PackyAPI edit failed ({}ms): status={}, body={}", timeCostMs, e.getStatusCode(), body);
            // 检测模型下架等特定错误，给出明确提示
            String message = extractPackyErrorMessage(body);
            if (message != null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败（" + e.getStatusCode() + "），请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (SocketException e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            log.error("PackyAPI edit connection reset ({}ms): {}", timeCostMs, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：服务连接被重置，请稍后重试");
        } catch (Exception e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            log.error("Image editing failed ({}ms): {}", timeCostMs, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：" + e.getMessage());
        }
    }

    /**
     * 分页查询当前用户的历史记录，支持日期范围和 prompt 模糊匹配。
     */
    public Page<ImageGenerationRecord> listRecords(Long userId, LocalDate startDate, LocalDate endDate,
                                                    String prompt, Pageable pageable) {
        Specification<ImageGenerationRecord> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("userId"), userId));

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            }
            if (prompt != null && !prompt.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("prompt")), "%" + prompt.toLowerCase() + "%"));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ImageGenerationRecord> result = recordRepository.findAll(spec, pageable);
        return result;
    }

    /**
     * 删除一条历史记录（仅限记录拥有者）。
     */
    public void deleteRecord(Long recordId, Long userId) {
        ImageGenerationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除此记录");
        }
        recordRepository.delete(record);
    }

    /**
     * 获取已启用的图片生成模型，如果没有则抛异常。
     */
    private AiModel getImageModel() {
        List<AiModel> models = aiModelRepository.findByModelTypeAndEnabled("IMAGE", true);
        if (models.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员未配置图片生成模型，请联系管理员设置");
        }
        return models.get(0);
    }

    /**
     * 构建带超时和认证头的 WebClient。
     */
    private WebClient buildWebClient(AiModel model) {
        return WebClient.builder()
                .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                .build();
    }

    /**
     * 将 Base64 解码后的图片字节保存到本地文件。
     *
     * @param imageBytes 图片字节数据
     * @param subDir     子目录（generate / edit）
     * @return 保存后的本地相对路径（如 uploads/generate/uuid.png）
     */
    private String saveImageBytes(byte[] imageBytes, String subDir) throws IOException {
        Path targetDir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        String fileName = UUID.randomUUID() + ".png";
        Path targetPath = targetDir.resolve(fileName);

        Files.write(targetPath, imageBytes);
        log.info("Image saved: {} -> {} ({} bytes)", fileName, targetPath, imageBytes.length);

        return Paths.get(uploadDir, subDir, fileName).toString();
    }

    /**
     * 从 PackyAPI 返回的 URL 下载图片并保存到本地。
     *
     * @param imageUrl PackyAPI 返回的临时图片 URL
     * @param subDir   子目录（generate / edit）
     * @return 保存后的本地相对路径（如 uploads/generate/uuid_filename.png）
     */
    private String downloadImage(String imageUrl, String subDir, String apiKey) throws IOException {
        Path targetDir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        String fileName = UUID.randomUUID() + ".png";
        Path targetPath = targetDir.resolve(fileName);

        // Curl 测试证实 URL 有效，但 Spring WebClient 在 CloudFront SSL 重新协商时返回空体。
        // 改用 java.net.HttpURLConnection 直接下载。
        byte[] imageBytes;
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(imageUrl);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                throw new IOException("图片下载失败（HTTP " + statusCode + "）");
            }

            try (java.io.InputStream in = conn.getInputStream()) {
                imageBytes = in.readAllBytes();
            }
        } catch (IOException e) {
            log.error("Failed to download image from {}: {}", imageUrl, e.getMessage());
            throw new IOException("图片下载失败：" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        if (imageBytes == null || imageBytes.length == 0) {
            throw new IOException("下载的图片内容为空");
        }

        Files.write(targetPath, imageBytes);
        log.info("Image downloaded: {} -> {} ({} bytes)", imageUrl, targetPath, imageBytes.length);

        return Paths.get(uploadDir, subDir, fileName).toString();
    }

    /**
     * 判断 PackyAPI 错误是否为模型下架（model_not_found）。
     */
    private boolean isModelNotFoundError(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            var root = objectMapper.readTree(body);
            var error = root.path("error");
            if (error.isMissingNode()) return false;
            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            return "model_not_found".equals(code) && message.contains("无可用渠道");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fallback API 是否已配置（api-url 和 api-key 均非空）。
     */
    private boolean isFallbackConfigured(AiModel model) {
        // 有显式 fallback 配置，或可复用失败请求的模型信息
        if (fallbackApiUrl != null && !fallbackApiUrl.isBlank()
                && fallbackApiKey != null && !fallbackApiKey.isBlank()) {
            return true;
        }
        return model != null && model.getApiUrl() != null && !model.getApiUrl().isBlank()
                && model.getApiKey() != null && !model.getApiKey().isBlank();
    }

    /**
     * 通过 OpenAI 兼容的 Chat Completions API 生图（fallback）。
     * <p>当 PackyAPI 模型下架时自动调用此方法重试。</p>
     */
    private ImageGenerationResult callChatCompletionsFallback(String prompt, String size, String quality,
                                                               Long userId, long startTime, AiModel model) {
        try {
            // 解析 fallback 目标：优先显式配置，否则复用失败的模型信息
            String targetUrl = (fallbackApiUrl != null && !fallbackApiUrl.isBlank())
                    ? fallbackApiUrl : model.getApiUrl() + "/v1/chat/completions";
            String targetKey = (fallbackApiKey != null && !fallbackApiKey.isBlank())
                    ? fallbackApiKey : model.getApiKey();
            String targetModel = (fallbackModel != null && !fallbackModel.isBlank())
                    ? fallbackModel : model.getModelName();

            // 构建 OpenAI Chat Completions 请求体
            String requestBody = String.format(
                    "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"n\":1,\"size\":\"%s\"}",
                    escapeJson(targetModel), escapeJson(prompt), escapeJson(size));

            log.info("Fallback ChatCompletions: url={}, model={}", targetUrl, targetModel);

            // 发送 HTTP 请求
            java.net.URL url = new java.net.URL(targetUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + targetKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);

            // 写入请求体
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                String errorBody = readStream(conn.getErrorStream());
                log.error("Fallback ChatCompletions failed ({}): {}", statusCode, errorBody);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "图片生成失败：备用接口返回 " + statusCode);
            }

            // 读取响应
            String responseJson;
            try (java.io.InputStream in = conn.getInputStream()) {
                responseJson = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            // 解析 OpenAI 响应，提取图片 URL
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：备用接口返回格式异常");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：备用接口未返回图片");
            }

            // 从 Markdown 图片语法中提取 URL：![alt](url)
            String imageUrl = extractImageUrlFromMarkdown(content);
            if (imageUrl == null) {
                // 如果无法从 Markdown 提取，直接将 content 作为 URL 尝试
                imageUrl = content.trim();
            }

            long timeCostMs = System.currentTimeMillis() - startTime;
            log.info("Fallback ChatCompletions succeeded ({}ms), url={}", timeCostMs, imageUrl);

            // 下载图片到本地
            String resultPath = downloadImage(imageUrl, "generate", targetKey);

            // 保存历史记录
            ImageGenerationRecord record = ImageGenerationRecord.builder()
                    .userId(userId)
                    .mode("GENERATE")
                    .prompt(prompt)
                    .revisedPrompt(content)
                    .size(size)
                    .quality(quality)
                    .resultPath(resultPath)
                    .timeCostMs(timeCostMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            recordRepository.save(record);

            return new ImageGenerationResult(
                    "/" + resultPath.replace("\\", "/").replace("./", ""),
                    content, timeCostMs, record.getId());

            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            log.error("Fallback ChatCompletions IO error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：备用接口调用异常");
        }
    }

    /**
     * 从 Markdown 格式中提取图片 URL：![alt](url)
     */
    private String extractImageUrlFromMarkdown(String markdown) {
        if (markdown == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "!\\[.*?\\]\\((https?://[^)]+)\\)").matcher(markdown);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 读取错误流内容。
     */
    private String readStream(java.io.InputStream stream) throws IOException {
        if (stream == null) return "";
        return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * JSON 字符串转义（防止 prompt 中的引号破坏 JSON）。
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 解析 PackyAPI 错误响应体，提取对用户友好的错误消息。
     *
     * @param body PackyAPI 返回的 JSON 错误体（可能为 null 或空）
     * @return 友好的中文错误消息，无可识别错误时返回 null
     */
    private String extractPackyErrorMessage(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            var root = objectMapper.readTree(body);
            var error = root.path("error");
            if (error.isMissingNode()) return null;
            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            // 模型下架：渠道不可用
            if ("model_not_found".equals(code) && message.contains("无可用渠道")) {
                return "图片生成功能暂不可用：底层模型渠道已下架，请联系管理员";
            }
            // 余额不足
            if ("insufficient_quota".equals(code) || message.contains("余额不足")) {
                return "图片生成功能暂不可用：API 余额不足";
            }
            // 超时
            if (message.contains("timeout") || message.contains("超时")) {
                return "图片生成超时，请稍后重试";
            }
        } catch (Exception ignored) {
            // 解析失败则使用默认错误消息
        }
        return null;
    }

    /**
     * 文生图请求体（内部类）。
     */
    private record GenerationRequest(String model, String prompt, String size, String quality,
                                     int n, String output_format, String response_format) {}

    /**
     * 图片生成结果 DTO。
     */
    public record ImageGenerationResult(String url, String revisedPrompt, Long timeCostMs, Long recordId) {}
}
