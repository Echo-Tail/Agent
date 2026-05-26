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
    private Long id;
    private Long groupId;
    private Long userId;
    /** 用户名（从 User 表关联） */
    private String username;
    private GroupRole role;
    private LocalDateTime joinedAt;
}
