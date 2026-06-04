package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.GroupRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群成员 DTO，包含用户名信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMemberDTO {
    /** 群成员记录 ID。 */
    private Long id;
    /** 群 ID。 */
    private Long groupId;
    /** 用户 ID。 */
    private Long userId;
    /** 用户名（从 User 表关联） */
    private String username;
    /** 成员在群中的角色。 */
    private GroupRole role;
    /** 用户加入群的时间。 */
    private LocalDateTime joinedAt;
}
