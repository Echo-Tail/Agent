package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 工单处理请求 DTO，用于管理员接单、完成或更新处理意见。
 */
public class TicketHandleRequest {
    /** 处理意见、解决方案或关闭说明。 */
    @NotBlank(message = "处理意见不能为空")
    private String handlingNote;
}
