package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 技能索引表 — 文件系统 workspace/skills/ 的只读缓存。
 * <p>SSOT 是文件系统，此表仅在技能变更时刷新，供前端列表展示。</p>
 */
@Entity
@Table(name = "skill_index")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillIndex {

    @Id
    @Column(length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
