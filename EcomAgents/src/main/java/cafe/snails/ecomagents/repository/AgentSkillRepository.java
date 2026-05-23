package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AgentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentSkillRepository extends JpaRepository<AgentSkill, Long> {
    List<AgentSkill> findByAgentId(Long agentId);
    void deleteByAgentId(Long agentId);
    void deleteByAgentIdAndSkillName(Long agentId, String skillName);
    List<AgentSkill> findBySkillName(String skillName);
    boolean existsByAgentIdAndSkillName(Long agentId, String skillName);
}
