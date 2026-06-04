package cafe.snails.ecomagents.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
/**
 * 工单字段变更记录响应 DTO，用于展示工单审计时间线。
 */
public class TicketChangeRecordResponse {
    /** 变更记录 ID。 */
    private Long id;
    /** 被修改的字段名。 */
    private String fieldName;
    /** 修改前的值。 */
    private String oldValue;
    /** 修改后的值。 */
    private String newValue;
    /** 执行变更的用户 ID。 */
    private Long changedBy;
    /** 执行变更的用户展示名。 */
    private String changedByName;
    /** 变更发生时间。 */
    private LocalDateTime changedAt;
}
