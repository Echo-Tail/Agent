package cafe.snails.ecomagents.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Prompt 组合引擎 — 将三层输入模型组合为最终结构化 brief 和自然语言 prompt。
 *
 * <p>三层输入参与规则：
 * <ul>
 *   <li><b>目标产品事实（productFactsJson）</b> — 默认参与，按图片类型自动筛选相关字段</li>
 *   <li><b>图片表达结构（imageExpressionJson）</b> — 用户选择的单个表达结构参与</li>
 *   <li><b>素材事实（sourceMaterialFactsJson）</b> — 仅在 {@code checkedMaterialFactKeys} 中出现的字段参与</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptCompositionService {

    private final ObjectMapper objectMapper;

    /**
     * 组合三层输入，生成结构化 brief 和最终自然语言 prompt。
     *
     * @param productFactsJson         目标产品事实 JSON（来自产品资料版本）
     * @param imageExpressionJson      图片表达结构 JSON（用户选择的分析结果）
     * @param sourceMaterialFactsJson  素材事实 JSON（来自 ASIN/URL 分析）
     * @param checkedMaterialFactKeys  逗号分隔的已勾选素材事实键名
     * @param imageType                图片类型（用于自动筛选产品事实字段）
     * @return 包含 structuredBrief 和 finalPrompt 的 composition result
     */
    public PromptCompositionResult compose(String productFactsJson,
                                           String imageExpressionJson,
                                           String sourceMaterialFactsJson,
                                           String checkedMaterialFactKeys,
                                           String imageType) {
        StringBuilder brief = new StringBuilder();
        StringBuilder prompt = new StringBuilder();

        // --- Step 1: Image Expression Structure (always primary) ---
        JsonNode expression = parseSafe(imageExpressionJson);
        if (expression != null) {
            appendExpressionToBrief(brief, expression);
            appendExpressionToPrompt(prompt, expression);
        }

        // --- Step 2: Target Product Facts (filtered by imageType) ---
        JsonNode facts = parseSafe(productFactsJson);
        if (facts != null) {
            appendFactsToBrief(brief, facts, imageType);
            appendFactsToPrompt(prompt, facts, imageType);
        }

        // --- Step 3: Source Material Facts (only checked keys) ---
        Set<String> checkedKeys = parseCheckedKeys(checkedMaterialFactKeys);
        if (!checkedKeys.isEmpty()) {
            JsonNode materialFacts = parseSafe(sourceMaterialFactsJson);
            if (materialFacts != null) {
                appendMaterialFactsToBrief(brief, materialFacts, checkedKeys);
                appendMaterialFactsToPrompt(prompt, materialFacts, checkedKeys);
            }
        }

        return new PromptCompositionResult(
                brief.toString().trim(),
                prompt.toString().trim()
        );
    }

    // --- Brief generation ---

    private void appendExpressionToBrief(StringBuilder brief, JsonNode expr) {
        brief.append("## 图片表达结构\n\n");
        appendField(brief, "目标用途", expr.get("intended_use"));
        appendField(brief, "图片类型", expr.get("image_type"));

        JsonNode scene = expr.get("scene");
        if (scene != null && !scene.isEmpty()) {
            brief.append("### 场景设定\n");
            appendField(brief, "- 背景", scene.get("background"));
            appendField(brief, "- 环境", scene.get("environment"));
            appendField(brief, "- 灯光", scene.get("lighting"));
            appendField(brief, "- 氛围", scene.get("mood"));
        }

        JsonNode subject = expr.get("subject");
        if (subject != null && !subject.isEmpty()) {
            brief.append("### 主体\n");
            appendField(brief, "- 主体角色", subject.get("main_subject_role"));
            appendField(brief, "- 主体位置", subject.get("subject_placement"));
            appendArrayField(brief, "- 辅助物体", subject.get("supporting_objects"));
        }

        JsonNode composition = expr.get("composition");
        if (composition != null && !composition.isEmpty()) {
            brief.append("### 构图\n");
            appendField(brief, "- 取景", composition.get("framing"));
            appendField(brief, "- 视角", composition.get("viewpoint"));
            appendField(brief, "- 布局", composition.get("layout_grid"));
        }

        JsonNode style = expr.get("visual_style");
        if (style != null && !style.isEmpty()) {
            brief.append("### 视觉风格\n");
            appendField(brief, "- 媒介", style.get("medium"));
            appendField(brief, "- 色调", style.get("color_palette"));
            appendField(brief, "- 精细程度", style.get("polish_level"));
        }

        JsonNode copy = expr.get("copy_structure");
        if (copy != null && !copy.isEmpty()) {
            brief.append("### 文案结构\n");
            appendField(brief, "- 标题", copy.get("headline"));
            appendArrayField(brief, "- 功能标签", copy.get("feature_labels"));
            appendField(brief, "- 文案布局", copy.get("body_copy_pattern"));
            appendField(brief, "- 字型", copy.get("typography"));
        }

        brief.append("\n");
    }

    private void appendFactsToBrief(StringBuilder brief, JsonNode facts, String imageType) {
        brief.append("## 目标产品事实\n\n");
        brief.append("（按图片类型「").append(imageType).append("」自动筛选）\n\n");

        JsonNode identity = facts.get("identity");
        if (identity != null) {
            brief.append("### 产品身份\n");
            appendField(brief, "- 产品名称", identity.get("product_name"));
            appendField(brief, "- 品牌", identity.get("brand"));
            appendField(brief, "- 型号", identity.get("model_number"));
            brief.append("\n");
        }

        List<String> relevantSections = getRelevantFactSections(imageType);
        for (String section : relevantSections) {
            JsonNode data = facts.get(section);
            if (data != null && !data.isEmpty()) {
                brief.append("### ").append(sectionLabel(section)).append("\n");
                data.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                        brief.append("- ").append(key).append(": ").append(formatValue(value)).append("\n");
                    }
                });
                brief.append("\n");
            }
        }
    }

    private void appendMaterialFactsToBrief(StringBuilder brief, JsonNode materialFacts, Set<String> checkedKeys) {
        brief.append("## 素材事实（用户勾选）\n\n");
        materialFacts.fields().forEachRemaining(entry -> {
            if (checkedKeys.contains(entry.getKey())) {
                String value = formatValue(entry.getValue());
                if (!value.isBlank()) {
                    brief.append("- ").append(entry.getKey()).append(": ").append(value).append("\n");
                }
            }
        });
        brief.append("\n");
    }

    // --- Prompt generation ---

    private void appendExpressionToPrompt(StringBuilder prompt, JsonNode expr) {
        JsonNode scene = expr.get("scene");
        if (scene != null) {
            String env = text(scene.get("environment"));
            String bg = text(scene.get("background"));
            String lighting = text(scene.get("lighting"));
            if (!env.isBlank()) prompt.append("Environment: ").append(env).append(". ");
            if (!bg.isBlank()) prompt.append("Background: ").append(bg).append(". ");
            if (!lighting.isBlank()) prompt.append("Lighting: ").append(lighting).append(". ");
        }

        JsonNode subject = expr.get("subject");
        if (subject != null) {
            String role = text(subject.get("main_subject_role"));
            if (!role.isBlank()) prompt.append("The ").append(role).append(" is prominently featured. ");
        }

        JsonNode style = expr.get("visual_style");
        if (style != null) {
            String medium = text(style.get("medium"));
            String palette = text(style.get("color_palette"));
            String detail = text(style.get("texture_detail"));
            if (!medium.isBlank()) prompt.append("Style: ").append(medium).append(". ");
            if (!palette.isBlank()) prompt.append("Color palette: ").append(palette).append(". ");
            if (!detail.isBlank()) prompt.append("Texture detail: ").append(detail).append(". ");
        }

        JsonNode composition = expr.get("composition");
        if (composition != null) {
            String framing = text(composition.get("framing"));
            String viewpoint = text(composition.get("viewpoint"));
            if (!framing.isBlank()) prompt.append("Framing: ").append(framing).append(". ");
            if (!viewpoint.isBlank()) prompt.append("Viewpoint: ").append(viewpoint).append(". ");
        }

        // Risk notes as negative constraints
        JsonNode risks = expr.get("risk_notes");
        if (risks != null && risks.isArray() && risks.size() > 0) {
            prompt.append("CRITICAL constraints: ");
            for (JsonNode risk : risks) {
                prompt.append(risk.asText()).append("; ");
            }
        }

        prompt.append("\n\n");
    }

    private void appendFactsToPrompt(StringBuilder prompt, JsonNode facts, String imageType) {
        List<String> relevantSections = getRelevantFactSections(imageType);

        JsonNode identity = facts.get("identity");
        if (identity != null) {
            String name = text(identity.get("product_name"));
            String brand = text(identity.get("brand"));
            if (!name.isBlank()) {
                prompt.append("Product: ").append(name).append(". ");
            }
            if (!brand.isBlank()) {
                prompt.append("Brand: ").append(brand).append(". ");
            }
        }

        for (String section : relevantSections) {
            JsonNode data = facts.get(section);
            if (data != null && !data.isEmpty()) {
                List<String> items = new ArrayList<>();
                data.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    String formatted = formatValue(value);
                    if (!formatted.isBlank() && !"false".equalsIgnoreCase(formatted)) {
                        items.add(key + ": " + formatted);
                    }
                });
                if (!items.isEmpty()) {
                    prompt.append(String.join("; ", items)).append(". ");
                }
            }
        }

        prompt.append("\n\n");
    }

    private void appendMaterialFactsToPrompt(StringBuilder prompt, JsonNode materialFacts, Set<String> checkedKeys) {
        prompt.append("Reference selling points: ");
        List<String> items = new ArrayList<>();
        materialFacts.fields().forEachRemaining(entry -> {
            if (checkedKeys.contains(entry.getKey())) {
                String formatted = formatValue(entry.getValue());
                if (!formatted.isBlank()) {
                    items.add(entry.getKey() + ": " + formatted);
                }
            }
        });
        if (!items.isEmpty()) {
            prompt.append(String.join("; ", items)).append(".");
        }
        prompt.append("\n\n");
    }

    // --- Field mapping by image type ---

    /**
     * 图片类型到产品事实字段的映射（阶段 1 写死，后续规则配置化）。
     */
    private List<String> getRelevantFactSections(String imageType) {
        if (imageType == null) return List.of("physical_specs", "technical_specs", "features");

        return switch (imageType.trim()) {
            case "feature_infographic" -> List.of("physical_specs", "technical_specs", "features", "compatibility");
            case "installation_scene" -> List.of("physical_specs", "compatibility", "included_items");
            case "dimension" -> List.of("physical_specs");
            case "ports_wiring" -> List.of("physical_specs", "technical_specs");
            case "package_contents" -> List.of("physical_specs", "included_items");
            case "compatibility" -> List.of("compatibility", "physical_specs");
            case "a_plus_banner" -> List.of("identity", "physical_specs", "features");
            case "a_plus_module" -> List.of("physical_specs", "technical_specs", "features", "compatibility", "included_items");
            default -> List.of("physical_specs", "technical_specs", "features");
        };
    }

    private String sectionLabel(String section) {
        return switch (section) {
            case "physical_specs" -> "物理规格";
            case "technical_specs" -> "技术规格";
            case "features" -> "功能";
            case "compatibility" -> "兼容性";
            case "included_items" -> "包装清单";
            case "identity" -> "产品身份";
            default -> section;
        };
    }

    // --- Helpers ---

    private JsonNode parseSafe(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    private void appendField(StringBuilder sb, String label, JsonNode value) {
        if (value != null && !value.isNull()) {
            String text = value.asText("");
            if (!text.isBlank()) {
                sb.append(label).append(": ").append(text).append("\n");
            }
        }
    }

    private void appendArrayField(StringBuilder sb, String label, JsonNode value) {
        if (value != null && value.isArray() && value.size() > 0) {
            List<String> items = new ArrayList<>();
            value.forEach(item -> {
                String t = item.asText("");
                if (!t.isBlank()) items.add(t);
            });
            if (!items.isEmpty()) {
                sb.append(label).append(": ").append(String.join(", ", items)).append("\n");
            }
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String formatValue(JsonNode value) {
        if (value == null || value.isNull()) return "";
        if (value.isTextual()) return value.asText();
        if (value.isBoolean()) return value.asBoolean() ? "yes" : "no";
        if (value.isNumber()) return value.asText();
        if (value.isArray()) {
            List<String> items = new ArrayList<>();
            value.forEach(item -> {
                String t = item.asText("");
                if (!t.isBlank()) items.add(t);
            });
            return String.join(", ", items);
        }
        return value.toPrettyString();
    }

    private Set<String> parseCheckedKeys(String checkedKeys) {
        Set<String> keys = new HashSet<>();
        if (checkedKeys == null || checkedKeys.isBlank()) return keys;
        for (String key : checkedKeys.split(",")) {
            String trimmed = key.trim();
            if (!trimmed.isBlank()) keys.add(trimmed);
        }
        return keys;
    }

    public record PromptCompositionResult(
            String structuredBrief,
            String finalPrompt
    ) {}
}
