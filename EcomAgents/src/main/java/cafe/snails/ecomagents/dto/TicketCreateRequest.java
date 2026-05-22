package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.AffectedMenu;
import cafe.snails.ecomagents.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TicketCreateRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题最多120个字符")
    private String title;

    @NotNull(message = "受影响菜单不能为空")
    private AffectedMenu affectedMenu;

    @NotNull(message = "优先级不能为空")
    private TicketPriority priority;

    @NotBlank(message = "内容不能为空")
    private String content;

    private List<Long> attachmentIds;
}
