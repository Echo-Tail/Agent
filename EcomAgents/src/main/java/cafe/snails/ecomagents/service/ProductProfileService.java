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
    private static final String SOURCE_TYPE_MARKDOWN = "MARKDOWN";
    private static final String SOURCE_TYPE_BRIGHT_DATA_ASIN = "BRIGHT_DATA_ASIN";

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
                .sourceType(SOURCE_TYPE_MARKDOWN)
                .status(STATUS_PENDING_PARSE)
                .build();
        profile = profileRepository.save(profile);

        String factsJson = parseWithLlm("markdown", markdownContent);
        if (factsJson != null) {
            profile.setProductFactsJson(factsJson);
            applyFactsToProfile(profile, factsJson);
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
        if (profileRepository.findBySku(asin).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "ASIN already exists: " + asin);
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
                .sourceType(SOURCE_TYPE_BRIGHT_DATA_ASIN)
                .sourceAsin(asin)
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
            profile.setSourceRawJson(brightDataJson);
            String factsJson = parseWithLlm("bright_data", brightDataJson);
            if (factsJson != null) {
                profile.setProductFactsJson(factsJson);
                applyFactsToProfile(profile, factsJson);
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
        String sourceType = profile.getSourceType();
        String parseSourceType = SOURCE_TYPE_BRIGHT_DATA_ASIN.equals(sourceType) ? "bright_data" : "markdown";
        String parseContent = SOURCE_TYPE_BRIGHT_DATA_ASIN.equals(sourceType) ? profile.getSourceRawJson() : profile.getMarkdownContent();
        if (isBlank(parseContent)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "No original product profile source is available for reparsing");
        }
        profile.setStatus(STATUS_PENDING_PARSE);
        profile = profileRepository.save(profile);

        String factsJson = parseWithLlm(parseSourceType, parseContent);
        if (factsJson != null) {
            profile.setProductFactsJson(factsJson);
            applyFactsToProfile(profile, factsJson);
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
        applyFactsToProfile(profile, productFactsJson);
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
        profile.setSourceType(SOURCE_TYPE_MARKDOWN);
        profile.setSourceRawJson(null);
        profile.setStatus(STATUS_PENDING_PARSE);
        profile = profileRepository.save(profile);

        String factsJson = parseWithLlm("markdown", markdownContent);
        if (factsJson != null) {
            profile.setProductFactsJson(factsJson);
            applyFactsToProfile(profile, factsJson);
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
            + " \"amazon_listing\": { \"title\": \"\", \"bullet_points\": [], \"product_description\": \"\", \"product_details\": {}, \"technical_details\": {}, \"included_components_raw\": [], \"important_information\": \"\" },"
            + " \"physical_specs\": {}, \"technical_specs\": {}, \"features\": {},"
            + " \"compatibility\": { \"vehicle_fitment\": [], \"compatible_devices\": [], \"not_compatible\": [], \"unsupported_or_unknown\": [], \"fitment_notes\": \"\" },"
            + " \"included_items\": [], \"warranty\": \"\", \"selling_points\": [], \"claims_to_avoid\": [],"
            + " \"review\": { \"status\": \"needs_human_review\", \"missing_fields\": [], \"low_confidence_fields\": [], \"notes\": \"\" } }";

    private String parseWithLlm(String sourceType, String content) {
        if (isBlank(content)) return null;

        String normalizedInput = normalizeInputForLlm(sourceType, content);
        AiModel model = findTextModel();
        if (model == null) {
            log.info("No enabled TEXT model in model management, using local product profile fallback");
            return fallbackParse(sourceType, normalizedInput);
        }

        String systemPrompt = "You are an Amazon US car stereo product data extraction expert. Use only the input, preserve title, bullet_points, description, and product details, and return valid JSON only.";

        String userPrompt = "Extract car stereo product facts. bullet_points must preserve the original list length and wording; do not assume exactly 5 bullets. Use this JSON schema:\n"
                + JSON_STRUCTURE_TEMPLATE
                + "\nNormalized input:\n" + normalizedInput;

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

            if (resp == null) return fallbackParse(sourceType, normalizedInput);

            JsonNode root = objectMapper.readTree(resp);
            String text = root.path("choices").get(0).path("message").path("content").asText();
            if (isBlank(text)) return fallbackParse(sourceType, normalizedInput);

            text = text.replaceAll("(?s)```\\w*\\s*", "").trim();
            int js = text.indexOf('{'), je = text.lastIndexOf('}');
            if (js >= 0 && je > js) text = text.substring(js, je + 1);

            objectMapper.readTree(text); // validate
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    objectMapper.readTree(text));
        } catch (Exception e) {
            log.warn("LLM call failed for {}: {}, using local fallback", sourceType, e.getMessage());
            return fallbackParse(sourceType, normalizedInput);
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


    private String normalizeInputForLlm(String sourceType, String content) {
        try {
            if (!"bright_data".equals(sourceType)) {
                ObjectNode n = objectMapper.createObjectNode();
                n.put("source_type", "markdown");
                n.put("content", truncate(content, 20000));
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(n);
            }
            JsonNode root = objectMapper.readTree(content);
            JsonNode record = root.path("records").isArray() && root.path("records").size() > 0 ? root.path("records").get(0)
                    : root.isArray() && root.size() > 0 ? root.get(0) : root;
            ObjectNode n = objectMapper.createObjectNode();
            n.put("source_type", "bright_data");
            copyText(n, record, "asin", "asin", "ASIN");
            copyText(n, record, "title", "title", "product_title", "name");
            copyText(n, record, "brand", "brand", "Brand");
            copyText(n, record, "manufacturer", "manufacturer", "Manufacturer");
            copyText(n, record, "model_number", "model_number", "model", "Model", "Model Number", "part_number");
            copyText(n, record, "description", "description", "product_description", "Product Description");
            copyArray(n, record, "bullet_points", "bullet_points", "bullets", "feature_bullets", "about_this_item", "highlights");
            copyObject(n, record, "product_details", "product_details", "details", "Product details", "product_information");
            copyObject(n, record, "technical_details", "technical_details", "technical_information", "specifications", "tech_specs");
            copyArray(n, record, "included_components", "included_components", "included_items", "package_includes", "components");
            copyArray(n, record, "images", "images", "image_urls", "product_images");
            copyText(n, record, "warranty", "warranty", "Warranty");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(n);
        } catch (Exception e) {
            log.warn("Failed to normalize product input: {}", e.getMessage());
            return truncate(content, 20000);
        }
    }

    private void copyText(ObjectNode target, JsonNode source, String targetName, String... names) {
        JsonNode value = firstField(source, names);
        if (value != null && !value.isNull()) target.put(targetName, truncate(value.asText(value.toString()), 6000));
    }

    private void copyArray(ObjectNode target, JsonNode source, String targetName, String... names) {
        JsonNode value = firstField(source, names);
        if (value == null || value.isNull()) return;
        ArrayNode out = target.putArray(targetName);
        if (value.isArray()) {
            for (int i = 0; i < value.size() && i < 20; i++) out.add(truncate(value.get(i).asText(value.get(i).toString()), 1200));
        } else {
            out.add(truncate(value.asText(value.toString()), 1200));
        }
    }

    private void copyObject(ObjectNode target, JsonNode source, String targetName, String... names) {
        JsonNode value = firstField(source, names);
        if (value != null && value.isObject()) target.set(targetName, value);
    }

    private JsonNode firstField(JsonNode source, String... names) {
        for (String name : names) {
            JsonNode v = source.get(name);
            if (v != null && !v.isNull()) return v;
        }
        return null;
    }

    private String fallbackParse(String sourceType, String normalizedInput) {
        if (!"bright_data".equals(sourceType)) return fallbackParseMarkdown(normalizedInput);
        try {
            JsonNode input = objectMapper.readTree(normalizedInput);
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode id = root.putObject("identity");
            String title = input.path("title").asText("");
            id.put("product_name", title);
            id.put("brand", input.path("brand").asText(""));
            id.put("manufacturer", input.path("manufacturer").asText(""));
            id.put("model_number", input.path("model_number").asText(""));
            id.put("sku", input.path("asin").asText(""));
            id.put("target_asin", input.path("asin").asText(""));
            id.put("category", DEFAULT_CATEGORY);
            ObjectNode listing = root.putObject("amazon_listing");
            listing.put("title", title);
            listing.set("bullet_points", input.path("bullet_points").isArray() ? input.path("bullet_points") : objectMapper.createArrayNode());
            listing.put("product_description", input.path("description").asText(""));
            listing.set("product_details", input.path("product_details").isObject() ? input.path("product_details") : objectMapper.createObjectNode());
            listing.set("technical_details", input.path("technical_details").isObject() ? input.path("technical_details") : objectMapper.createObjectNode());
            listing.set("included_components_raw", input.path("included_components").isArray() ? input.path("included_components") : objectMapper.createArrayNode());
            listing.put("important_information", "");
            root.putObject("physical_specs");
            root.putObject("technical_specs");
            ObjectNode features = root.putObject("features");
            String all = (title + " " + listing.path("product_description").asText("") + " " + listing.path("bullet_points").toString()).toLowerCase();
            if (all.contains("carplay")) features.put("carplay", all.contains("wireless") ? "wireless Apple CarPlay" : "Apple CarPlay");
            if (all.contains("android auto")) features.put("android_auto", all.contains("wireless") ? "wireless Android Auto" : "Android Auto");
            if (all.contains("bluetooth")) features.put("bluetooth", "Bluetooth");
            if (all.contains("wifi") || all.contains("wi-fi")) features.put("wifi", "WiFi");
            if (all.contains("backup camera")) features.put("backup_camera", "backup camera support");
            if (all.contains("gps")) features.put("gps_navigation", "GPS navigation");
            ObjectNode compat = root.putObject("compatibility");
            ArrayNode fitment = compat.putArray("vehicle_fitment");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)Ford\\s*F[- ]?150[^.,;\\n]{0,120}").matcher(all);
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            while (m.find()) seen.add(m.group().trim());
            seen.forEach(fitment::add);
            compat.putArray("compatible_devices"); compat.putArray("not_compatible"); compat.putArray("unsupported_or_unknown"); compat.put("fitment_notes", "");
            root.putArray("included_items");
            root.put("warranty", input.path("warranty").asText(""));
            ArrayNode selling = root.putArray("selling_points");
            for (JsonNode b : listing.path("bullet_points")) selling.add(truncate(b.asText(), 240));
            root.putArray("claims_to_avoid");
            ObjectNode review = root.putObject("review");
            review.put("status", "needs_human_review"); review.putArray("missing_fields"); review.putArray("low_confidence_fields"); review.put("notes", "Parsed by local fallback; please verify before confirming.");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Bright Data fallback failed: {}", e.getMessage());
            return null;
        }
    }

    private void applyFactsToProfile(ProductProfile profile, String factsJson) {
        if (profile == null || isBlank(factsJson)) return;
        try {
            JsonNode root = objectMapper.readTree(factsJson);
            JsonNode id = root.path("identity");
            String productName = id.path("product_name").asText("");
            if (isBlank(productName)) productName = root.path("amazon_listing").path("title").asText("");
            if (!isBlank(productName) && (profile.getProductName().startsWith("ASIN-") || !profileRepository.existsByProductName(productName))) profile.setProductName(truncate(productName, 200));
            if (!isBlank(id.path("brand").asText(""))) profile.setBrand(id.path("brand").asText());
            if (!isBlank(id.path("sku").asText(""))) profile.setSku(id.path("sku").asText());
            if (!isBlank(id.path("model_number").asText(""))) profile.setModelNumber(id.path("model_number").asText());
            if (!isBlank(id.path("target_asin").asText(""))) profile.setTargetAsin(id.path("target_asin").asText());
            if (!isBlank(id.path("category").asText(""))) profile.setCategory(id.path("category").asText());
        } catch (Exception e) {
            log.warn("Failed to apply facts to profile: {}", e.getMessage());
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
