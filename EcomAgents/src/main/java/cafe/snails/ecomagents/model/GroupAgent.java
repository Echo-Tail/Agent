package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 群绑定的 Agent，映射 group_agents 表。
 * <p>一个 Agent 只能被拉入同一个群一次。</p>
 */
@Entity
@Table(name = "group_agents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "agent_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupAgent {
    /** 群 Agent 关联主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 群 ID。 */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** 被拉入群的 Agent ID。 */
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** 拉入者的用户 ID */
    @Column(name = "added_by", nullable = false)
    private Long addedBy;

    /** Agent 被拉入群的时间。 */
    @Column(nullable = false)
    private LocalDateTime addedAt;

    /** 首次持久化前自动补齐拉入时间。 */
    @PrePersist
    protected void onCreate() {
        if (addedAt == null) addedAt = LocalDateTime.now();
    }
}
