package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能定义 DTO，描述一个可供 Agent 使用的技能。
 * <p>与 ToolDefinition 类似，但以 Long 类型的 ID 标识。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillDefinition {
    /** 技能 ID。 */
    private Long id;
    /** 技能名称，通常作为 Agent 绑定技能时的唯一标识。 */
    private String name;
    /** 技能用途说明。 */
    private String description;
    /** 技能正文或提示词内容。 */
    private String content;
    /** 技能分类，用于前端分组展示。 */
    private String category;

    /** 是否启用该技能。 */
    @Builder.Default
    private Boolean enabled = true;

    /** 技能来源，例如全局技能池或工作区副本。 */
    @Builder.Default
    private String source = "pool";

    /** 技能资源配置的 JSON 字符串。 */
    private String resourcesJson;
    /** 技能扩展元数据的 JSON 字符串。 */
    private String metadataJson;
    /** 创建时间的字符串表示。 */
    private String createdAt;
    /** 更新时间的字符串表示。 */
    private String updatedAt;
}
