package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketHandleRequest {
    @NotBlank(message = "处理意见不能为空")
    private String handlingNote;
}
