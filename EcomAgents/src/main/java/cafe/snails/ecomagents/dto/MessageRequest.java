package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 添加会话消息请求 DTO。
 */
@Data
public class MessageRequest {
    /** 消息角色：user / assistant */
    @NotBlank(message = "角色不能为空")
    private String role;

    /** 消息文本内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
