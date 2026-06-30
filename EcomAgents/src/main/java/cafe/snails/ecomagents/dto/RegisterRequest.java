package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO。
 */
@Data
public class RegisterRequest {
    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度2-20个字符")
    private String username;

    /** 电子邮箱（可选） */
    private String email;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度6-20个字符")
    private String password;

    /** 邀请码 */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
