package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent（AI 助手）数据访问层。
 */
public interface AgentRepository extends JpaRepository<Agent, Long> {
    /** 按创建者 ID 查找 Agent */
    List<Agent> findByCreatedBy(Long createdBy);
    /** 按状态查找 Agent */
    List<Agent> findByStatus(String status);
    /** 查找系统 Agent */
    Optional<Agent> findByIsSystemTrue();
}
