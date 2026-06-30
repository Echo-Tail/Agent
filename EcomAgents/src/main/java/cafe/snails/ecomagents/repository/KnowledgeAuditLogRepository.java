package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.KnowledgeAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
/**
 * 知识库审计日志仓库。
 */
public interface KnowledgeAuditLogRepository extends JpaRepository<KnowledgeAuditLog, Long> {
    /** 查询指定知识库的审计日志，按创建时间倒序。 */
    List<KnowledgeAuditLog> findByKbIdOrderByCreatedAtDesc(Long kbId);
    /** 查询全部知识库审计日志，按创建时间倒序。 */
    List<KnowledgeAuditLog> findAllByOrderByCreatedAtDesc();
}
