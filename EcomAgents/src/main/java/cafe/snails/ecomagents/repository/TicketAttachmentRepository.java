package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    List<TicketAttachment> findByTicketIdAndActiveTrue(Long ticketId);
    List<TicketAttachment> findByTicketId(Long ticketId);
}
