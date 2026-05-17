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
    private Long id;
    private String name;
    private String description;
    private String content;
    private String category;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private String source = "pool";

    private String resourcesJson;
    private String metadataJson;
    private String createdAt;
    private String updatedAt;
}
