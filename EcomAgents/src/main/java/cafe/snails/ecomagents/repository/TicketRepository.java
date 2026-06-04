package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 工单仓库，支持基础 CRUD、动态条件查询和按编号前缀查找最新工单。
 */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    /** 查询指定编号前缀下编号最大的工单，用于生成递增工单号。 */
    Optional<Ticket> findTopByTicketNumberStartingWithOrderByTicketNumberDesc(String prefix);
}
