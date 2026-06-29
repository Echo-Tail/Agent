package cafe.snails.ecomagents.dto;

import lombok.Data;

/**
 * 登录成功响应 DTO，包含用户信息和 JWT token。
 */
@Data
public class LoginResponse {
    /** 登录用户信息 */
    private UserDTO user;
    /** JWT token（当前为 mock 实现） */
    private String token;
}
