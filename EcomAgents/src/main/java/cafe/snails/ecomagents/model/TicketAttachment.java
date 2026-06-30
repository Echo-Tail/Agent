package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_attachments", indexes = {
        @Index(name = "idx_ticket_attachments_ticket", columnList = "ticket_id")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 工单附件关联实体，连接工单与已上传文件记录，并保留添加/移除审计信息。
 */
public class TicketAttachment {

    /** 关联记录主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属工单。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** 附件对应的文件记录。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "file_record_id", nullable = false)
    private FileRecord fileRecord;

    /** 是否仍有效；移除附件时置为 false 以保留审计链路。 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** 添加附件时间。 */
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    /** 添加附件的用户 ID。 */
    @Column(name = "added_by", nullable = false)
    private Long addedBy;

    /** 移除附件时间，仍有效时为空。 */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    /** 移除附件的用户 ID。 */
    @Column(name = "removed_by")
    private Long removedBy;
}
