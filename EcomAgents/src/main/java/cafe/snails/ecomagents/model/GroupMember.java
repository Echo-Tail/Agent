package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 群成员，映射 group_members 表。
 * <p>一个用户在一个群里只有一个角色（CREATOR 或 MEMBER）。</p>
 */
@Entity
@Table(name = "group_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember {
    /** 群成员记录主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 群 ID。 */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** 用户 ID。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 成员角色 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupRole role;

    /** 加入群的时间。 */
    @Column(nullable = false)
    private LocalDateTime joinedAt;

    /** 首次持久化前自动补齐入群时间。 */
    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) joinedAt = LocalDateTime.now();
    }
}
