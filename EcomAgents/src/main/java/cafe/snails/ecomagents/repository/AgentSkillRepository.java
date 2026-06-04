package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AgentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
/**
 * Agent 技能绑定仓库，维护 Agent 与技能名称之间的关联关系。
 */
public interface AgentSkillRepository extends JpaRepository<AgentSkill, Long> {
    /** 查询指定 Agent 绑定的所有技能。 */
    List<AgentSkill> findByAgentId(Long agentId);
    /** 删除指定 Agent 的全部技能绑定。 */
    void deleteByAgentId(Long agentId);
    /** 删除指定 Agent 与指定技能的绑定。 */
    void deleteByAgentIdAndSkillName(Long agentId, String skillName);
    /** 查询绑定了指定技能的所有 Agent 记录。 */
    List<AgentSkill> findBySkillName(String skillName);
    /** 判断指定 Agent 是否已绑定指定技能。 */
    boolean existsByAgentIdAndSkillName(Long agentId, String skillName);
}
