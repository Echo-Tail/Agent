package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具可用性响应 DTO，描述某个 Agent 绑定的工具是否已全局启用且完成配置。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolAvailability {
    /** 工具唯一标识。 */
    private String toolId;
    /** 被检查的 Agent ID。 */
    private Long agentId;
    /** 该工具是否已绑定到 Agent。 */
    private boolean boundToAgent;
    /** 该工具是否在全局工具配置中启用。 */
    private boolean globallyEnabled;
    /** 该工具所需配置是否完整。 */
    private boolean configured;
    /** 综合判断后的最终可用状态。 */
    private boolean available;
    /** 不可用原因或可用性说明。 */
    private String message;
}
