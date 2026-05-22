package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    Optional<Ticket> findTopByTicketNumberStartingWithOrderByTicketNumberDesc(String prefix);
}
