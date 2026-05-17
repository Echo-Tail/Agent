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
    private String id;
    private String name;
    private String description;
    private String category;
}
