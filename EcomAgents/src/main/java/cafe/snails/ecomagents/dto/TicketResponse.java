package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.AffectedMenu;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.model.TicketPriority;
import cafe.snails.ecomagents.model.TicketStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
/**
 * 工单详情响应 DTO，聚合工单主体、提交人/处理人展示名以及附件列表。
 */
public class TicketResponse {
    /** 工单主键 ID。 */
    private Long id;
    /** 面向用户展示和检索的工单编号。 */
    private String ticketNumber;
    /** 工单标题。 */
    private String title;
    /** 受影响的业务菜单或模块。 */
    private AffectedMenu affectedMenu;
    /** 工单优先级。 */
    private TicketPriority priority;
    /** 用户提交的工单正文。 */
    private String content;
    /** 当前处理状态。 */
    private TicketStatus status;
    /** 提交人用户 ID。 */
    private Long submitterId;
    /** 提交人展示名称。 */
    private String submitterName;
    /** 当前处理人用户 ID，未分配时为空。 */
    private Long handlerId;
    /** 当前处理人展示名称。 */
    private String handlerName;
    /** 处理备注或结论。 */
    private String handlingNote;
    /** 工单创建时间。 */
    private LocalDateTime createdAt;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
    /** 开始处理时间。 */
    private LocalDateTime startedAt;
    /** 完成时间。 */
    private LocalDateTime completedAt;
    /** 工单关联附件。 */
    private List<FileRecord> attachments;
}
