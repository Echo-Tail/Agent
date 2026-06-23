package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.BrightDataScrapeRequest;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.model.ProductProfileImage;
import cafe.snails.ecomagents.model.ProductProfileVersion;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ProductProfileImageRepository;
import cafe.snails.ecomagents.repository.ProductProfileRepository;
import cafe.snails.ecomagents.repository.ProductProfileVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductProfileService {

    private static final String STATUS_PENDING_PARSE = "PENDING_PARSE";
    private static final String STATUS_PENDING_CONFIRM = "PENDING_CONFIRM";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_PARSE_FAILED = "PARSE_FAILED";
    private static final String DEFAULT_CATEGORY = "car stereo";

    private final ProductProfileRepository profileRepository;
    private final ProductProfileVersionRepository versionRepository;
    private final ProductProfileImageRepository imageRepository;
    private final AiModelRepository aiModelRepository;
    private final BrightDataService brightDataService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Transactional
    public ProductProfile create(String productName, String markdownContent, Long userId) {
        return createFromMarkdown(productName, markdownContent, userId);
    }

    @Transactional
    public ProductProfile createFromMarkdown(String productName, String markdownContent, Long userId) {
        if (isBlank(productName) && !isBlank(markdownContent)) {
            productName = extractTitleFromMarkdown(markdownContent);
        }
        if (isBlank(productName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "产品名称不能为空");
        }
        if (profileRepository.existsByProductName(productName.trim())) {
            throw new BusinessException(ErrorCode.CONFLICT, "产品名称 '" + productName.trim() + "' 已存在");
        }

        ProductProfile profile = ProductProfile.builder()
                .userId(userId)
                .productName(productName.trim())
                .category(DEFAULT_CATEGORY)
                .markdownContent(markdownContent)
                .status(STATUS_PENDING_PARSE)
                .build();
        profile = profileRepository.save(profile);

        String factsJson = parseWithLlm("markdown", markdownContent);
        if (factsJson != null) {
            profile.setProductFactsJson(factsJson);
            profile.setStatus(STATUS_PENDING_CONFIRM);
            checkSkuModelDuplicate(profile);
        } else {
            profile.setStatus(STATUS_PARSE_FAILED);
            profile.setParseError("LLM 解析失败，请检查 Markdown 格式后重试");
        }
        return profileRepository.save(profile);
    }

    @Transactional
    public ProductProfile createFromMarkdownFile(MultipartFile file, Long userId) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String fileName = file.getOriginalFilename();
            String productName = fileName != null ? fileName.replaceAll("(?i)\\.(md|markdown|txt)$", "").trim() : null;
            return createFromMarkdown(productName, content, userId);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件读取失败: " + e.getMessage());
        }
    }

    @Transactional
    public ProductProfile createFromAsin(String asin, Long userId) {
        if (isBlank(asin)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ASIN 不能为空");
        }
        asin = asin.trim().toUpperCase();
        if (profileRepository.existsByProductName("ASIN-" + asin) &&
            profileRepository.findBySku(asin).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "ASIN '" + asin + "' 已存在");
        }

        // Build Amazon URL for Bright Data
        String amazonUrl = "https://www.amazon.com/dp/" + asin;
        BrightDataScrapeRequest scrapeReq = new BrightDataScrapeRequest();
        scrapeReq.setInput(List.of(Map.of("url", amazonUrl)));

        ProductProfile profile = ProductProfile.builder()
                .userId(userId)
                .productName("ASIN-" + asin)
                .brand("")
                .sku(asin)
                .category(DEFAULT_CATEGORY)
                .status(STATUS_PENDING_PARSE)
                .build();
        profile = profileRepository.save(profile);

        try {
            log.info("[ASIN] >>> Starting Bright Data scrape for ASIN={}", asin);
            ApiResponse<?> scrapeResponse = brightDataService.scrape(scrapeReq, userId);
            log.info("[ASIN] <<< Bright Data scrape done: code={}, message={}",
                    scrapeResponse.getCode(), scrapeResponse.getMessage());
            if (scrapeResponse.getCode() != 200) {
                profile.setStatus(STATUS_PARSE_FAILED);
                profile.setParseError("Bright Data 采集失败: " + scrapeResponse.getMessage());
                return profileRepository.save(profile);
            }

            // Convert scrape response to JSON string for LLM parsing
            String brightDataJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(scrapeResponse.getData());
            String factsJson = parseWithLlm("bright_data", brightDataJson);
            if (factsJson != null) {
                profile.setProductFactsJson(factsJson);
                String title = extractTitleFromBrightData(scrapeResponse.getData());
                if (!isBlank(title)) {
                    if (!profileRepository.existsByProductName(title)) {
                        profile.setProductName(title);
                    }
                }
                profile.setStatus(STATUS_PENDING_CONFIRM);
                checkSkuModelDuplicate(profile);
            } else {
                profile.setStatus(STATUS_PARSE_FAILED);
                profile.setParseError("LLM 解析 Bright Data 结果失败，请在详情页手动粘贴数据");
            }
        } catch (Exception e) {
            log.warn("Bright Data scrape failed for ASIN {}: {}", asin, e.getMessage());
            profile.setStatus(STATUS_PARSE_FAILED);
            profile.setParseError("Bright Data 采集异常: " + e.getMessage());
        }
        return profileRepository.save(profile);
    }

    @Transactional
    public ProductProfile reparse(Long id, Long userId) {
        ProductProfile profile = get(id, userId);
        if (profile.getMarkdownContent() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可重新解析的 Markdown 内容");
        }
        profile.setStatus(STATUS_PENDING_PARSE);
        profile = profileRepository.save(profile);

        String factsJson = parseWithLlm("markdown", profile.getMarkdownContent());
        if (factsJson != null) {
            profile.setProductFactsJson(factsJson);
            profile.setStatus(STATUS_PENDING_CONFIRM);
            profile.setParseError(null);
            checkSkuModelDuplicate(profile);
        } else {
            profile.setStatus(STATUS_PARSE_FAILED);
            profile.setParseError("LLM 解析失败，请检查 Markdown 格式后重试");
        }
        return profileRepository.save(profile);
    }

    @Transactional
    public ProductProfile updateFacts(Long id, String productFactsJson, Long userId) {
        ProductProfile profile = get(id, userId);
        profile.setProductFactsJson(productFactsJson);
        return profileRepository.save(profile);
    }

    @Transactional
    public ProductProfile confirm(Long id, Long userId) {
        ProductProfile profile = get(id, userId);
        if (!STATUS_PENDING_CONFIRM.equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不允许确认，需要先解析");
        }

        // Check for SKU / model duplicate → create version on existing, update on new
        Long existingProfileId = findExistingBySkuOrModel(profile);
        if (existingProfileId != null && !existingProfileId.equals(profile.getId())) {
            // SKU exists on another profile — this is a new version of that product
            ProductProfile existing = profileRepository.findById(existingProfileId).orElseThrow();
            createVersionSnapshot(existing, profile.getProductFactsJson(), userId);
            // Delete the duplicate draft profile
            profileRepository.delete(profile);
            return profileRepository.findById(existingProfileId).orElseThrow();
        }

        // Fresh product — create first version
        createVersionSnapshot(profile, profile.getProductFactsJson(), userId);
        profile.setStatus(STATUS_CONFIRMED);
        return profileRepository.save(profile);
    }

    @Transactional
    public ProductProfile createNewVersion(Long id, String markdownContent, Long userId) {
        ProductProfile profile = get(id, userId);
        if (profile.getStatus() != STATUS_CONFIRMED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有已确认的产品资料可以创建新版本");
        }

        // Save old markdown to history, set new markdown, re-parse
        profile.setMarkdownContent(markdownContent);
        profile.setStatus(STATUS_PENDING_PARSE);
        profile = profileRepository.save(profile);

        String factsJson = parseWithLlm("markdown", markdownContent);
        if (factsJson != null) {
            profile.setProductFactsJson(factsJson);
            profile.setStatus(STATUS_PENDING_CONFIRM);
            profile.setParseError(null);
        } else {
            profile.setStatus(STATUS_PARSE_FAILED);
            profile.setParseError("LLM 解析失败");
        }
        return profileRepository.save(profile);
    }

    @Transactional
    public ProductProfileImage uploadImage(Long profileId, MultipartFile file, String tag, Long userId) {
        ProductProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "产品资料不存在"));
        if (!profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该产品资料");
        }

        String fileName = file.getOriginalFilename();
        String ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf("."));
        }
        String storedName = "profile-" + profileId + "-" + UUID.randomUUID() + ext;
        Path targetDir = Paths.get(uploadDir, "products", String.valueOf(profileId));
        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storedName);
            file.transferTo(targetPath.toFile());

            ProductProfileImage image = ProductProfileImage.builder()
                    .profileId(profileId)
                    .fileName(fileName)
                    .filePath("/uploads/products/" + profileId + "/" + storedName)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .tag(tag != null ? tag.trim() : "other")
                    .uploadedBy(userId)
                    .build();
            return imageRepository.save(image);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteImage(Long imageId, Long userId) {
        ProductProfileImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片不存在"));
        ProductProfile profile = profileRepository.findById(image.getProfileId()).orElse(null);
        if (profile != null && !profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该图片");
        }
        imageRepository.delete(image);
    }

    public ProductProfile get(Long id, Long userId) {
        ProductProfile profile = profileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "产品资料不存在"));
        if (!profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该产品资料");
        }
        return profile;
    }

    public Page<ProductProfile> list(Long userId, String status, String keyword, Pageable pageable) {
        Specification<ProductProfile> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("productName")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("brand")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return profileRepository.findAll(spec, pageable);
    }

    public List<ProductProfileVersion> getVersions(Long profileId, Long userId) {
        ProductProfile profile = get(profileId, userId);
        return versionRepository.findByProfileIdOrderByVersionNumberDesc(profile.getId());
    }

    public ProductProfileVersion getVersion(Long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版本不存在"));
    }

    public List<ProductProfileImage> getImages(Long profileId, Long userId) {
        ProductProfile profile = get(profileId, userId);
        return imageRepository.findByProfileId(profile.getId());
    }

    @Transactional
    public void delete(Long id, Long userId) {
        ProductProfile profile = get(id, userId);
        imageRepository.deleteByProfileId(profile.getId());
        profileRepository.delete(profile);
    }

    // --- Internal helpers ---

    private void createVersionSnapshot(ProductProfile profile, String factsJson, Long userId) {
        int nextVersion = versionRepository.countByProfileId(profile.getId()) + 1;
        ProductProfileVersion version = ProductProfileVersion.builder()
                .profileId(profile.getId())
                .versionNumber(nextVersion)
                .productFactsJson(factsJson)
                .confirmedBy(userId)
                .confirmedAt(LocalDateTime.now())
                .build();
        version = versionRepository.save(version);
        profile.setProductFactsJson(factsJson);
        profile.setCurrentVersionId(version.getId());
    }

    private void checkSkuModelDuplicate(ProductProfile profile) {
        Long existing = findExistingBySkuOrModel(profile);
        if (existing != null && !existing.equals(profile.getId())) {
            profile.setStatus(STATUS_PENDING_CONFIRM + "_VERSION");
        }
    }

    private Long findExistingBySkuOrModel(ProductProfile profile) {
        if (profile.getSku() != null && !profile.getSku().isBlank()) {
            var existing = profileRepository.findBySku(profile.getSku().trim());
            if (existing.isPresent()) return existing.get().getId();
        }
        if (profile.getModelNumber() != null && !profile.getModelNumber().isBlank()) {
            var existing = profileRepository.findByModelNumber(profile.getModelNumber().trim());
            if (existing.isPresent()) return existing.get().getId();
        }
        return null;
    }

    private String extractTitleFromMarkdown(String markdown) {
        if (isBlank(markdown)) return null;
        var m = java.util.regex.Pattern.compile("(?m)^#\\s+(.+)$").matcher(markdown);
        if (m.find()) { String t = m.group(1).trim(); if (!t.isBlank() && t.length() < 200) return t; }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractTitleFromBrightData(Object data) {
        if (data == null) return null;
        try {
            String json = objectMapper.writeValueAsString(data);
            var root = objectMapper.readTree(json);
            var first = root.isArray() ? root.get(0) : root;
            if (first != null && first.has("title")) {
                String t = first.get("title").asText();
                if (!isBlank(t) && t.length() < 200) return t;
            }
        } catch (Exception e) {
            log.warn("Failed to extract title from Bright Data: {}", e.getMessage());
        }
        return null;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ======== LLM-powered parsing ========

    /**
     * 调用 LLM 解析输入内容为结构化 car stereo 产品参数 JSON。
     *
     * @param sourceType "markdown" 或 "bright_data"
     * @param content    原始内容
     * @return 结构化 JSON 字符串，失败返回 null
     */

    private static final String JSON_STRUCTURE_TEMPLATE =
            "{ \"identity\": { \"product_name\": \"\", \"brand\": \"\", \"manufacturer\": \"\", \"model_number\": \"\", \"sku\": \"\", \"target_asin\": \"\", \"category\": \"car stereo\" },"
            + " \"physical_specs\": { \"screen_size\": \"\", \"form_factor\": \"\", \"product_dimensions\": \"\", \"color\": \"\", \"material\": \"\" },"
            + " \"technical_specs\": { \"controller_type\": \"\", \"connectivity\": [], \"connector_types\": [], \"control_methods\": [] },"
            + " \"features\": { \"carplay\": \"\", \"android_auto\": \"\", \"bluetooth\": \"\", \"wifi\": \"\", \"backup_camera\": \"\", \"gps_navigation\": \"\", \"steering_wheel_control\": \"\", \"fm_am_radio\": \"\" },"
            + " \"compatibility\": { \"compatible_devices\": [], \"vehicle_fitment\": [], \"unsupported_or_unknown\": [] },"
            + " \"included_items\": [], \"warranty\": \"\" }";

    /**
     * 调用模型管理中的文本模型解析输入内容为 car stereo 产品参数 JSON。
     * 没有可用模型时自动降级到正则提取。
     */
    private String parseWithLlm(String sourceType, String content) {
        if (isBlank(content)) return null;

        AiModel model = findTextModel();
        if (model == null) {
            log.info("No enabled TEXT model in model management, using regex fallback");
            return "markdown".equals(sourceType) ? fallbackParseMarkdown(content) : null;
        }

        String systemPrompt = "你是一个 car stereo 产品参数提取专家。请从以下"
                + ("markdown".equals(sourceType) ? "Markdown 文档" : "Bright Data 商品数据")
                + "中提取产品参数，返回严格的 JSON 格式，不要包含任何其他文字。";

        String userPrompt = "请提取以下 car stereo 产品参数，严格按以下 JSON 结构返回：\n"
                + JSON_STRUCTURE_TEMPLATE
                + "\n输入内容：\n" + truncate(content, 8000);

        try {
            // Build request body as HashMap to ensure clean JSON serialization
            Map<String, Object> bodyMap = new java.util.HashMap<>();
            bodyMap.put("model", model.getModelName());
            int maxTokens = model.getMaxTokens() != null ? Math.min(model.getMaxTokens(), 8192) : 4096;
            bodyMap.put("max_tokens", maxTokens);
            bodyMap.put("temperature", 0.3);
            List<Map<String, String>> msgs = new ArrayList<>();
            msgs.add(Map.of("role", "system", "content", systemPrompt));
            msgs.add(Map.of("role", "user", "content", userPrompt));
            bodyMap.put("messages", msgs);

            String url = buildChatEndpoint(model);
            log.info("Calling LLM: url={}, model={}", url, model.getModelName());

            String resp = webClientBuilder.build()
                    .post().uri(url)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .bodyValue(bodyMap)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                                log.warn("LLM API 4xx error: status={}, body={}", clientResponse.statusCode(), errorBody);
                                return clientResponse.createException().flatMap(mono -> {
                                    throw new RuntimeException("LLM API error: " + errorBody);
                                });
                            })
                    )
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (resp == null) return fallbackParseMarkdown(content);

            JsonNode root = objectMapper.readTree(resp);
            String text = root.path("choices").get(0).path("message").path("content").asText();
            if (isBlank(text)) return fallbackParseMarkdown(content);

            text = text.replaceAll("(?s)```\\w*\\s*", "").trim();
            int js = text.indexOf('{'), je = text.lastIndexOf('}');
            if (js >= 0 && je > js) text = text.substring(js, je + 1);

            objectMapper.readTree(text); // validate
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    objectMapper.readTree(text));
        } catch (Exception e) {
            log.warn("LLM call failed for {}: {}, using regex fallback", sourceType, e.getMessage());
            return "markdown".equals(sourceType) ? fallbackParseMarkdown(content) : null;
        }
    }

    private AiModel findTextModel() {
        var def = aiModelRepository.findByIsDefaultTrue();
        if (def.isPresent() && def.get().getEnabled() && "TEXT".equals(def.get().getModelType())) {
            return def.get();
        }
        var list = aiModelRepository.findByModelTypeAndEnabled("TEXT", true);
        return list.isEmpty() ? null : list.get(0);
    }

    private String buildChatEndpoint(AiModel model) {
        String base = model.getApiUrl();
        if (isBlank(base)) return "";
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        // If the URL already ends with a known endpoint path, return as-is
        if (base.endsWith("/chat/completions") || base.endsWith("/messages")) return base;
        // Build endpoint from base + version + path
        String ver = model.getApiVersion() != null ? model.getApiVersion() : "";
        // Remove leading slash from ver for path building
        if (ver.startsWith("/")) ver = ver.substring(1);
        String endpoint;
        if ("anthropic".equalsIgnoreCase(model.getApiType())) {
            endpoint = "/messages";
        } else {
            endpoint = "/chat/completions";
        }
        // Check if base already contains the version path
        if (!ver.isBlank() && !base.contains("/" + ver)) {
            return base + "/" + ver + endpoint;
        }
        return base + endpoint;
    }

    // ======== Regex fallback parsing ========

    private String fallbackParseMarkdown(String markdown) {
        if (isBlank(markdown)) return null;
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode id = root.putObject("identity");
            id.put("product_name", extractTitleFromMarkdown(markdown));
            id.put("brand", regexExtract(markdown, "品牌", "Brand", "brand"));
            id.put("manufacturer", regexExtract(markdown, "制造商", "Manufacturer"));
            id.put("model_number", regexExtract(markdown, "型号", "Model Number", "Model"));
            id.put("sku", regexExtract(markdown, "SKU", "产品编号"));
            id.put("category", "car stereo");
            root.putObject("physical_specs").put("screen_size", regexExtract(markdown, "屏幕尺寸", "Screen Size"));
            root.putObject("technical_specs");
            root.putObject("features");
            root.putObject("compatibility");
            root.put("included_items", "[]");
            root.put("warranty", regexExtract(markdown, "保修", "Warranty"));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Regex fallback failed: {}", e.getMessage());
            return null;
        }
    }

    private String regexExtract(String text, String... keys) {
        for (String key : keys) {
            String p = "(?im)(?:^|\\n)#{1,6}\\s*" + java.util.regex.Pattern.quote(key) + "\\s*\\n([^#\\n][^\\n]*)";
            var m = java.util.regex.Pattern.compile(p).matcher(text);
            if (m.find()) { String v = m.group(1).trim(); if (!v.isBlank()) return v; }
            p = "(?im)\\*{1,2}" + java.util.regex.Pattern.quote(key) + "\\*{1,2}\\s*[:：]?\\s*([^\\n]+)";
            m = java.util.regex.Pattern.compile(p).matcher(text);
            if (m.find()) { String v = m.group(1).trim(); if (!v.isBlank()) return v; }
        }
        return "";
    }
}
