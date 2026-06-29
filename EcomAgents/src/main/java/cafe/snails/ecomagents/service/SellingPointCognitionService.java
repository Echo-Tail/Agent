package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ProductProfileRepository;
import cafe.snails.ecomagents.repository.ProductSellingPointCognitionVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellingPointCognitionService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private static final String CATEGORY = "car_stereo";
    private static final String CATEGORY_STRATEGY_VERSION = "car_stereo_v1";
    private static final int MAX_COGNITIONS = 20;

    private final ProductProfileRepository profileRepository;
    private final ProductSellingPointCognitionVersionRepository cognitionVersionRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LlmService llmService;

    @Autowired(required = false)
    private AiModelRepository aiModelRepository;

    @Transactional
    public ProductSellingPointCognitionVersion generate(Long profileId, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        if (isBlank(profile.getProductFactsJson())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Product facts are required before generating selling point cognitions");
        }

        String cognitionJson = generateLocalCognitionJson(profile);
        ProductSellingPointCognitionVersion version = ProductSellingPointCognitionVersion.builder()
                .profileId(profile.getId())
                .profileVersionId(profile.getCurrentVersionId())
                .versionNumber(cognitionVersionRepository.countByProfileId(profile.getId()) + 1)
                .status(STATUS_DRAFT)
                .cognitionJson(cognitionJson)
                .sourceFactsHash(sha256(profile.getProductFactsJson()))
                .createdBy(userId)
                .build();
        return cognitionVersionRepository.save(version);
    }

    public ProductSellingPointCognitionVersion getCurrent(Long profileId, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        return cognitionVersionRepository.findTopByProfileIdOrderByVersionNumberDesc(profile.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Selling point cognition version not found"));
    }

    public List<ProductSellingPointCognitionVersion> listVersions(Long profileId, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        return cognitionVersionRepository.findByProfileIdOrderByVersionNumberDesc(profile.getId());
    }

    @Transactional
    public ProductSellingPointCognitionVersion update(Long profileId, Long versionId, String cognitionJson, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        ProductSellingPointCognitionVersion version = getOwnedVersion(profile.getId(), versionId);
        if (STATUS_CONFIRMED.equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Confirmed cognition versions cannot be edited");
        }
        validateCognitionJson(cognitionJson);
        version.setCognitionJson(cognitionJson);
        return cognitionVersionRepository.save(version);
    }

    @Transactional
    public ProductSellingPointCognitionVersion confirm(Long profileId, Long versionId, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        ProductSellingPointCognitionVersion version = getOwnedVersion(profile.getId(), versionId);
        validateCognitionJson(version.getCognitionJson());
        version.setStatus(STATUS_CONFIRMED);
        version.setConfirmedBy(userId);
        version.setConfirmedAt(LocalDateTime.now());
        return cognitionVersionRepository.save(version);
    }

    private ProductProfile getAuthorizedProfile(Long profileId, Long userId) {
        ProductProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product profile not found"));
        if (!profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to access this product profile");
        }
        return profile;
    }

    private ProductSellingPointCognitionVersion getOwnedVersion(Long profileId, Long versionId) {
        ProductSellingPointCognitionVersion version = cognitionVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Selling point cognition version not found"));
        if (!profileId.equals(version.getProfileId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cognition version does not belong to this product profile");
        }
        return version;
    }

    private String generateLocalCognitionJson(ProductProfile profile) {
        try {
            JsonNode facts = objectMapper.readTree(profile.getProductFactsJson());
            ObjectNode root = objectMapper.createObjectNode();
            root.put("category", CATEGORY);
            root.put("category_strategy_version", CATEGORY_STRATEGY_VERSION);
            root.put("profile_id", profile.getId());
            if (profile.getCurrentVersionId() != null) root.put("profile_version_id", profile.getCurrentVersionId());
            root.put("status", "draft");
            ArrayNode cognitions = root.putArray("buyer_cognitions");
            ArrayNode globalConstraints = root.putArray("global_constraints");
            ArrayNode claimsToAvoid = root.putArray("claims_to_avoid");
            Set<String> seen = new HashSet<>();

            addCompatibilityCognitions(facts, cognitions, globalConstraints, claimsToAvoid, seen);
            addBulletCognitions(facts, cognitions, seen);
            addProductDetailCognitions(facts, cognitions, claimsToAvoid, seen);

            ObjectNode review = root.putObject("review");
            review.put("status", "needs_human_review");
            ArrayNode missing = review.putArray("missing_fields");
            if (cognitions.isEmpty()) missing.add("buyer_cognitions");
            review.putArray("low_confidence_items");
            review.put("notes", "Generated by cognition engine (LLM text generation with template fallback); verify wording and evidence before confirming.");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to generate local cognition json for profile {}: {}", profile.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to parse product facts JSON");
        }
    }

    private void addCompatibilityCognitions(JsonNode facts, ArrayNode cognitions, ArrayNode globalConstraints,
                                            ArrayNode claimsToAvoid, Set<String> seen) {
        JsonNode fitment = facts.path("compatibility").path("vehicle_fitment");
        if (fitment.isArray() && !fitment.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : fitment) {
                if (!text.isEmpty()) text.append("; ");
                text.append(item.asText());
            }
            String sourceText = text.toString();
            globalConstraints.add("Only show compatibility with: " + sourceText);
            addCognition(cognitions, seen, "compatibility", "infographic", "Vehicle Compatibility",
                    "Help buyers quickly confirm whether this stereo fits their vehicle before purchase.",
                    "Help buyers quickly confirm whether this stereo fits their vehicle before purchase.",
                    "Avoid buying the wrong head unit for the dashboard or AC type.",
                    "Avoid buying the wrong head unit for the dashboard or AC type.",
                    "Buyer believes the fitment information is clear enough to reduce purchase risk.",
                    "Buyer believes the fitment information is clear enough to reduce purchase risk.",
                    1, "high", "compatibility.vehicle_fitment", sourceText);
        }

        String fitmentNotes = facts.path("compatibility").path("fitment_notes").asText("");
        String all = facts.toString().toLowerCase(Locale.ROOT);
        if (all.contains("manual ac")) {
            globalConstraints.add("Only show Manual AC compatibility when discussing fitment.");
            claimsToAvoid.add("Do not claim automatic AC compatibility.");
        }
        if (!isBlank(fitmentNotes)) {
            globalConstraints.add(fitmentNotes);
        }
    }

    private void addBulletCognitions(JsonNode facts, ArrayNode cognitions, Set<String> seen) {
        JsonNode bullets = facts.path("amazon_listing").path("bullet_points");
        if (!bullets.isArray()) return;
        for (int i = 0; i < bullets.size() && cognitions.size() < MAX_COGNITIONS; i++) {
            String text = bullets.get(i).asText("").trim();
            if (isBlank(text)) continue;
            Classification c = classify(text);
            addBulletCognitionItem(cognitions, seen, c, text, i);
        }
    }

    private void addProductDetailCognitions(JsonNode facts, ArrayNode cognitions, ArrayNode claimsToAvoid, Set<String> seen) {
        JsonNode details = facts.path("amazon_listing").path("product_details");
        if (!details.isObject()) return;
        details.properties().forEach(entry -> {
            if (cognitions.size() >= MAX_COGNITIONS) return;
            String key = entry.getKey();
            String value = entry.getValue().asText("");
            if (isBlank(value)) return;
            String combined = key + ": " + value;
            Classification c = classify(combined);
            addProductDetailCognitionItem(cognitions, seen, c, key, combined);

            String lower = combined.toLowerCase(Locale.ROOT);
            if (lower.contains("backup camera") && (lower.contains("support") || lower.contains("input"))
                    && !lower.contains("built-in media") && !lower.contains("include")) {
                claimsToAvoid.add("Do not claim backup camera included unless evidence confirms it is included.");
            }
            if (lower.contains("4k") && !lower.contains("resolution")) {
                claimsToAvoid.add("Do not claim 4K display unless evidence says 4K resolution.");
            }
        });
    }

    /**
     * 处理单条 bullet point→cognition：先尝试 LLM 生成，失败回退到模板。
     */
    private void addBulletCognitionItem(ArrayNode cognitions, Set<String> seen, Classification c, String sourceText, int index) {
        String sourcePath = "amazon_listing.bullet_points[" + index + "]";
        CognitionTexts ct = generateCognitionTexts(c.type(), c.visualModel(), c.feature(), sourceText);
        addCognition(cognitions, seen, c.type(), c.visualModel(), c.feature(),
                ct != null ? ct.buyerCognitionCn() : cognitionFor(c.feature(), sourceText),
                ct != null ? ct.buyerCognitionEn() : cognitionFor(c.feature(), sourceText),
                ct != null ? ct.painPointCn() : painPointFor(c.type()),
                ct != null ? ct.painPointEn() : painPointFor(c.type()),
                ct != null ? ct.beliefCn() : beliefFor(c.type()),
                ct != null ? ct.beliefEn() : beliefFor(c.type()),
                c.priority(), "medium", sourcePath, sourceText);
    }

    /**
     * 处理单条 product detail→cognition：先尝试 LLM 生成，失败回退到模板。
     */
    private void addProductDetailCognitionItem(ArrayNode cognitions, Set<String> seen, Classification c, String feature, String combined) {
        String sourcePath = "amazon_listing.product_details." + feature;
        CognitionTexts ct = generateCognitionTexts(c.type(), c.visualModel(), feature, combined);
        addCognition(cognitions, seen, c.type(), c.visualModel(), feature,
                ct != null ? ct.buyerCognitionCn() : cognitionFor(feature, combined),
                ct != null ? ct.buyerCognitionEn() : cognitionFor(feature, combined),
                ct != null ? ct.painPointCn() : painPointFor(c.type()),
                ct != null ? ct.painPointEn() : painPointFor(c.type()),
                ct != null ? ct.beliefCn() : beliefFor(c.type()),
                ct != null ? ct.beliefEn() : beliefFor(c.type()),
                c.priority(), "medium", sourcePath, combined);
    }

    private void addCognition(ArrayNode cognitions, Set<String> seen, String type, String visualModel, String feature,
                              String buyerCognitionCn, String buyerCognitionEn,
                              String painPointCn, String painPointEn,
                              String beliefCn, String beliefEn,
                              int priority, String confidence,
                              String sourcePath, String sourceText) {
        if (cognitions.size() >= MAX_COGNITIONS || isBlank(feature) || isBlank(sourceText)) return;
        String id = toId(feature);
        if (!seen.add(id)) return;
        ObjectNode item = cognitions.addObject();
        item.put("id", id);
        item.put("enabled", true);
        item.put("priority", priority);
        item.put("type", type);
        item.put("visual_model", visualModel);
        item.put("feature", truncate(feature, 120));
        item.put("feature_cn", truncate(feature, 120));
        item.put("buyer_cognition_cn", truncate(firstNonBlank(buyerCognitionCn, buyerCognitionEn), 500));
        item.put("buyer_cognition_en", truncate(firstNonBlank(buyerCognitionEn, buyerCognitionCn), 500));
        item.put("scene_cn", "Real in-car usage scenario.");
        item.put("scene_en", "Real in-car usage scenario.");
        item.put("pain_point_cn", firstNonBlank(painPointCn, painPointEn));
        item.put("pain_point_en", firstNonBlank(painPointEn, painPointCn));
        item.put("belief_cn", firstNonBlank(beliefCn, beliefEn));
        item.put("belief_en", firstNonBlank(beliefEn, beliefCn));
        item.put("confidence", confidence);
        ArrayNode evidence = item.putArray("evidence");
        ObjectNode ev = evidence.addObject();
        ev.put("source_path", sourcePath);
        ev.put("source_text", truncate(sourceText, 1200));
        item.putArray("risk_notes");
    }

    private Classification classify(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "manual ac", "compatible", "fit", "dodge ram", "ford f", "dashboard")) {
            return new Classification("compatibility", "infographic", "Vehicle Compatibility", 1);
        }
        if (containsAny(lower, "carplay", "android auto", "bluetooth", "wi-fi", "wifi", "mirrorlink")) {
            return new Classification("connection", "connection", connectionFeature(lower), 2);
        }
        if (containsAny(lower, "screen", "qled", "resolution", "touch", "inch", "display")) {
            return new Classification("display", "scenario", "Screen Experience", 3);
        }
        if (containsAny(lower, "backup", "rear", "camera", "safe", "parking")) {
            return new Classification("safety", "scenario", "Safer Parking", 4);
        }
        if (containsAny(lower, "gps", "navigation", "map")) {
            return new Classification("navigation", "scenario", "GPS Navigation", 5);
        }
        if (containsAny(lower, "ram", "rom", "android 14", "processor", "rockchip", "system")) {
            return new Classification("performance", "infographic", "System Performance", 6);
        }
        if (containsAny(lower, "dsp", "eq", "audio", "music", "radio", "fm", "am")) {
            return new Classification("audio", "scenario", "Audio Entertainment", 7);
        }
        if (containsAny(lower, "install", "package", "warranty", "support", "harness", "manual")) {
            return new Classification("installation", "infographic", "Installation and Support", 8);
        }
        return new Classification("installation", "scenario", firstWords(text, 5), 10);
    }

    private String connectionFeature(String lower) {
        if (lower.contains("carplay")) return "Wireless CarPlay";
        if (lower.contains("android auto")) return "Android Auto";
        if (lower.contains("bluetooth")) return "Bluetooth Connection";
        if (lower.contains("wifi") || lower.contains("wi-fi")) return "WiFi Connectivity";
        return "Smartphone Connection";
    }

    private String cognitionFor(String feature, String sourceText) {
        String lower = sourceText.toLowerCase(Locale.ROOT);
        if (lower.contains("carplay")) return "Connect the phone to the car screen for navigation, calls, music, and messages.";
        if (lower.contains("android auto")) return "Use Android phone apps on the car screen while driving.";
        if (lower.contains("backup") || lower.contains("camera")) return "See the rear view while parking to make reversing feel safer.";
        if (lower.contains("qled") || lower.contains("screen") || lower.contains("resolution")) return "Make maps, apps, and controls easier to see on a clearer touchscreen.";
        if (lower.contains("gps") || lower.contains("navigation")) return "Navigate with maps directly on the dashboard screen.";
        if (lower.contains("install") || lower.contains("compatible") || lower.contains("fit")) return "Confirm fitment and installation details before purchase.";
        return "Turn this product feature into a clear buyer benefit: " + feature + ".";
    }

    private String painPointFor(String type) {
        return switch (type) {
            case "compatibility" -> "Buyers worry about choosing the wrong stereo for their vehicle.";
            case "connection" -> "Cable pairing and phone switching can feel inconvenient before driving.";
            case "display" -> "Small or unclear factory screens make navigation and app use harder.";
            case "safety" -> "Parking and calling while driving can create avoidable safety concerns.";
            case "navigation" -> "Phone-only navigation is less integrated with the dashboard.";
            case "audio" -> "Factory audio and media controls can feel outdated.";
            case "performance" -> "Slow head units make apps, maps, and music feel laggy.";
            default -> "Buyers need clear proof before choosing an upgrade product.";
        };
    }

    private String beliefFor(String type) {
        return switch (type) {
            case "compatibility" -> "The product is less risky to buy because fitment information is visible.";
            case "connection" -> "The old vehicle can feel closer to a modern connected car.";
            case "display" -> "The upgraded screen will make daily driving interactions easier.";
            case "safety" -> "The stereo can support a safer and more confident driving experience.";
            case "navigation" -> "Navigation becomes easier to follow from the dashboard.";
            case "audio" -> "Driving can feel more enjoyable with better entertainment controls.";
            case "performance" -> "The system should feel smoother for everyday apps and controls.";
            default -> "The feature gives a practical reason to upgrade.";
        };
    }

    private void validateCognitionJson(String cognitionJson) {
        if (isBlank(cognitionJson)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cognition JSON cannot be empty");
        }
        try {
            JsonNode root = objectMapper.readTree(cognitionJson);
            if (!root.path("buyer_cognitions").isArray()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Cognition JSON must contain buyer_cognitions array");
            }
            if (root.path("buyer_cognitions").size() > MAX_COGNITIONS) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Cognition JSON cannot contain more than 20 buyer cognitions");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid cognition JSON");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to hash product facts");
        }
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String toId(String value) {
        String id = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("(^_|_$)", "");
        return isBlank(id) ? "selling_point" : id;
    }

    private String firstWords(String value, int count) {
        if (isBlank(value)) return "Selling Point";
        String[] parts = value.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && i < count; i++) {
            if (!out.isEmpty()) out.append(' ');
            out.append(parts[i]);
        }
        return out.toString();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** LLM 生成的认知文案结果。 */
    private record CognitionTexts(
            String buyerCognitionCn, String buyerCognitionEn,
            String painPointCn, String painPointEn,
            String beliefCn, String beliefEn) {}

    /**
     * 调用 LLM 生成认知文案，失败时返回 null（由调用方 fallback 到模板）。
     */
    private CognitionTexts generateCognitionTexts(String type, String visualModel, String feature, String sourceText) {
        if (llmService == null) return null;
        io.agentscope.core.model.GenerateOptions modelOptions = buildDefaultModelOptions();
        if (modelOptions == null) return null;
        try {
            Map<String, Object> input = new java.util.LinkedHashMap<>();
            input.put("type", type);
            input.put("visual_model", visualModel);
            input.put("feature", feature);
            input.put("source_text", truncate(sourceText, 800));

            String systemPrompt = """
                    你是一名 Amazon US car stereo 视觉营销策略师。
                    根据产品特征的分类信息，生成买家认知文案。
                    只能使用输入事实，不允许发明功能。
                    中英双语输出，JSON 格式，字段说明：
                    - buyer_cognition_cn / buyer_cognition_en：买家认知核心主张
                    - pain_point_cn / pain_point_en：该卖点解决的用户痛点
                    - belief_cn / belief_en：买家看完后应建立的购买信念
                    输出合法 JSON，不要 markdown 包裹。""";

            String userJson = objectMapper.writeValueAsString(input);
            List<Map<String, String>> history = List.of(Map.of("role", "user", "content", userJson));

            String response = llmService.syncChat(systemPrompt, history, modelOptions);
            if (response == null || response.isBlank()) {
                log.warn("LLM returned empty response for feature '{}', falling back to template", truncate(feature, 60));
                return null;
            }

            log.debug("LLM cognition response for feature '{}': {}", truncate(feature, 60), truncate(response, 300));

            JsonNode root = objectMapper.readTree(response);
            return new CognitionTexts(
                    stringOrDefault(root, "buyer_cognition_cn"),
                    stringOrDefault(root, "buyer_cognition_en"),
                    stringOrDefault(root, "pain_point_cn"),
                    stringOrDefault(root, "pain_point_en"),
                    stringOrDefault(root, "belief_cn"),
                    stringOrDefault(root, "belief_en"));
        } catch (Exception e) {
            log.warn("LLM cognition text generation failed for feature '{}', falling back to template: {}",
                    truncate(feature, 60), e.getMessage());
            return null;
        }
    }

    /**
     * 从模型管理数据库中获取一个已启用的 TEXT 模型，构建 GenerateOptions。
     * 优先使用默认模型，无默认模型时取第一个已启用的 TEXT 模型。
     */
    private io.agentscope.core.model.GenerateOptions buildDefaultModelOptions() {
        if (aiModelRepository == null) return null;
        AiModel model = aiModelRepository.findByIsDefaultTrue()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()) && "TEXT".equals(m.getModelType()))
                .orElseGet(() -> aiModelRepository.findByModelTypeAndEnabled("TEXT", true)
                        .stream().findFirst().orElse(null));
        if (model == null) {
            log.info("No enabled TEXT model found in ai_models table, falling back to templates");
            return null;
        }

        int resolvedMaxTokens = model.getMaxTokens() != null && model.getMaxTokens() > 0
                ? Math.min(model.getMaxTokens(), 4096) : 4096;
        double resolvedTemp = model.getTemperature() != null ? model.getTemperature() : 0.3;

        log.info("Using model: name={}, modelName={}, maxTokens={}, temperature={}",
                model.getName(), model.getModelName(), resolvedMaxTokens, resolvedTemp);

        var execConfig = io.agentscope.core.model.ExecutionConfig.builder()
                .timeout(java.time.Duration.ofSeconds(30))
                .maxAttempts(1)
                .build();
        return io.agentscope.core.model.GenerateOptions.builder()
                .modelName(model.getModelName())
                .apiKey(model.getApiKey())
                .baseUrl(AiModelService.extractBaseUrl(model.getApiUrl()))
                .endpointPath(AiModelService.buildEndpointPath(model))
                .temperature(resolvedTemp)
                .maxTokens(resolvedMaxTokens)
                .executionConfig(execConfig)
                .build();
    }

    private String stringOrDefault(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return (node.isTextual() && !node.asText().isBlank()) ? node.asText() : "";
    }

    private String firstNonBlank(String first, String fallback) {
        return (first != null && !first.isBlank()) ? first : fallback;
    }

    private record Classification(String type, String visualModel, String feature, int priority) {}
}