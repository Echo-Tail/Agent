# 视觉策略 LLM 编排 — 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 subagent-driven-development（推荐）或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** VisualStrategyService 从固定 6 slot 模板改为卖点数据驱动：1 次 LLM 调用分析 cognition 卖点分布，动态决定每张图/A+ 模块主打什么卖点。

**架构：**
- VisualStrategyService 注入 LlmService + AiModelRepository（与 SellingPointCognitionService 相同模式）
- 新增 `designVisualStrategy()` 方法发 1 次 LLM 请求，返回策略编排 JSON
- `generateLocalStrategyJson` 按编排结果动态分配 slot 角色，LLM 不可用时回退到固定模板
- 新增 `selectCognitionsByFocus()` 按中文 type 匹配 cognition（修复现有 selectCognitions 的英文 vs 中文 bug）
- 模板代码不变（galleryStructureCn/En、textOverlays、promptFor 等），新 role 补充 switch 分支

**技术栈：** Spring Boot, Jackson, AgentScope Java SDK (LlmService), Vue 3 + Axios

---

## 文件清单

### 修改

| 文件 | 职责 |
|---|---|
| `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java` | 新增 LLM 编排、selectCognitionsByFocus、新 role 模板 |
| `EcomAgents/src/test/java/cafe/snails/ecomagents/service/VisualStrategyServiceTest.java` | 更新测试数据、覆盖新路径 |
| `ShadcnAgentUI/src/api/product-profiles.ts` | generateVisualStrategy 增加 timeout |

---

### 任务 1：VisualStrategyService 注入 LlmService + AiModelRepository

**文件：** `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java`

- [ ] **步骤 1：添加 import 和字段**

在 `@Service @RequiredArgsConstructor` 类上添加：

```java
import org.springframework.beans.factory.annotation.Autowired;
import cafe.snails.ecomagents.repository.AiModelRepository;
// LlmService 已引入（如果不存在则加 import）
```

并在 `private final ObjectMapper objectMapper;` 后添加：

```java
@Autowired(required = false)
private LlmService llmService;

@Autowired(required = false)
private AiModelRepository aiModelRepository;
```

> 注意：`@RequiredArgsConstructor` 只注入 final 字段，所以 `llmService` 和 `aiModelRepository` 必须用 `@Autowired` 而非构造器注入。

- [ ] **步骤 2：编译验证**

```bash
cd EcomAgents && cmd.exe /c "set \"JAVA_HOME=D:\Program Files\jdk-17.0.2\" && gradle compileJava"
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 3：更新测试构造器**

测试中 `service = new VisualStrategyService(...)` 不需要改参数，因为 `llmService` 和 `aiModelRepository` 是 `@Autowired(required=false)`，不传时为 null。

- [ ] **步骤 4：运行现有测试验证不破坏**

```bash
cd EcomAgents && cmd.exe /c "set \"JAVA_HOME=D:\Program Files\jdk-17.0.2\" && gradle test --tests *VisualStrategyServiceTest*"
```

预期：通过

---

### 任务 2：补充新 role 模板（feature_spotlight、usage_scene、feature_showcase）

**文件：** `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java`

在 5 个 switch 方法中补充新 role 分支：`galleryStructureCn`、`galleryStructureEn`、`visualElements`、`textOverlays`、`promptFor`、`aplusGoal`。

- [ ] **步骤 1：galleryStructureCn 补充**

```java
case "feature_spotlight" -> "产品核心部件特写，突出做工和设计质感，使用标注说明关键技术和参数。";
case "usage_scene" -> "真实驾驶场景，展示产品在车内的完整使用状态，驾驶视角。";
```

- [ ] **步骤 2：galleryStructureEn 补充**

```java
case "feature_spotlight" -> "Close-up of core product components, highlighting build quality and key tech specs with callouts.";
case "usage_scene" -> "Real in-car usage scene, showing the product in its full installed state from the driver's perspective.";
```

- [ ] **步骤 3：visualElements 补充**

```java
case "feature_spotlight" -> { elements.add("product close-up"); elements.add("tech spec callouts"); elements.add("build quality details"); }
case "usage_scene" -> { elements.add("installed dashboard view"); elements.add("driver perspective"); elements.add("screen UI"); }
```

- [ ] **步骤 4：textOverlays 补充**

在中文 headline switch 补充：
```java
case "feature_spotlight" -> "性能核心";
case "usage_scene" -> "真实体验";
```

在英文 headline switch 补充：
```java
case "feature_spotlight" -> "Core Performance";
case "usage_scene" -> "Real Experience";
```

- [ ] **步骤 5：aplusGoal 补充**

在中文 switch 补充：
```java
case "feature_showcase" -> "深度展示产品核心性能亮点";
```

在英文 switch 补充：
```java
case "feature_showcase" -> "Showcase the core performance highlights of the product.";
```

- [ ] **步骤 6：编译验证**

```bash
cd EcomAgents && cmd.exe /c "set \"JAVA_HOME=D:\Program Files\jdk-17.0.2\" && gradle compileJava"
```

---

### 任务 3：新增 designVisualStrategy 方法

**文件：** `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java`

在 `generateLocalStrategyJson` 之前新增。

- [ ] **步骤 1：添加 import**

```java
import java.util.Map;
import java.util.Collections;
import io.agentscope.core.model.GenerateOptions;
```

- [ ] **步骤 2：编写 designVisualStrategy 方法**

```java
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
                输出合法 JSON 数组格式，不要 markdown 包裹。""";

        String userJson = "{\"cognitions\": " + cognitions.toString() + "}";
        List<Map<String, String>> history = List.of(Map.of("role", "user", "content", userJson));

        String response = llmService.syncChat(systemPrompt, history, modelOptions);
        if (response == null || response.isBlank()) {
            log.warn("LLM strategy design returned empty response, falling back to fixed template");
            return null;
        }

        log.debug("LLM strategy design response length: {}", response.length());

        JsonNode root = objectMapper.readTree(response);
        return root;
    } catch (Exception e) {
        log.warn("LLM strategy design failed, falling back to fixed template: {}", e.getMessage());
        return null;
    }
}
```

- [ ] **步骤 3：复制 buildDefaultModelOptions 方法**

从 SellingPointCognitionService 复制 `buildDefaultModelOptions()` 方法（完全相同的逻辑），及 `stringOrDefault()` 辅助方法。或者抽取为 shared util，但为了避免过度抽象，直接复制。

```java
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
    int resolvedMaxTokens = model.getMaxTokens() != null && model.getMaxTokens() > 0
            ? Math.min(model.getMaxTokens(), 4096) : 4096;
    double resolvedTemp = model.getTemperature() != null ? model.getTemperature() : 0.3;
    log.info("Using model: name={}, modelName={}, maxTokens={}, temperature={}",
            model.getName(), model.getModelName(), resolvedMaxTokens, resolvedTemp);
    var execConfig = io.agentscope.core.model.ExecutionConfig.builder()
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
```

需要添加 import：
```java
import cafe.snails.ecomagents.model.AiModel;
```

- [ ] **步骤 4：添加 log 字段**

在类上添加 `@Slf4j` 注解（如果不存在）：
```java
import lombok.extern.slf4j.Slf4j;
```

- [ ] **步骤 5：编译验证**

```bash
cd EcomAgents && cmd.exe /c "set \"JAVA_HOME=D:\Program Files\jdk-17.0.2\" && gradle compileJava"
```

---

### 任务 4：新增 selectCognitionsByFocus 方法

**文件：** `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java`

- [ ] **步骤 1：编写 selectCognitionsByFocus**

```java
/**
 * 按中文 focus_on 类型精确匹配 cognition。用于 LLM 编排结果。
 * focusOn = null 时返回空列表（跳过 slot）。
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
```

- [ ] **步骤 2：编译验证**

---

### 任务 5：修改 generateLocalStrategyJson 使用 LLM 编排

**文件：** `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java`

- [ ] **步骤 1：修改 generateLocalStrategyJson**

在 `generateLocalStrategyJson` 中，解析 cognitionRoot 后，调用 `designVisualStrategy()`。

如果返回非空，用编排结果驱动 slot 分配；否则走原有固定模板。

```java
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

        // LLM 编排
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
```

- [ ] **步骤 2：新增 buildGalleryImages 重载**

```java
private ArrayNode buildGalleryImages(JsonNode cognitionRoot, JsonNode design) {
    if (design == null) return buildGalleryImages(cognitionRoot); // 回退到固定模板
    ArrayNode images = objectMapper.createArrayNode();
    JsonNode galleryDesign = design.path("gallery");
    if (!galleryDesign.isArray() || galleryDesign.isEmpty()) return buildGalleryImages(cognitionRoot);
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
    }
    // 如果 LLM 设计的 slot 太少，用固定模板补充到 6 个
    if (slot <= 6) {
        ArrayNode fallbackImages = buildGalleryImages(cognitionRoot);
        for (int i = slot - 1; i < fallbackImages.size() && images.size() < 6; i++) {
            images.add(fallbackImages.get(i));
        }
    }
    return images;
}
```

- [ ] **步骤 3：新增 buildAplusModules 重载**

```java
private ArrayNode buildAplusModules(JsonNode cognitionRoot, JsonNode design) {
    if (design == null) return buildAplusModules(cognitionRoot); // 回退到固定模板
    ArrayNode modules = objectMapper.createArrayNode();
    JsonNode aplusDesign = design.path("aplus");
    if (!aplusDesign.isArray() || aplusDesign.isEmpty()) return buildAplusModules(cognitionRoot);
    int index = 1;
    for (JsonNode moduleDesign : aplusDesign) {
        String moduleType = moduleDesign.path("type").asText("");
        if (moduleType.isBlank()) continue;
        String focusOn = moduleDesign.path("focus_on").isNull() ? null : moduleDesign.path("focus_on").asText();
        if (focusOn == null) continue;
        String visualModel = aplusVisualModel(moduleType);
        addAplusModule(modules, cognitionRoot, index++, moduleType, visualModel, List.of(focusOn));
    }
    // 补充到 6 个
    if (index <= 6) {
        ArrayNode fallbackModules = buildAplusModules(cognitionRoot);
        for (int i = index - 1; i < fallbackModules.size() && modules.size() < 6; i++) {
            modules.add(fallbackModules.get(i));
        }
    }
    return modules;
}
```

- [ ] **步骤 4：新增辅助方法**

```java
/** 从 role 获取默认 goal_cn（供 LLM 未提供 goal 时兜底）。 */
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
```

- [ ] **步骤 5：编译验证**

```bash
cd EcomAgents && cmd.exe /c "set \"JAVA_HOME=D:\Program Files\jdk-17.0.2\" && gradle compileJava"
```

---

### 任务 6：修改 addGalleryImage / addAplusModule 接受单个 focus_on

**文件：** `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java`

- [ ] **步骤 1：修改 addGalleryImage 内的 selectCognitions 调用**

`addGalleryImage` 方法签名不变，但需要修改 `selectCognitions` 调用。

当前的 `selectCognitions(cognitionRoot, preferredTypes, 3)` 中，`preferredTypes` 传的是 `List.of("compatibility", "display")` 英文值。

但对于 LLM 编排路径，传入的 `preferredTypes` 是 `List.of(focusOn)` 其中 focusOn 是中文值。

我们需要修改 `addGalleryImage` 内部的匹配逻辑：如果 preferredTypes 中的值是中文，就直接匹配中文 type。

或者更简单：在 `addGalleryImage` 中保持调用 `selectCognitions` 但修改 `selectCognitions` 来同时匹配中英文。

最简单的方案：**修改 `selectCognitions` 使其同时支持中英文 type 匹配**。

```java
private List<JsonNode> selectCognitions(JsonNode cognitionRoot, List<String> preferredTypes, int max) {
    List<JsonNode> result = new ArrayList<>();
    JsonNode all = cognitionRoot.path("buyer_cognitions");
    if (!all.isArray()) return result;
    for (String type : preferredTypes) {
        for (JsonNode item : all) {
            if (result.size() >= max) return result;
            if (!item.path("enabled").asBoolean(true)) continue;
            // 支持中英文 type 匹配
            String cognitionType = item.path("type").asText("");
            if (cognitionType.equals(type) || englishToChineseType(type).equals(cognitionType) || chineseToEnglishType(type).equals(cognitionType)) {
                result.add(item);
            }
        }
    }
    for (JsonNode item : all) {
        if (result.size() >= max) return result;
        if (!item.path("enabled").asBoolean(true)) continue;
        if (!result.contains(item)) result.add(item);
    }
    return result;
}
```

以及中英文映射辅助方法：

```java
private String englishToChineseType(String type) {
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
```

- [ ] **步骤 2：编译验证**

---

### 任务 7：更新测试

**文件：** `EcomAgents/src/test/java/cafe/snails/ecomagents/service/VisualStrategyServiceTest.java`

- [ ] **步骤 1：更新测试数据的 cognitionJson type 为中文**

测试数据的 `cognitionJson()` 中 `type` 字段从英文改为中文，以匹配真实数据：

```java
private String cognitionJson() {
    return """
            {
              "category": "car_stereo",
              "category_strategy_version": "car_stereo_v1",
              "buyer_cognitions": [
                {"id":"compatibility","enabled":true,"priority":1,"type":"兼容性","visual_model":"infographic","buyer_cognition_cn":"确认车型和 Manual AC 后再购买","buyer_cognition_en":"Confirm vehicle fitment and Manual AC before purchase.","evidence":[{"source_path":"compatibility.vehicle_fitment","source_text":"Dodge RAM 2013-2018 Manual AC only"}]},
                {"id":"wireless_carplay","enabled":true,"priority":2,"type":"连接方式","visual_model":"connection","buyer_cognition_cn":"上车连接手机应用","buyer_cognition_en":"Connect phone apps to the dashboard screen.","evidence":[{"source_path":"amazon_listing.bullet_points[1]","source_text":"Wireless CarPlay and Android Auto"}]},
                {"id":"qled_screen","enabled":true,"priority":3,"type":"屏幕显示","visual_model":"scenario","buyer_cognition_cn":"大屏导航更清晰","buyer_cognition_en":"A clearer touchscreen makes navigation easier.","evidence":[{"source_path":"amazon_listing.bullet_points[2]","source_text":"9 inch QLED touchscreen"}]},
                {"id":"backup_camera","enabled":true,"priority":4,"type":"安全保障","visual_model":"scenario","buyer_cognition_cn":"倒车更安心","buyer_cognition_en":"Rear view support makes parking feel safer.","evidence":[{"source_path":"amazon_listing.bullet_points[3]","source_text":"Supports backup camera input"}]}
              ],
              "global_constraints": ["Only show Manual AC compatibility."],
              "claims_to_avoid": ["Do not claim automatic AC compatibility."]
            }
            """;
}
```

- [ ] **步骤 2：运行测试验证通过（LLM 不可用，应回退到固定模板，输出仍然是 6 slot）**

```bash
cd EcomAgents && cmd.exe /c "set \"JAVA_HOME=D:\Program Files\jdk-17.0.2\" && gradle test --tests *VisualStrategyServiceTest*"
```

预期：所有测试通过（LLM 不可用 → `designVisualStrategy` 返回 null → 回退到原有固定模板）

- [ ] **步骤 3：验证 generate_shouldCreateDefaultGalleryAndAplusDraftFromConfirmedCognition 输出正确**

该测试验证 gallery 有 6 images、aplus 有 6 modules，且 prompt 包含 "Amazon premium"。LLM 不可用时回退到固定模板，这些断言仍然成立。

---

### 任务 8：前端增加 generateVisualStrategy timeout

**文件：** `ShadcnAgentUI/src/api/product-profiles.ts`

- [ ] **步骤 1：修改 generateVisualStrategy 增加 timeout**

```typescript
export function generateVisualStrategy(id: number, data?: { cognition_version_id?: number | null; cognitionVersionId?: number | null; content_scope?: string[]; contentScope?: string[] }) {
  return api.post<VisualStrategyVersion>(`/product-profiles/${id}/visual-strategies/generate`, data ?? {}, {
    timeout: 120_000,
  })
}
```

- [ ] **步骤 2：TypeScript 编译验证**

```bash
cd ShadcnAgentUI && npx vue-tsc --noEmit 2>&1 | head -5
```

预期：仅 baseUrl deprecation 警告，无报错。

---

### 任务 9：最终验证

- [ ] **步骤 1：运行所有相关测试**

```bash
cd EcomAgents && cmd.exe /c "set \"JAVA_HOME=D:\Program Files\jdk-17.0.2\" && gradle test --tests *VisualStrategyServiceTest* --tests *SellingPointCognitionServiceTest* --tests *ProductProfileControllerVisualStrategyTest* --tests *ProductProfileControllerSellingPointCognitionTest* --tests *ProductProfileControllerTest*"
```

- [ ] **步骤 2：确认所有测试通过**

预期：BUILD SUCCESSFUL
