package cafe.snails.ecomagents.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 批量生成邀请码请求 DTO。
 */
@Data
public class BatchInviteRequest {
    /** 生成数量 */
    @Min(value = 1, message = "数量至少为1")
    private int count;
}
