package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.KnowledgeAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeAuditLogRepository extends JpaRepository<KnowledgeAuditLog, Long> {
    List<KnowledgeAuditLog> findByKbIdOrderByCreatedAtDesc(Long kbId);
    List<KnowledgeAuditLog> findAllByOrderByCreatedAtDesc();
}
