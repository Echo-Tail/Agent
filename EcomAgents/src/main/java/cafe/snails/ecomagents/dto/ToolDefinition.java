package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具定义 DTO，描述一个可供 Agent 使用的系统工具。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {
    /** 工具唯一标识。 */
    private String id;
    /** 工具展示名称。 */
    private String name;
    /** 工具用途说明。 */
    private String description;
    /** 工具分类，用于前端分组展示。 */
    private String category;

    /** 是否启用该工具。 */
    @Builder.Default
    private Boolean enabled = true;

    /** 工具配置 JSON，保存 API Key、端点或开关等扩展配置。 */
    @Builder.Default
    private String configJson = "";
}
