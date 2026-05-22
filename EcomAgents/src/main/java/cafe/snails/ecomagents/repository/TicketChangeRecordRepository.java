package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.TicketChangeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketChangeRecordRepository extends JpaRepository<TicketChangeRecord, Long> {
    List<TicketChangeRecord> findByTicketIdOrderByChangedAtDesc(Long ticketId);
}
