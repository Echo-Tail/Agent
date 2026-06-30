package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.AffectedMenu;
import cafe.snails.ecomagents.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
/**
 * 创建工单请求 DTO，承载用户提交的问题、优先级和附件引用。
 */
public class TicketCreateRequest {
    /** 工单标题。 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题最多120个字符")
    private String title;

    /** 受影响的菜单或业务模块。 */
    @NotNull(message = "受影响菜单不能为空")
    private AffectedMenu affectedMenu;

    /** 工单优先级。 */
    @NotNull(message = "优先级不能为空")
    private TicketPriority priority;

    /** 工单详细内容。 */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 已上传附件的文件 ID 列表。 */
    private List<Long> attachmentIds;
}
