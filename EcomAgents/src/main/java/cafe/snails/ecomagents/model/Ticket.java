package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_submitter", columnList = "submitter_id"),
        @Index(name = "idx_tickets_status", columnList = "status"),
        @Index(name = "idx_tickets_number", columnList = "ticket_number", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 工单实体，记录用户提交的问题、流转状态、处理人和处理时间线。
 */
public class Ticket {

    /** 工单主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 全局唯一工单编号，用于展示、搜索和外部沟通。 */
    @Column(name = "ticket_number", nullable = false, unique = true, length = 32)
    private String ticketNumber;

    /** 工单标题，概括用户遇到的问题。 */
    @Column(nullable = false, length = 120)
    private String title;

    /** 受影响的菜单或业务模块。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "affected_menu", nullable = false, length = 40)
    private AffectedMenu affectedMenu;

    /** 优先级，影响排序和处理 SLA。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    /** 工单详细内容。 */
    @Lob
    @Column(nullable = false)
    private String content;

    /** 当前处理状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    /** 提交人用户 ID。 */
    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    /** 处理人用户 ID，尚未接单时为空。 */
    @Column(name = "handler_id")
    private Long handlerId;

    /** 处理备注、解决方案或关闭说明。 */
    @Lob
    @Column(name = "handling_note")
    private String handlingNote;

    /** 工单创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最近更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 工单开始处理时间。 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 工单完成时间。 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
