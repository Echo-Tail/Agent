package cafe.snails.ecomagents.model;

/**
 * 工单状态枚举，描述工单从提交到完成的处理流程。
 */
public enum TicketStatus {
    /** 待处理。 */
    PENDING,
    /** 处理中。 */
    IN_PROGRESS,
    /** 已完成。 */
    COMPLETED
}
