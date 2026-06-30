package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import cafe.snails.ecomagents.model.ProductVisualStrategyVersion;
import cafe.snails.ecomagents.repository.ProductProfileRepository;
import cafe.snails.ecomagents.repository.ProductSellingPointCognitionVersionRepository;
import cafe.snails.ecomagents.repository.ProductVisualStrategyVersionRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cafe.snails.ecomagents.model.AiModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import java.util.Collections;
import java.util.Map;

@Service
@Slf4j
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

    @Autowired(required = false)
    private LlmService llmService;

    @Autowired(required = false)
    private AiModelRepository aiModelRepository;

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

    /**
     * 调用 LLM 分析卖点分布，设计 gallery 和 A+ 的策略编排（1 次请求）。
     * 返回 {gallery: [...], aplus: [...]} 的 JsonNode，LLM 不可用时返回 null。
     */
    private JsonNode designVisualStrategy(JsonNode cognitionRoot) {
        if (llmService == null) return null;
        GenerateOptions modelOptions = buildDefaultModelOptions();
        if (modelOptions == null) return null;

        try {
            JsonNode cognitions = cognitionRoot.path("buyer_cognitions");
            if (!cognitions.isArray() || cognitions.isEmpty()) return null;

            String systemPrompt = """
                    你是一名 Amazon US car stereo 视觉营销策略师。
                    以下是该产品已确认的卖点认知列表，请为 6 张 Amazon 副图（gallery）和 6 个 A+ 模块设计视觉策略。

                    已知卖点类型：兼容性、连接方式、屏幕显示、安全保障、导航功能、音频娱乐、性能配置、安装服务
                    已知画面角色：why_buy, core_connection, screen_experience, safety_scene, entertainment_audio, compatibility_installation, feature_spotlight, usage_scene
                    已知 A+ 模块类型：brand_banner, upgrade_story, core_features_grid, driving_scenarios, compatibility_installation, specs_package_support, feature_showcase

                    请分析卖点分布强度，为 gallery 和 aplus 分别输出策略编排 JSON。
                    只使用已知的 role/type，focus_on 必须是已知卖点类型或 null（跳过），同一 focus_on 可在多个 slot 中使用。
                    gallery 最多 6 项，aplus 最多 6 项。

                    每个 gallery slot 除了 role / focus_on / goal_cn / goal_en 外，必须包含 prompt_cn 和 prompt_en 字段。
                    prompt 遵循以下固定结构，每段用【 】标题开头，中英文各写一份：

                    【目标】这一张图要让用户相信什么
                    【主体】产品 + 关键元素
                    【场景】车内真实使用场景
                    【画面结构】明确左右/前后/层级关系
                    【风格】专业汽车产品摄影，超写实，景深适中
                    【光线】柔光箱车内照明，主光源从挡风玻璃方向照射，自然漫反射
                    【文字及排版】文字内容和位置要求
                    【约束】禁止文字遮挡产品主体，禁止杂乱背景元素，禁止水印

                    每个 A+ module 除了 type / focus_on 外，必须包含 image_prompt_cn 和 image_prompt_en 字段，同样按上述结构编写。

                    输出合法 JSON，不要 markdown 包裹。""";

            String userJson = "{\"cognitions\": " + cognitions.toString() + "}";
            List<Map<String, String>> history = List.of(Map.of("role", "user", "content", userJson));

            String response = llmService.syncChat(systemPrompt, history, modelOptions);
            if (response == null || response.isBlank()) {
                log.warn("LLM strategy design returned empty response, falling back to fixed template");
                return null;
            }

            log.debug("LLM strategy design response length: {}", response.length());

            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.warn("LLM strategy design failed, falling back to fixed template: {}", e.getMessage());
            return null;
        }
    }

    private GenerateOptions buildDefaultModelOptions() {
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
        var execConfig = ExecutionConfig.builder()
                .timeout(java.time.Duration.ofSeconds(30))
                .maxAttempts(1)
                .build();
        return GenerateOptions.builder()
                .modelName(model.getModelName())
                .apiKey(model.getApiKey())
                .baseUrl(AiModelService.extractBaseUrl(model.getApiUrl()))
                .endpointPath(AiModelService.buildEndpointPath(model))
                .temperature(resolvedTemp)
                .maxTokens(resolvedMaxTokens)
                .executionConfig(execConfig)
                .build();
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

            // LLM 编排策略设计（不可用时回退到固定模板）
            JsonNode design = designVisualStrategy(cognitionRoot);

            if (scope.gallery()) {
                ObjectNode gallery = root.putObject("gallery_strategy");
                gallery.set("images", buildGalleryImages(cognitionRoot, design));
            }
            if (scope.aplus()) {
                ObjectNode aplus = root.putObject("aplus_strategy");
                aplus.put("layout_type", "standard_modules");
                aplus.set("modules", buildAplusModules(cognitionRoot, design));
            }

            ObjectNode review = root.putObject("review");
            review.put("status", "needs_human_review");
            review.putArray("missing_assets");
            review.putArray("low_confidence_prompts");
            review.put("notes", "Generated by cognition-driven strategy engine with LLM design; review text and prompts before use.");
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

    private ArrayNode buildGalleryImages(JsonNode cognitionRoot, JsonNode design) {
        if (design == null) return buildGalleryImages(cognitionRoot); // 回退到固定模板
        JsonNode galleryDesign = design.path("gallery");
        if (!galleryDesign.isArray() || galleryDesign.isEmpty()) return buildGalleryImages(cognitionRoot);
        ArrayNode images = objectMapper.createArrayNode();
        int slot = 1;
        for (JsonNode slotDesign : galleryDesign) {
            String role = slotDesign.path("role").asText("");
            if (role.isBlank()) continue;
            String focusOn = slotDesign.path("focus_on").isNull() ? null : slotDesign.path("focus_on").asText();
            if (focusOn == null) continue; // focus_on: null = 跳过
            String goalCn = slotDesign.path("goal_cn").asText(galleryGoal(role, true));
            String goalEn = slotDesign.path("goal_en").asText(galleryGoal(role, false));
            String visualModel = galleryVisualModel(role);
            addGalleryImage(images, cognitionRoot, slot++, role, visualModel, goalCn, goalEn, List.of(focusOn));
            // 用 LLM 生成的详细 prompt 覆写模板 prompt
            if (slotDesign.has("prompt_cn") || slotDesign.has("prompt_en")) {
                ObjectNode lastImage = (ObjectNode) images.get(images.size() - 1);
                setIfPresent(lastImage, "prompt_cn", slotDesign, "prompt_cn");
                setIfPresent(lastImage, "prompt_en", slotDesign, "prompt_en");
            }
        }
        // 用固定模板补充到 6 个
        if (images.size() < 6) {
            ArrayNode fallbackImages = buildGalleryImages(cognitionRoot);
            for (int i = 0; i < fallbackImages.size() && images.size() < 6; i++) {
                images.add(fallbackImages.get(i));
            }
        }
        return images;
    }

    /** 从 role 获取默认 goal_cn/en（供 LLM 未提供 goal 时兜底）。 */
    private String galleryGoal(String role, boolean cn) {
        if (cn) return switch (role) {
            case "why_buy" -> "让买家相信老车可以升级成智能车机";
            case "core_connection" -> "突出手机互联是最强差异点";
            case "screen_experience" -> "展示大屏和清晰 UI 带来的使用体验";
            case "safety_scene" -> "展示倒车或通话等安全场景";
            case "entertainment_audio" -> "展示音乐和娱乐体验";
            case "compatibility_installation" -> "降低买错和装错的风险";
            case "feature_spotlight" -> "突出核心性能和做工品质";
            case "usage_scene" -> "展示真实车内使用场景";
            default -> "展示产品核心卖点";
        };
        return switch (role) {
            case "why_buy" -> "Make buyers believe their older vehicle can become a smarter cockpit.";
            case "core_connection" -> "Show the strongest phone connection benefit.";
            case "screen_experience" -> "Show how the large clear screen improves daily use.";
            case "safety_scene" -> "Show safer parking or hands-free driving scenarios.";
            case "entertainment_audio" -> "Show a more enjoyable music and media experience.";
            case "compatibility_installation" -> "Reduce fitment and installation risk before purchase.";
            case "feature_spotlight" -> "Highlight core performance and build quality.";
            case "usage_scene" -> "Show real in-car usage.";
            default -> "Showcase the key selling points.";
        };
    }

    /** 从 role 获取默认 visualModel。 */
    private String galleryVisualModel(String role) {
        return switch (role) {
            case "why_buy" -> "comparison";
            case "core_connection" -> "connection";
            case "feature_spotlight", "compatibility_installation" -> "infographic";
            default -> "scenario";
        };
    }

    private String aplusVisualModel(String moduleType) {
        return switch (moduleType) {
            case "upgrade_story" -> "comparison";
            case "core_features_grid", "compatibility_installation", "specs_package_support", "feature_showcase" -> "infographic";
            default -> "scenario";
        };
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

    private ArrayNode buildAplusModules(JsonNode cognitionRoot, JsonNode design) {
        if (design == null) return buildAplusModules(cognitionRoot); // 回退到固定模板
        JsonNode aplusDesign = design.path("aplus");
        if (!aplusDesign.isArray() || aplusDesign.isEmpty()) return buildAplusModules(cognitionRoot);
        ArrayNode modules = objectMapper.createArrayNode();
        int index = 1;
        for (JsonNode moduleDesign : aplusDesign) {
            String moduleType = moduleDesign.path("type").asText("");
            if (moduleType.isBlank()) continue;
            String focusOn = moduleDesign.path("focus_on").isNull() ? null : moduleDesign.path("focus_on").asText();
            if (focusOn == null) continue;
            String visualModel = aplusVisualModel(moduleType);
            addAplusModule(modules, cognitionRoot, index++, moduleType, visualModel, List.of(focusOn));
            // 用 LLM 生成的详细 prompt 覆写模板 prompt
            if (moduleDesign.has("image_prompt_cn") || moduleDesign.has("image_prompt_en")) {
                ObjectNode lastModule = (ObjectNode) modules.get(modules.size() - 1);
                setIfPresent(lastModule, "image_prompt_cn", moduleDesign, "image_prompt_cn");
                setIfPresent(lastModule, "image_prompt_en", moduleDesign, "image_prompt_en");
            }
        }
        // 补充到 6 个
        if (modules.size() < 6) {
            ArrayNode fallbackModules = buildAplusModules(cognitionRoot);
            for (int i = 0; i < fallbackModules.size() && modules.size() < 6; i++) {
                modules.add(fallbackModules.get(i));
            }
        }
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
                String cognitionType = item.path("type").asText("");
                String cognitionTypeEn = chineseToEnglishType(cognitionType);
                if (type.equals(cognitionType) || type.equals(cognitionTypeEn)) result.add(item);
            }
        }
        for (JsonNode item : all) {
            if (result.size() >= max) return result;
            if (!item.path("enabled").asBoolean(true)) continue;
            if (!result.contains(item)) result.add(item);
        }
        return result;
    }

    /**
     * 按中文 focus_on 类型精确匹配 cognition。用于 LLM 编排结果。
     * focusOn = null 或空时返回空列表（跳过 slot）。
     */
    private List<JsonNode> selectCognitionsByFocus(JsonNode cognitionRoot, String focusOn, int max) {
        List<JsonNode> result = new ArrayList<>();
        if (focusOn == null || focusOn.isBlank()) return result;
        JsonNode all = cognitionRoot.path("buyer_cognitions");
        if (!all.isArray()) return result;
        for (JsonNode item : all) {
            if (result.size() >= max) return result;
            if (!item.path("enabled").asBoolean(true)) continue;
            if (focusOn.equals(item.path("type").asText())) result.add(item);
        }
        // Fallback: 如果按 type 配不到，取任意 enabled cognition
        for (JsonNode item : all) {
            if (result.size() >= max) return result;
            if (!item.path("enabled").asBoolean(true)) continue;
            if (!result.contains(item)) result.add(item);
        }
        return result;
    }

    /** 中文 type → 英文 type（用于双语匹配）。 */
    private String chineseToEnglishType(String type) {
        return switch (type) {
            case "兼容性" -> "compatibility";
            case "连接方式" -> "connection";
            case "屏幕显示" -> "display";
            case "安全保障" -> "safety";
            case "导航功能" -> "navigation";
            case "音频娱乐" -> "audio";
            case "性能配置" -> "performance";
            case "安装服务" -> "installation";
            default -> type;
        };
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
            case "feature_spotlight" -> "产品核心部件特写，突出做工和设计质感，使用标注说明关键技术和参数。";
            case "usage_scene" -> "真实驾驶场景，展示产品在车内的完整使用状态，驾驶视角。";
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
            case "feature_spotlight" -> "Close-up of core product components, highlighting build quality and key tech specs with callouts.";
            case "usage_scene" -> "Real in-car usage scene, showing the product in its full installed state from the driver's perspective.";
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
            case "feature_spotlight" -> { elements.add("product close-up"); elements.add("tech spec callouts"); elements.add("build quality details"); }
            case "usage_scene" -> { elements.add("installed dashboard view"); elements.add("driver perspective"); elements.add("screen UI"); }
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
                case "feature_spotlight" -> "性能核心";
                case "usage_scene" -> "真实体验";
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
                case "feature_spotlight" -> "Core Performance";
                case "usage_scene" -> "Real Experience";
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
            case "feature_showcase" -> "深度展示产品核心性能亮点";
            default -> "展示参数、包装和售后支持";
        };
        return switch (moduleType) {
            case "brand_banner" -> "Build the first impression for the brand and product.";
            case "upgrade_story" -> "Explain the value of upgrading an older vehicle with a smart head unit.";
            case "core_features_grid" -> "Summarize the most important product features.";
            case "driving_scenarios" -> "Show real driving use cases in detail.";
            case "compatibility_installation" -> "Reduce fitment and installation risk.";
            case "feature_showcase" -> "Showcase the core performance highlights of the product.";
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

    /** 如果 source node 中存在 field，写入 target node。 */
    private void setIfPresent(ObjectNode target, String field, JsonNode source, String sourceField) {
        JsonNode value = source.path(sourceField);
        if (value.isTextual() && !value.asText().isBlank()) {
            target.put(field, value.asText());
        }
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