package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一群成员 DTO，合并用户和 Agent 成员。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedMemberDTO {
    /** 记录 ID（group_members.id 或 group_agents.id） */
    private Long id;
    /** USER 或 AGENT */
    private String memberType;
    /** 用户 ID 或 Agent ID */
    private Long refId;
    /** 显示名称（User.username 或 Agent.name） */
    private String name;
    /** 头像 URL（User.avatar 或 Agent.avatar） */
    private String avatar;
    /** Agent 图标类名（仅 AGENT 时有值） */
    private String icon;
    /** 角色（CREATOR / MEMBER） */
    private String role;
}
