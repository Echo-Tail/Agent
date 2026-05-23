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
public class AgentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(nullable = false)
    private LocalDateTime copiedAt;

    @Column(length = 50)
    private String version;
}
