package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skills")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 全局技能实体，保存可被 Agent 绑定或复制到工作区的技能元信息。
 */
public class Skills {

    /** 技能主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 技能名称，全局唯一。 */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** 技能用途说明。 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 技能分类，用于筛选和展示。 */
    @Column(length = 50)
    private String category;

    /** 技能版本号。 */
    @Column(length = 50)
    private String version;

    /** 技能来源地址，通常指向导入包或外部仓库。 */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 最近更新时间。 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
