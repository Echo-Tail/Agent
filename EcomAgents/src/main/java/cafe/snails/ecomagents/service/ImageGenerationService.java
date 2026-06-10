package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.model.TokenUsageRecord;
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
 * 图片生成服务 — 调用图片生成 API 实现文生图和图生图。
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
    private final TokenUsageService tokenUsageService;
    private final cafe.snails.ecomagents.repository.UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${image.timeout-seconds:600}")
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
    /**
     * 单次文生图 API 调用结果。
     */
    private record SingleGenerateResult(String savedPath, String revisedPrompt) {}

    public ImageGenerationResult generate(String prompt, String size, String quality, int n, Long userId) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片描述不能为空");
        }
        if (n < 1 || n > 10) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "生成张数必须在 1~10 之间");
        }

        AiModel model = getImageModel();
        String finalSize = (size != null && !size.isBlank()) ? size : DEFAULT_SIZE;
        String finalQuality = (quality != null && !quality.isBlank()) ? quality : DEFAULT_QUALITY;
        long overallStart = System.currentTimeMillis();

        // API 不支持 n>1，并行调用 n 次，每次 n=1
        List<String> resultPaths = new ArrayList<>();
        String revisedPrompt = null;
        int maxParallel = Math.min(n, 4);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(maxParallel);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<SingleGenerateResult>>(n);
            for (int i = 0; i < n; i++) {
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        callGenerateOnce(prompt, finalSize, finalQuality, model), executor));
            }
            // 等待全部完成（任一失败则抛出异常）
            for (var future : futures) {
                SingleGenerateResult r = future.join();
                resultPaths.add(r.savedPath());
                if (revisedPrompt == null) {
                    revisedPrompt = r.revisedPrompt();
                }
            }
        } finally {
            executor.shutdown();
        }

        long overallMs = System.currentTimeMillis() - overallStart;

        // 每张图保存一条历史记录
        List<Long> recordIds = new ArrayList<>();
        for (String path : resultPaths) {
            ImageGenerationRecord record = ImageGenerationRecord.builder()
                    .userId(userId)
                    .mode("GENERATE")
                    .prompt(prompt)
                    .revisedPrompt(revisedPrompt)
                    .size(finalSize)
                    .quality(finalQuality)
                    .resultPath(path)
                    .timeCostMs(overallMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            // 读取图片尺寸
            int[] d = readImageSize(Paths.get(uploadDir, path));
            if (d != null) { record.setWidth(d[0]); record.setHeight(d[1]); }
            recordRepository.save(record);
            recordIds.add(record.getId());
        }

        log.info("Generated {} image(s) for user {} ({}ms)", resultPaths.size(), userId, overallMs);
        recordImageUsage(model, userId, true, null);

        List<String> urls = resultPaths.stream()
                .map(p -> "/" + p.replace("\\", "/").replace("./", ""))
                .toList();
        return new ImageGenerationResult(urls, revisedPrompt, overallMs, recordIds.get(0));
    }

    /**
     * 单次文生图 API 调用：发送 n=1 的请求，保存返回的图片，返回路径 + revised_prompt。
     */
    private SingleGenerateResult callGenerateOnce(String prompt, String size, String quality, AiModel model) {
        long startTime = System.currentTimeMillis();
        try {
            String requestJson = objectMapper.writeValueAsString(
                    new GenerationRequest(MODEL_NAME, prompt, size, quality, 1, "png"));

            java.net.URL url = new java.net.URL(model.getApiUrl() + "/v1/images/generations");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + model.getApiKey());
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutSeconds * 1000);
                conn.setReadTimeout(timeoutSeconds * 1000);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(requestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                long timeCostMs = System.currentTimeMillis() - startTime;

                if (statusCode >= 200 && statusCode < 300) {
                    String responseJson;
                    try (java.io.InputStream in = conn.getInputStream()) {
                        responseJson = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    if (responseJson == null || responseJson.isBlank()) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：API 返回了空响应");
                    }
                    JsonNode response = objectMapper.readTree(responseJson);
                    JsonNode dataArray = response.get("data");
                    if (dataArray == null || !dataArray.isArray() || dataArray.size() == 0) {
                        log.error("Image API unexpected response: {}", responseJson);
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：API 返回格式异常");
                    }
                    JsonNode dataNode = dataArray.get(0);
                    String revisedPrompt = dataNode.has("revised_prompt") ? dataNode.get("revised_prompt").asText() : null;
                    String savedPath = resolveResultPath(dataNode, "generate", model.getApiKey());
                    log.info("callGenerateOnce done in {}ms: {}", timeCostMs, savedPath);
                    return new SingleGenerateResult(savedPath, revisedPrompt);
                } else {
                    String errorBody;
                    try (java.io.InputStream in = conn.getErrorStream()) {
                        errorBody = in != null ? new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) : "";
                    }
                    log.error("callGenerateOnce failed ({}ms): status={}, body={}", timeCostMs, statusCode, errorBody);

                    String message = extractPackyErrorMessage(errorBody);
                    if (message != null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败（" + statusCode + "），请稍后重试");
                }
            } finally {
                conn.disconnect();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            log.error("callGenerateOnce IO error ({}ms): {}", timeCostMs, e.getMessage());
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
    public ImageGenerationResult edit(String prompt, String size, String quality, List<MultipartFile> images, MultipartFile mask, int n, Long userId) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "修改描述不能为空");
        }
        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少上传一张参考图片");
        }
        if (images.size() > 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参考图片最多 4 张");
        }
        if (n < 1 || n > 10) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "生成张数必须在 1~10 之间");
        }

        AiModel model = getImageModel();
        String finalSize = (size != null && !size.isBlank()) ? size : DEFAULT_SIZE;
        String finalQuality = (quality != null && !quality.isBlank()) ? quality : DEFAULT_QUALITY;
        long overallStart = System.currentTimeMillis();

        // API 不支持 n>1，并行调用 n 次，每次 n=1
        List<String> resultPaths = new ArrayList<>();
        String revisedPrompt = null;
        int maxParallel = Math.min(n, 4);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(maxParallel);
        try {
            byte[] multipartBody = buildEditMultipartBody(prompt, images, mask);

            var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<SingleGenerateResult>>(n);
            for (int i = 0; i < n; i++) {
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        callEditOnce(model, multipartBody), executor));
            }
            for (var future : futures) {
                SingleGenerateResult r = future.join();
                resultPaths.add(r.savedPath());
                if (revisedPrompt == null) {
                    revisedPrompt = r.revisedPrompt();
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：" + e.getMessage());
        } finally {
            executor.shutdown();
        }

        long overallMs = System.currentTimeMillis() - overallStart;

        // 每张图保存一条历史记录
        List<Long> recordIds = new ArrayList<>();
        for (String path : resultPaths) {
            ImageGenerationRecord record = ImageGenerationRecord.builder()
                    .userId(userId)
                    .mode("EDIT")
                    .prompt(prompt)
                    .revisedPrompt(revisedPrompt)
                    .size(finalSize)
                    .quality(finalQuality)
                    .resultPath(path)
                    .timeCostMs(overallMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            int[] d = readImageSize(Paths.get(uploadDir, path));
            if (d != null) { record.setWidth(d[0]); record.setHeight(d[1]); }
            recordRepository.save(record);
            recordIds.add(record.getId());
        }

        log.info("Edited {} image(s) for user {} ({}ms)", resultPaths.size(), userId, overallMs);
        recordImageUsage(model, userId, true, null);

        List<String> urls = resultPaths.stream()
                .map(p -> "/" + p.replace("\\", "/").replace("./", ""))
                .toList();
        return new ImageGenerationResult(urls, revisedPrompt, overallMs, recordIds.get(0));
    }

    /**
     * 构建 edit 的 multipart/form-data 请求体（n 固定传 1）。
     */
    private byte[] buildEditMultipartBody(String prompt, List<MultipartFile> images, MultipartFile mask) throws IOException {
        String boundary = "----Boundary" + UUID.randomUUID().toString().replace("-", "");
        java.io.ByteArrayOutputStream byteOut = new java.io.ByteArrayOutputStream();

        appendMultipartField(byteOut, boundary, "model", MODEL_NAME);
        appendMultipartField(byteOut, boundary, "prompt", prompt);
        appendMultipartField(byteOut, boundary, "n", "1");

        for (MultipartFile imageFile : images) {
            String fileName = imageFile.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) fileName = "image.png";
            String mime = "image/png";
            String ext = fileName.toLowerCase();
            if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) mime = "image/jpeg";
            else if (ext.endsWith(".gif")) mime = "image/gif";
            else if (ext.endsWith(".webp")) mime = "image/webp";
            appendMultipartFileField(byteOut, boundary, "image[]", fileName, mime, imageFile.getBytes());
        }

        if (mask != null && !mask.isEmpty()) {
            String maskName = mask.getOriginalFilename();
            if (maskName == null || maskName.isBlank()) maskName = "mask.png";
            appendMultipartFileField(byteOut, boundary, "mask", maskName, "image/png", mask.getBytes());
        }

        byteOut.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return byteOut.toByteArray();
    }

    /**
     * 单次 edit API 调用：发送 multipart 请求，保存返回图片。
     */
    private SingleGenerateResult callEditOnce(AiModel model, byte[] multipartBody) {
        long startTime = System.currentTimeMillis();
        try {
            // 从 multipart body 头部提取 boundary
            String rawHead = new String(multipartBody, 0, Math.min(200, multipartBody.length), java.nio.charset.StandardCharsets.UTF_8);
            String boundary = rawHead.substring(2, rawHead.indexOf("\r\n"));

            java.net.URL url = new java.net.URL(model.getApiUrl() + "/v1/images/edits");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Authorization", "Bearer " + model.getApiKey());
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutSeconds * 1000);
                conn.setReadTimeout(timeoutSeconds * 1000);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(multipartBody);
                }

                int statusCode = conn.getResponseCode();
                long timeCostMs = System.currentTimeMillis() - startTime;

                if (statusCode >= 200 && statusCode < 300) {
                    String responseJson;
                    try (java.io.InputStream in = conn.getInputStream()) {
                        responseJson = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    if (responseJson == null || responseJson.isBlank()) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：API 返回了空响应");
                    }
                    JsonNode response = objectMapper.readTree(responseJson);
                    JsonNode dataArray = response.get("data");
                    if (dataArray == null || !dataArray.isArray() || dataArray.size() == 0) {
                        log.error("Image API edit unexpected response: {}", responseJson);
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：API 返回格式异常");
                    }
                    JsonNode dataNode = dataArray.get(0);
                    String revisedPrompt = dataNode.has("revised_prompt") ? dataNode.get("revised_prompt").asText() : null;
                    String savedPath = resolveResultPath(dataNode, "edit", model.getApiKey());
                    log.info("callEditOnce done in {}ms: {}", timeCostMs, savedPath);
                    return new SingleGenerateResult(savedPath, revisedPrompt);
                } else {
                    String errorBody;
                    try (java.io.InputStream in = conn.getErrorStream()) {
                        errorBody = in != null ? new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) : "";
                    }
                    log.error("callEditOnce failed ({}ms): status={}, body={}", timeCostMs, statusCode, errorBody);
                    String message = extractPackyErrorMessage(errorBody);
                    if (message != null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败（" + statusCode + "），请稍后重试");
                }
            } finally {
                conn.disconnect();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            long timeCostMs = System.currentTimeMillis() - startTime;
            log.error("callEditOnce IO error ({}ms): {}", timeCostMs, e.getMessage());
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
     * 追加 multipart/form-data 文本字段。
     */
    private void appendMultipartField(java.io.ByteArrayOutputStream out, String boundary,
                                       String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write("Content-Type: text/plain; charset=UTF-8\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 追加 multipart/form-data 文件字段。
     */
    private void appendMultipartFileField(java.io.ByteArrayOutputStream out, String boundary,
                                           String fieldName, String fileName, String mimeType,
                                           byte[] data) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write("Content-Transfer-Encoding: binary\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 记录图片生成的 Token 用量（固定 0.2 CNY/张）。
     */
    private void recordImageUsage(AiModel model, Long userId, boolean success, String errorMessage) {
        try {
            String username = null;
            if (userId != null) {
                username = userRepository.findById(userId)
                        .map(cafe.snails.ecomagents.model.User::getUsername)
                        .orElse(null);
            }
            TokenUsageRecord record = TokenUsageRecord.builder()
                    .modelId(model != null ? model.getId() : null)
                    .modelName(model != null ? model.getName() : MODEL_NAME)
                    .modelType("IMAGE")
                    .userId(userId)
                    .agentId(0L)
                    .agentName("图片生成")
                    .username(username)
                    .promptTokens(1)
                    .completionTokens(0)
                    .totalTokens(1)
                    .success(success)
                    .errorMessage(success ? null : truncate(errorMessage, 500))
                    .build();
            tokenUsageService.record(record);
        } catch (Exception e) {
            log.warn("Failed to record image token usage: {}", e.getMessage());
        }
    }

    /**
     * 截断字符串到指定最大长度。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    /**
     * 解析图片 API 响应数据节点，提取 b64_json 并保存到本地。
     * <p>gpt-image 系列模型始终返回 base64 编码的图片。</p>
     */
    private String resolveResultPath(JsonNode dataNode, String subDir, String apiKey) throws IOException {
        JsonNode b64Node = dataNode.get("b64_json");
        if (b64Node == null) {
            log.error("Image API response missing b64_json field");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片生成失败：API 未返回图片数据");
        }
        return saveBase64Image(b64Node.asText(), subDir);
    }

    /**
     * 将 base64 编码的图片数据解码并保存到本地文件。
     */
    private String saveBase64Image(String base64Data, String subDir) throws IOException {
        // 去除可能的 data:image/png;base64, 前缀
        String data = base64Data;
        if (data.contains(",")) {
            data = data.substring(data.indexOf(",") + 1);
        }
        byte[] imageBytes = java.util.Base64.getDecoder().decode(data);

        Path targetDir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        String fileName = UUID.randomUUID() + ".png";
        Path targetPath = targetDir.resolve(fileName);
        Files.write(targetPath, imageBytes);

        log.info("Base64 image saved: {} -> {} ({} bytes)", fileName, targetPath, imageBytes.length);
        return Paths.get(uploadDir, subDir, fileName).toString();
    }

    /**
     * 从 API 返回的 URL 下载图片并保存到本地。
     *
     * @param imageUrl API 返回的临时图片 URL
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
            conn = (java.net.HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
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
     * 判断 API 错误是否需要触发 fallback（模型下架、参数不兼容等）。
     */
    private boolean isModelNotFoundError(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            var root = objectMapper.readTree(body);
            var error = root.path("error");
            if (error.isMissingNode()) return false;
            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            // 模型下架：渠道不可用
            if ("model_not_found".equals(code)) return true;
            // 参数不兼容：当前 API 不支持某些参数（如 response_format）
            if ("unknown_parameter".equals(code)) return true;
            // 其他无法处理的请求错误也 fallback
            if (message.contains("Unknown parameter") || message.contains("unknown parameter")) return true;
            return false;
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
     * <p>当主 API 失败时自动调用此方法重试。</p>
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

            // 构建 OpenAI Chat Completions 请求体（size 嵌入 prompt，不单独传参）
            String sizedPrompt = String.format("生成一张%s的图片：%s", size, prompt);
            String requestBody = String.format(
                    "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"n\":1}",
                    escapeJson(targetModel), escapeJson(sizedPrompt));

            log.info("Fallback ChatCompletions: url={}, model={}", targetUrl, targetModel);

            // 发送 HTTP 请求
            java.net.URL url = new java.net.URL(targetUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + targetKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(Math.min(timeoutSeconds * 1000, 30_000));
                conn.setReadTimeout(timeoutSeconds * 1000);

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
            recordImageUsage(model, userId, true, null);

            return new ImageGenerationResult(
                    List.of("/" + resultPath.replace("\\", "/").replace("./", "")),
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
     * 通过 OpenAI 兼容的 Chat Completions API 进行图生图编辑（fallback）。
     * <p>将参考图片转为 base64 data URI 嵌入请求，调用 Chat Completions 接口。</p>
     */
    private ImageGenerationResult callChatCompletionsEditFallback(String prompt, String size, String quality,
                                                                   List<MultipartFile> images, Long userId,
                                                                   long startTime, AiModel model) {
        try {
            // 解析 fallback 目标
            String targetUrl = (fallbackApiUrl != null && !fallbackApiUrl.isBlank())
                    ? fallbackApiUrl : model.getApiUrl() + "/v1/chat/completions";
            String targetKey = (fallbackApiKey != null && !fallbackApiKey.isBlank())
                    ? fallbackApiKey : model.getApiKey();
            String targetModel = (fallbackModel != null && !fallbackModel.isBlank())
                    ? fallbackModel : model.getModelName();

            // 将参考图片转为 base64 data URI
            StringBuilder imagesJson = new StringBuilder();
            for (int i = 0; i < images.size(); i++) {
                MultipartFile file = images.get(i);
                byte[] bytes = file.getBytes();
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                // 根据文件扩展名推断 MIME
                String mimeType = "image/png";
                String name = file.getOriginalFilename();
                if (name != null) {
                    String ext = name.toLowerCase();
                    if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) mimeType = "image/jpeg";
                    else if (ext.endsWith(".png")) mimeType = "image/png";
                    else if (ext.endsWith(".gif")) mimeType = "image/gif";
                    else if (ext.endsWith(".webp")) mimeType = "image/webp";
                }
                if (i > 0) imagesJson.append(",");
                // 标准 Chat Completions 格式：type=image_url, image_url={url: data:...}
                imagesJson.append(String.format(
                        "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:%s;base64,%s\"}}",
                        mimeType, base64));
            }

            // 构建请求体（文本 + 图片，size 嵌入 prompt 不单独传参）
            String sizedPrompt = String.format("生成一张%s的图片：%s", size, prompt);
            // 标准 Chat Completions 格式：type=text / type=image_url
            String requestBody = String.format(
                    "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"%s\"},%s]}],\"n\":1}",
                    escapeJson(targetModel), escapeJson(sizedPrompt), imagesJson.toString());

            log.info("Fallback ChatCompletions edit: url={}, model={}, images={}", targetUrl, targetModel, images.size());

            // 发送 HTTP 请求
            java.net.URL url = new java.net.URL(targetUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + targetKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(Math.min(timeoutSeconds * 1000, 30_000));
                conn.setReadTimeout(timeoutSeconds * 1000);

                // 写入请求体
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                if (statusCode != 200) {
                    String errorBody = readStream(conn.getErrorStream());
                    log.error("Fallback ChatCompletions edit failed ({}): {}", statusCode, errorBody);
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "图片编辑失败：备用接口返回 " + statusCode);
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
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：备用接口返回格式异常");
                }
                String content = choices.get(0).path("message").path("content").asText("");
                if (content.isBlank()) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：备用接口未返回图片");
                }

                String imageUrl = extractImageUrlFromMarkdown(content);
                if (imageUrl == null) {
                    imageUrl = content.trim();
                }

                long timeCostMs = System.currentTimeMillis() - startTime;
                log.info("Fallback ChatCompletions edit succeeded ({}ms), url={}", timeCostMs, imageUrl);

                String resultPath = downloadImage(imageUrl, "edit", targetKey);

                ImageGenerationRecord record = ImageGenerationRecord.builder()
                        .userId(userId)
                        .mode("EDIT")
                        .prompt(prompt)
                        .revisedPrompt(content)
                        .size(size)
                        .quality(quality)
                        .resultPath(resultPath)
                        .timeCostMs(timeCostMs)
                        .createdAt(LocalDateTime.now())
                        .build();
                recordRepository.save(record);
                recordImageUsage(model, userId, true, null);

                return new ImageGenerationResult(
                        List.of("/" + resultPath.replace("\\", "/").replace("./", "")),
                        content, timeCostMs, record.getId());

            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            log.error("Fallback ChatCompletions edit IO error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片编辑失败：备用接口调用异常");
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
     * 解析 API 错误响应体，提取对用户友好的错误消息。
     *
     * @param body API 返回的 JSON 错误体（可能为 null 或空）
     * @return 友好的中文错误消息，无可识别错误时返回 null
     */
    private int[] readImageSize(java.nio.file.Path path) {
        try (var in = javax.imageio.ImageIO.createImageInputStream(path.toFile())) {
            var readers = javax.imageio.ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to read image size from {}: {}", path, e.getMessage());
        }
        return null;
    }

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
                                     int n, String output_format) {}

    /**
     * 图片生成结果 DTO。
     */
    public record ImageGenerationResult(List<String> urls, String revisedPrompt, Long timeCostMs, Long recordId) {}
}
