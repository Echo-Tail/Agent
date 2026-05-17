package cafe.snails.ecomagents.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * 用户信息 DTO，用于前端展示，不暴露密码等敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    /** 用户 ID */
    private Long id;
    /** 用户名 */
    private String username;
    /** 邮箱 */
    private String email;
    /** 角色：admin / user */
    private String role;
    /** 状态：active / disabled */
    private String status;
    /** 注册使用的邀请码 */
    private String inviteCode;
    /** 创建日期 */
    private LocalDate createdAt;
}
