package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 工单附件关联仓库，查询工单的有效附件和完整附件历史。
 */
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    /** 查询工单当前有效附件。 */
    List<TicketAttachment> findByTicketIdAndActiveTrue(Long ticketId);
    /** 查询工单全部附件关联记录，包含已移除记录。 */
    List<TicketAttachment> findByTicketId(Long ticketId);
}
