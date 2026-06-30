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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

            // 先收集所有需要 LLM 生成文案的 items，批量处理一次（减少 DeepSeek 请求频率）
            Map<String, CognitionTexts> batchResults = batchGenerateAllCognitionTexts(facts);

            addCompatibilityCognitions(facts, cognitions, globalConstraints, claimsToAvoid, seen);
            addBulletCognitions(facts, cognitions, seen, batchResults);
            addProductDetailCognitions(facts, cognitions, claimsToAvoid, seen, batchResults);

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
                    "帮助买家在购买前快速确认这款音响是否适合他们的车辆。",
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

    private void addBulletCognitions(JsonNode facts, ArrayNode cognitions, Set<String> seen,
                                      Map<String, CognitionTexts> batchResults) {
        JsonNode bullets = facts.path("amazon_listing").path("bullet_points");
        if (!bullets.isArray()) return;
        for (int i = 0; i < bullets.size() && cognitions.size() < MAX_COGNITIONS; i++) {
            String text = bullets.get(i).asText("").trim();
            if (isBlank(text)) continue;
            Classification c = classify(text);
            addBulletCognitionItem(cognitions, seen, c, text, i, batchResults);
        }
    }

    private void addProductDetailCognitions(JsonNode facts, ArrayNode cognitions, ArrayNode claimsToAvoid,
                                             Set<String> seen, Map<String, CognitionTexts> batchResults) {
        JsonNode details = facts.path("amazon_listing").path("product_details");
        if (!details.isObject()) return;
        details.properties().forEach(entry -> {
            if (cognitions.size() >= MAX_COGNITIONS) return;
            String key = entry.getKey();
            String value = entry.getValue().asText("");
            if (isBlank(value)) return;
            String combined = key + ": " + value;
            Classification c = classify(combined);
            addProductDetailCognitionItem(cognitions, seen, c, key, combined, batchResults);

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
    private void addBulletCognitionItem(ArrayNode cognitions, Set<String> seen, Classification c, String sourceText,
                                         int index, Map<String, CognitionTexts> batchResults) {
        String sourcePath = "amazon_listing.bullet_points[" + index + "]";
        CognitionTexts ct = lookupBatchResult(batchResults, sourceText);
        if (ct == null) ct = generateCognitionTexts(c.type(), c.visualModel(), c.feature(), sourceText);
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
    private void addProductDetailCognitionItem(ArrayNode cognitions, Set<String> seen, Classification c, String feature,
                                                String combined, Map<String, CognitionTexts> batchResults) {
        String sourcePath = "amazon_listing.product_details." + feature;
        CognitionTexts ct = lookupBatchResult(batchResults, combined);
        if (ct == null) ct = generateCognitionTexts(c.type(), c.visualModel(), feature, combined);
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
        item.put("type", typeToChinese(type));
        item.put("visual_model", visualModelToChinese(visualModel));
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

    /** 将英文 type 映射为中文展示。 */
    private String typeToChinese(String type) {
        return switch (type) {
            case "compatibility" -> "兼容性";
            case "connection" -> "连接方式";
            case "display" -> "屏幕显示";
            case "safety" -> "安全保障";
            case "navigation" -> "导航功能";
            case "audio" -> "音频娱乐";
            case "performance" -> "性能配置";
            case "installation" -> "安装服务";
            default -> type;
        };
    }

    /** 将英文 visualModel 映射为中文展示。 */
    private String visualModelToChinese(String visualModel) {
        return switch (visualModel) {
            case "infographic" -> "信息图";
            case "connection" -> "连接示意";
            case "scenario" -> "场景图";
            default -> visualModel;
        };
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

    /** 待批量处理的特征项。 */
    private record BatchItem(String type, String visualModel, String feature, String sourceText) {}

    /** LLM 生成的认知文案结果。 */
    private record CognitionTexts(
            String buyerCognitionCn, String buyerCognitionEn,
            String painPointCn, String painPointEn,
            String beliefCn, String beliefEn) {}

    /**
     * 调用 LLM 生成认知文案，失败时返回 null（由调用方 fallback 到模板）。
     * 注意：正常流程已走批量路径（batchGenerateAllCognitionTexts），此方法仅作为
     * 未命中批量缓存时的兜底（理论上不会发生）。
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
     * 从 facts 中收集所有 bullet point + product detail，返回批量处理列表。
     */
    private List<BatchItem> collectBatchItems(JsonNode facts) {
        List<BatchItem> items = new ArrayList<>();
        JsonNode bullets = facts.path("amazon_listing").path("bullet_points");
        if (bullets.isArray()) {
            for (int i = 0; i < bullets.size() && items.size() < MAX_COGNITIONS; i++) {
                String text = bullets.get(i).asText("").trim();
                if (isBlank(text)) continue;
                Classification c = classify(text);
                items.add(new BatchItem(c.type(), c.visualModel(), c.feature(), text));
            }
        }
        JsonNode details = facts.path("amazon_listing").path("product_details");
        if (details.isObject()) {
            for (var entry : details.properties()) {
                if (items.size() >= MAX_COGNITIONS) break;
                String key = entry.getKey();
                String value = entry.getValue().asText("");
                if (isBlank(value)) continue;
                String combined = key + ": " + value;
                Classification c = classify(combined);
                items.add(new BatchItem(c.type(), c.visualModel(), key, combined));
            }
        }
        return items;
    }

    /**
     * 批量调用 LLM 生成所有 items 的认知文案（只发一次请求），
     * 返回 Map&lt;sourceText, CognitionTexts&gt;。失败时返回空 Map，由调用方 fallback 到模板。
     */
    private Map<String, CognitionTexts> batchGenerateAllCognitionTexts(JsonNode facts) {
        List<BatchItem> items = collectBatchItems(facts);
        if (items.isEmpty() || llmService == null) return Collections.emptyMap();

        io.agentscope.core.model.GenerateOptions modelOptions = buildDefaultModelOptions();
        if (modelOptions == null) return Collections.emptyMap();

        try {
            // 构建包含所有 items 的 JSON prompt
            StringBuilder itemsJson = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                BatchItem item = items.get(i);
                if (i > 0) itemsJson.append(",");
                itemsJson.append("{\"index\":").append(i)
                        .append(",\"type\":\"").append(escapeJson(item.type()))
                        .append("\",\"visual_model\":\"").append(escapeJson(item.visualModel()))
                        .append("\",\"feature\":\"").append(escapeJson(item.feature()))
                        .append("\",\"source_text\":\"").append(escapeJson(truncate(item.sourceText(), 800)))
                        .append("\"}");
            }
            itemsJson.append("]");

            String systemPrompt = """
                    你是一名 Amazon US car stereo 视觉营销策略师。
                    根据以下产品特征的分类信息，为每个特征批量生成买家认知文案。
                    只能使用输入事实，不允许发明功能。
                    中英双语输出，JSON 格式，每个 item 包含字段：
                    - index: 序号（与输入对应）
                    - buyer_cognition_cn / buyer_cognition_en：买家认知核心主张
                    - pain_point_cn / pain_point_en：该卖点解决的用户痛点
                    - belief_cn / belief_en：买家看完后应建立的购买信念
                    输出合法 JSON 数组，不要 markdown 包裹。""";

            String userJson = "{\"items\": " + itemsJson + "}";
            List<Map<String, String>> history = List.of(Map.of("role", "user", "content", userJson));

            String response = llmService.syncChat(systemPrompt, history, modelOptions);
            if (response == null || response.isBlank()) {
                log.warn("Batch LLM returned empty response, falling back to per-item templates");
                return Collections.emptyMap();
            }

            log.debug("Batch LLM cognition response length: {}", response.length());

            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                log.warn("Batch LLM response is not an array, falling back to templates");
                return Collections.emptyMap();
            }

            Map<String, CognitionTexts> results = new java.util.LinkedHashMap<>();
            for (JsonNode node : root) {
                int idx = node.path("index").asInt(-1);
                if (idx < 0 || idx >= items.size()) continue;
                BatchItem item = items.get(idx);
                CognitionTexts ct = new CognitionTexts(
                        stringOrDefault(node, "buyer_cognition_cn"),
                        stringOrDefault(node, "buyer_cognition_en"),
                        stringOrDefault(node, "pain_point_cn"),
                        stringOrDefault(node, "pain_point_en"),
                        stringOrDefault(node, "belief_cn"),
                        stringOrDefault(node, "belief_en"));
                results.put(item.sourceText(), ct);
            }

            if (results.isEmpty()) {
                log.warn("Batch LLM returned no parseable results, falling back to templates");
                return Collections.emptyMap();
            }

            log.info("Batch cognition generation: {}/{} items succeeded", results.size(), items.size());
            return results;
        } catch (Exception e) {
            log.warn("Batch LLM cognition generation failed, falling back to per-item: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 从 batch 结果中按 sourceText 精确查找，返回 null 表示未命中。
     */
    private CognitionTexts lookupBatchResult(Map<String, CognitionTexts> batchResults, String sourceText) {
        if (batchResults.isEmpty()) return null;
        CognitionTexts ct = batchResults.get(sourceText);
        if (ct != null) return ct;
        // Fallback: 按截断后的文本匹配
        return batchResults.get(truncate(sourceText, 800));
    }

    /**
     * JSON 字符串转义（双引号、反斜线、换行等）。
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

        int resolvedMaxTokens = model.getMaxTokens();
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