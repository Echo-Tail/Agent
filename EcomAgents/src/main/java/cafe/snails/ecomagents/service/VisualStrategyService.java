package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import cafe.snails.ecomagents.model.ProductVisualStrategyVersion;
import cafe.snails.ecomagents.repository.ProductProfileRepository;
import cafe.snails.ecomagents.repository.ProductSellingPointCognitionVersionRepository;
import cafe.snails.ecomagents.repository.ProductVisualStrategyVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VisualStrategyService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String SCOPE_GALLERY = "gallery";
    public static final String SCOPE_APLUS = "aplus";

    private static final String CATEGORY = "car_stereo";
    private static final String CATEGORY_STRATEGY_VERSION = "car_stereo_v1";

    private final ProductProfileRepository profileRepository;
    private final ProductSellingPointCognitionVersionRepository cognitionVersionRepository;
    private final ProductVisualStrategyVersionRepository visualStrategyVersionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductVisualStrategyVersion generate(Long profileId, Long cognitionVersionId, List<String> contentScope, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        ProductSellingPointCognitionVersion cognition = resolveCognition(profile.getId(), cognitionVersionId);
        if (!STATUS_CONFIRMED.equals(cognition.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Visual strategy can only be generated from a confirmed cognition version");
        }

        Scope scope = normalizeScope(contentScope);
        String strategyJson = generateLocalStrategyJson(profile, cognition, scope);
        ProductVisualStrategyVersion version = ProductVisualStrategyVersion.builder()
                .profileId(profile.getId())
                .profileVersionId(cognition.getProfileVersionId())
                .cognitionVersionId(cognition.getId())
                .versionNumber(visualStrategyVersionRepository.countByProfileId(profile.getId()) + 1)
                .status(STATUS_DRAFT)
                .contentScope(scope.storageValue())
                .strategyJson(strategyJson)
                .createdBy(userId)
                .build();
        return visualStrategyVersionRepository.save(version);
    }

    public ProductVisualStrategyVersion getCurrent(Long profileId, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        return visualStrategyVersionRepository.findTopByProfileIdOrderByVersionNumberDesc(profile.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Visual strategy version not found"));
    }

    public List<ProductVisualStrategyVersion> listVersions(Long profileId, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        return visualStrategyVersionRepository.findByProfileIdOrderByVersionNumberDesc(profile.getId());
    }

    @Transactional
    public ProductVisualStrategyVersion update(Long profileId, Long versionId, String strategyJson, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        ProductVisualStrategyVersion version = getOwnedVersion(profile.getId(), versionId);
        if (STATUS_CONFIRMED.equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Confirmed visual strategy versions cannot be edited");
        }
        validateStrategyJson(strategyJson);
        version.setStrategyJson(strategyJson);
        return visualStrategyVersionRepository.save(version);
    }

    @Transactional
    public ProductVisualStrategyVersion confirm(Long profileId, Long versionId, Long userId) {
        ProductProfile profile = getAuthorizedProfile(profileId, userId);
        ProductVisualStrategyVersion version = getOwnedVersion(profile.getId(), versionId);
        validateStrategyJson(version.getStrategyJson());
        version.setStatus(STATUS_CONFIRMED);
        version.setConfirmedBy(userId);
        version.setConfirmedAt(LocalDateTime.now());
        return visualStrategyVersionRepository.save(version);
    }

    private ProductProfile getAuthorizedProfile(Long profileId, Long userId) {
        ProductProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product profile not found"));
        if (!profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to access this product profile");
        }
        return profile;
    }

    private ProductSellingPointCognitionVersion resolveCognition(Long profileId, Long cognitionVersionId) {
        ProductSellingPointCognitionVersion cognition;
        if (cognitionVersionId == null) {
            cognition = cognitionVersionRepository.findTopByProfileIdAndStatusOrderByVersionNumberDesc(profileId, STATUS_CONFIRMED)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Confirmed cognition version is required"));
        } else {
            cognition = cognitionVersionRepository.findById(cognitionVersionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Selling point cognition version not found"));
        }
        if (!profileId.equals(cognition.getProfileId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cognition version does not belong to this product profile");
        }
        return cognition;
    }

    private ProductVisualStrategyVersion getOwnedVersion(Long profileId, Long versionId) {
        ProductVisualStrategyVersion version = visualStrategyVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Visual strategy version not found"));
        if (!profileId.equals(version.getProfileId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Visual strategy version does not belong to this product profile");
        }
        return version;
    }

    private String generateLocalStrategyJson(ProductProfile profile, ProductSellingPointCognitionVersion cognition, Scope scope) {
        try {
            JsonNode cognitionRoot = objectMapper.readTree(cognition.getCognitionJson());
            ObjectNode root = objectMapper.createObjectNode();
            root.put("category", CATEGORY);
            root.put("category_strategy_version", CATEGORY_STRATEGY_VERSION);
            root.put("profile_id", profile.getId());
            if (cognition.getProfileVersionId() != null) root.put("profile_version_id", cognition.getProfileVersionId());
            root.put("cognition_version_id", cognition.getId());
            root.put("status", "draft");
            ArrayNode scopeArray = root.putArray("content_scope");
            if (scope.gallery()) scopeArray.add(SCOPE_GALLERY);
            if (scope.aplus()) scopeArray.add(SCOPE_APLUS);
            root.set("global_constraints", copyArray(cognitionRoot.path("global_constraints")));
            root.set("claims_to_avoid", copyArray(cognitionRoot.path("claims_to_avoid")));

            if (scope.gallery()) {
                ObjectNode gallery = root.putObject("gallery_strategy");
                gallery.set("images", buildGalleryImages(cognitionRoot));
            }
            if (scope.aplus()) {
                ObjectNode aplus = root.putObject("aplus_strategy");
                aplus.put("layout_type", "standard_modules");
                aplus.set("modules", buildAplusModules(cognitionRoot));
            }

            ObjectNode review = root.putObject("review");
            review.put("status", "needs_human_review");
            review.putArray("missing_assets");
            review.putArray("low_confidence_prompts");
            review.put("notes", "Generated by deterministic fixed car stereo strategy skeleton; review text and prompts before use.");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to parse cognition JSON");
        }
    }

    private ArrayNode buildGalleryImages(JsonNode cognitionRoot) {
        ArrayNode images = objectMapper.createArrayNode();
        addGalleryImage(images, cognitionRoot, 1, "why_buy", "comparison",
                "让买家相信老车可以升级成智能车机", "Make buyers believe their older vehicle can become a smarter cockpit.",
                List.of("compatibility", "display", "connection"));
        addGalleryImage(images, cognitionRoot, 2, "core_connection", "connection",
                "突出手机互联是最强差异点", "Show the strongest phone connection benefit.",
                List.of("connection"));
        addGalleryImage(images, cognitionRoot, 3, "screen_experience", "scenario",
                "展示大屏和清晰 UI 带来的使用体验", "Show how the large clear screen improves daily use.",
                List.of("display", "navigation"));
        addGalleryImage(images, cognitionRoot, 4, "safety_scene", "scenario",
                "展示倒车或通话等安全场景", "Show safer parking or hands-free driving scenarios.",
                List.of("safety"));
        addGalleryImage(images, cognitionRoot, 5, "entertainment_audio", "scenario",
                "展示音乐和娱乐体验", "Show a more enjoyable music and media experience.",
                List.of("audio", "connection"));
        addGalleryImage(images, cognitionRoot, 6, "compatibility_installation", "infographic",
                "降低买错和装错的风险", "Reduce fitment and installation risk before purchase.",
                List.of("compatibility", "installation"));
        return images;
    }

    private void addGalleryImage(ArrayNode images, JsonNode cognitionRoot, int slot, String role, String visualModel,
                                 String goalCn, String goalEn, List<String> preferredTypes) {
        List<JsonNode> selected = selectCognitions(cognitionRoot, preferredTypes, 3);
        ObjectNode image = images.addObject();
        image.put("slot", slot);
        image.put("role", role);
        image.put("visual_model", visualModel);
        image.put("goal_cn", goalCn);
        image.put("goal_en", goalEn);
        image.set("selected_cognition_ids", ids(selected));
        image.put("buyer_cognition_cn", firstText(selected, "buyer_cognition_cn", goalCn));
        image.put("buyer_cognition_en", firstText(selected, "buyer_cognition_en", goalEn));
        image.put("visual_structure_cn", galleryStructureCn(role));
        image.put("visual_structure_en", galleryStructureEn(role));
        image.set("required_visual_elements", visualElements(role));
        image.set("text_overlays_cn", textOverlays(role, true));
        image.set("text_overlays_en", textOverlays(role, false));
        image.put("prompt_cn", promptFor(role, true));
        image.put("prompt_en", promptFor(role, false));
        image.set("negative_constraints", copyArray(cognitionRoot.path("claims_to_avoid")));
        image.put("text_rendering_risk", "medium");
        image.set("evidence", mergeEvidence(selected));
    }

    private ArrayNode buildAplusModules(JsonNode cognitionRoot) {
        ArrayNode modules = objectMapper.createArrayNode();
        addAplusModule(modules, cognitionRoot, 1, "brand_banner", "scenario", List.of("connection", "display"));
        addAplusModule(modules, cognitionRoot, 2, "upgrade_story", "comparison", List.of("compatibility", "display", "connection"));
        addAplusModule(modules, cognitionRoot, 3, "core_features_grid", "infographic", List.of("connection", "display", "safety", "navigation"));
        addAplusModule(modules, cognitionRoot, 4, "driving_scenarios", "scenario", List.of("navigation", "audio", "safety"));
        addAplusModule(modules, cognitionRoot, 5, "compatibility_installation", "infographic", List.of("compatibility", "installation"));
        addAplusModule(modules, cognitionRoot, 6, "specs_package_support", "infographic", List.of("performance", "installation", "audio"));
        return modules;
    }

    private void addAplusModule(ArrayNode modules, JsonNode cognitionRoot, int index, String moduleType,
                                String visualModel, List<String> preferredTypes) {
        List<JsonNode> selected = selectCognitions(cognitionRoot, preferredTypes, 4);
        ObjectNode module = modules.addObject();
        module.put("module_index", index);
        module.put("module_type", moduleType);
        module.put("goal_cn", aplusGoal(moduleType, true));
        module.put("goal_en", aplusGoal(moduleType, false));
        module.set("selected_cognition_ids", ids(selected));
        module.put("visual_model", visualModel);
        module.put("headline_cn", aplusHeadline(moduleType, true));
        module.put("headline_en", aplusHeadline(moduleType, false));
        module.put("body_copy_cn", firstText(selected, "buyer_cognition_cn", aplusGoal(moduleType, true)));
        module.put("body_copy_en", firstText(selected, "buyer_cognition_en", aplusGoal(moduleType, false)));
        module.put("image_prompt_cn", "Amazon 标准 A+ 模块图片，真实车内场景，突出 " + aplusHeadline(moduleType, true) + "，干净高级，避免杂乱。");
        module.put("image_prompt_en", "Amazon standard A+ module image, realistic automotive scene, highlight " + aplusHeadline(moduleType, false) + ", clean premium product photography, no clutter.");
        ArrayNode assets = module.putArray("required_assets");
        assets.add("product_image");
        if (moduleType.contains("compatibility") || moduleType.contains("upgrade")) assets.add("installed_dashboard_image");
        module.set("text_overlays_cn", textOverlays(moduleType, true));
        module.set("text_overlays_en", textOverlays(moduleType, false));
        module.set("negative_constraints", copyArray(cognitionRoot.path("claims_to_avoid")));
        module.set("evidence", mergeEvidence(selected));
    }

    private List<JsonNode> selectCognitions(JsonNode cognitionRoot, List<String> preferredTypes, int max) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode all = cognitionRoot.path("buyer_cognitions");
        if (!all.isArray()) return result;
        for (String type : preferredTypes) {
            for (JsonNode item : all) {
                if (result.size() >= max) return result;
                if (!item.path("enabled").asBoolean(true)) continue;
                if (type.equals(item.path("type").asText())) result.add(item);
            }
        }
        for (JsonNode item : all) {
            if (result.size() >= max) return result;
            if (!item.path("enabled").asBoolean(true)) continue;
            if (!result.contains(item)) result.add(item);
        }
        return result;
    }

    private ArrayNode ids(List<JsonNode> selected) {
        ArrayNode ids = objectMapper.createArrayNode();
        selected.forEach(item -> ids.add(item.path("id").asText("")));
        return ids;
    }

    private ArrayNode mergeEvidence(List<JsonNode> selected) {
        ArrayNode evidence = objectMapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : selected) {
            JsonNode itemEvidence = item.path("evidence");
            if (!itemEvidence.isArray()) continue;
            for (JsonNode ev : itemEvidence) {
                String key = ev.path("source_path").asText("") + ev.path("source_text").asText("");
                if (seen.add(key)) evidence.add(ev);
            }
        }
        return evidence;
    }

    private String firstText(List<JsonNode> selected, String field, String fallback) {
        for (JsonNode item : selected) {
            String text = item.path(field).asText("");
            if (!text.isBlank()) return text;
        }
        return fallback;
    }

    private String galleryStructureCn(String role) {
        return switch (role) {
            case "why_buy" -> "左右对比：原车旧中控 vs 升级后智能大屏，中间用升级箭头连接。";
            case "core_connection" -> "手机、无线信号、车机屏幕三层连接结构，突出自动同步。";
            case "screen_experience" -> "车内驾驶视角，屏幕显示导航和应用 UI，突出清晰触控。";
            case "safety_scene" -> "真实倒车或通话场景，屏幕反馈清晰可见。";
            case "entertainment_audio" -> "车内音乐娱乐场景，屏幕和音频元素形成氛围。";
            default -> "产品与车型兼容信息图，使用标注线和简洁说明降低购买风险。";
        };
    }

    private String galleryStructureEn(String role) {
        return switch (role) {
            case "why_buy" -> "Side-by-side before and after dashboard comparison with an upgrade arrow.";
            case "core_connection" -> "Phone, wireless signal, and head unit screen layered as a clear connection flow.";
            case "screen_experience" -> "Cockpit driving view with navigation and app UI visible on the touchscreen.";
            case "safety_scene" -> "Real reversing or hands-free calling scene with clear screen feedback.";
            case "entertainment_audio" -> "In-car music and entertainment scene with screen and audio elements.";
            default -> "Compatibility and installation infographic with product, labels, and fitment notes.";
        };
    }

    private ArrayNode visualElements(String role) {
        ArrayNode elements = objectMapper.createArrayNode();
        switch (role) {
            case "why_buy" -> { elements.add("before dashboard"); elements.add("after touchscreen upgrade"); elements.add("upgrade arrow"); }
            case "core_connection" -> { elements.add("smartphone"); elements.add("wireless signal"); elements.add("car stereo screen UI"); }
            case "screen_experience" -> { elements.add("large touchscreen"); elements.add("navigation UI"); elements.add("cockpit view"); }
            case "safety_scene" -> { elements.add("rear camera view or calling UI"); elements.add("driver perspective"); }
            case "entertainment_audio" -> { elements.add("music UI"); elements.add("audio wave elements"); elements.add("dashboard screen"); }
            default -> { elements.add("fitment labels"); elements.add("vehicle model notes"); elements.add("installation diagram"); }
        }
        return elements;
    }

    private ObjectNode textOverlays(String role, boolean cn) {
        ObjectNode text = objectMapper.createObjectNode();
        if (cn) {
            text.put("headline", switch (role) {
                case "why_buy", "upgrade_story" -> "智能升级";
                case "core_connection" -> "无线互联";
                case "screen_experience" -> "清晰大屏";
                case "safety_scene" -> "安心驾驶";
                case "entertainment_audio" -> "畅享音乐";
                case "compatibility_installation" -> "确认适配";
                case "brand_banner" -> "智能车机升级";
                case "core_features_grid" -> "核心功能";
                case "driving_scenarios" -> "真实驾驶场景";
                case "specs_package_support" -> "参数与支持";
                default -> "产品卖点";
            });
            text.put("subhead", "可编辑图片文字");
        } else {
            text.put("headline", switch (role) {
                case "why_buy", "upgrade_story" -> "Smart Upgrade";
                case "core_connection" -> "Wireless Connection";
                case "screen_experience" -> "Clear Touchscreen";
                case "safety_scene" -> "Drive Safer";
                case "entertainment_audio" -> "Enjoy Your Music";
                case "compatibility_installation" -> "Check Fitment";
                case "brand_banner" -> "Smarter Dashboard Upgrade";
                case "core_features_grid" -> "Core Features";
                case "driving_scenarios" -> "Real Driving Scenarios";
                case "specs_package_support" -> "Specs & Support";
                default -> "Product Benefit";
            });
            text.put("subhead", "Editable image text");
        }
        text.putArray("badges");
        return text;
    }

    private String promptFor(String role, boolean cn) {
        if (cn) return "Amazon 高级车载电子产品副图，" + galleryStructureCn(role) + "，真实、清晰、干净，允许英文短文字覆盖，不要杂乱元素。";
        return "Amazon premium automotive electronics gallery image, " + galleryStructureEn(role) + " Realistic, clean, sharp lighting, allow short English text overlays, no clutter.";
    }

    private String aplusGoal(String moduleType, boolean cn) {
        if (cn) return switch (moduleType) {
            case "brand_banner" -> "建立品牌和产品第一印象";
            case "upgrade_story" -> "讲清老车升级智能车机的价值";
            case "core_features_grid" -> "集中展示核心功能";
            case "driving_scenarios" -> "展开真实驾驶使用场景";
            case "compatibility_installation" -> "降低适配和安装风险";
            default -> "展示参数、包装和售后支持";
        };
        return switch (moduleType) {
            case "brand_banner" -> "Build the first impression for the brand and product.";
            case "upgrade_story" -> "Explain the value of upgrading an older vehicle with a smart head unit.";
            case "core_features_grid" -> "Summarize the most important product features.";
            case "driving_scenarios" -> "Show real driving use cases in detail.";
            case "compatibility_installation" -> "Reduce fitment and installation risk.";
            default -> "Show specs, package contents, and support.";
        };
    }

    private String aplusHeadline(String moduleType, boolean cn) {
        return textOverlays(moduleType, cn).path("headline").asText();
    }

    private ArrayNode copyArray(JsonNode source) {
        ArrayNode out = objectMapper.createArrayNode();
        if (source != null && source.isArray()) {
            source.forEach(out::add);
        }
        return out;
    }

    private Scope normalizeScope(List<String> contentScope) {
        if (contentScope == null || contentScope.isEmpty()) return new Scope(true, true);
        Set<String> normalized = new LinkedHashSet<>();
        for (String item : contentScope) {
            if (item == null) continue;
            String value = item.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank()) normalized.add(value);
        }
        boolean gallery = normalized.contains(SCOPE_GALLERY);
        boolean aplus = normalized.contains(SCOPE_APLUS);
        if (!gallery && !aplus) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "content_scope must contain gallery, aplus, or both");
        }
        return new Scope(gallery, aplus);
    }

    private void validateStrategyJson(String strategyJson) {
        if (strategyJson == null || strategyJson.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Visual strategy JSON cannot be empty");
        }
        try {
            JsonNode root = objectMapper.readTree(strategyJson);
            if (!root.path("content_scope").isArray()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Visual strategy JSON must contain content_scope array");
            }
            boolean hasGallery = root.has("gallery_strategy");
            boolean hasAplus = root.has("aplus_strategy");
            if (!hasGallery && !hasAplus) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Visual strategy JSON must contain gallery_strategy or aplus_strategy");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid visual strategy JSON");
        }
    }

    private record Scope(boolean gallery, boolean aplus) {
        String storageValue() {
            if (gallery && aplus) return "gallery+aplus";
            return gallery ? SCOPE_GALLERY : SCOPE_APLUS;
        }
    }
}