package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_skills", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "skill_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * Agent 技能绑定实体，记录全局技能复制到某个 Agent 工作区后的引用关系。
 */
public class AgentSkill {

    /** 绑定记录主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Agent ID。 */
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** 绑定的技能名称。 */
    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    /** 技能复制到 Agent 工作区的时间。 */
    @Column(nullable = false)
    private LocalDateTime copiedAt;

    /** 绑定时的技能版本。 */
    @Column(length = 50)
    private String version;
}
