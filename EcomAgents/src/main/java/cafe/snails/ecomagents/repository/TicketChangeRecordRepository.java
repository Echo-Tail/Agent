package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.TicketChangeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 工单变更记录仓库。
 */
public interface TicketChangeRecordRepository extends JpaRepository<TicketChangeRecord, Long> {
    /** 查询指定工单的变更记录，按变更时间倒序。 */
    List<TicketChangeRecord> findByTicketIdOrderByChangedAtDesc(Long ticketId);
}
