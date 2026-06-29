package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_change_records", indexes = {
        @Index(name = "idx_ticket_change_records_ticket", columnList = "ticket_id")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 工单变更记录实体，保存字段级变更历史以支持审计和时间线展示。
 */
public class TicketChangeRecord {

    /** 变更记录主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发生变更的工单。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** 被修改的字段名。 */
    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    /** 修改前的字段值。 */
    @Lob
    @Column(name = "old_value")
    private String oldValue;

    /** 修改后的字段值。 */
    @Lob
    @Column(name = "new_value")
    private String newValue;

    /** 执行变更的用户 ID。 */
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    /** 变更发生时间。 */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
