package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.GroupAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupAgentRepository extends JpaRepository<GroupAgent, Long> {
    /** 查询群绑定的所有 Agent */
    List<GroupAgent> findByGroupId(Long groupId);
    /** 查询某个 Agent 被哪些群绑定 */
    List<GroupAgent> findByAgentId(Long agentId);
    /** 查询群内某个 Agent 绑定的记录 */
    Optional<GroupAgent> findByGroupIdAndAgentId(Long groupId, Long agentId);
    /** 检查 Agent 是否已在群里 */
    boolean existsByGroupIdAndAgentId(Long groupId, Long agentId);
    /** 移除群内某个 Agent */
    void deleteByGroupIdAndAgentId(Long groupId, Long agentId);
    /** 移除群的所有 Agent（解散群时使用） */
    void deleteByGroupId(Long groupId);
}
