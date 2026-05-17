package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * 会话文件夹实体，映射 session_folders 表。
 * <p>支持树形层级结构（通过 parentId 自关联），用于组织和管理会话列表。</p>
 */
@Entity
@Table(name = "session_folders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionFolder {
    /** 文件夹 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文件夹名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 父文件夹 ID，为 null 表示根文件夹 */
    private Long parentId;

    /** 排序序号，同级文件夹中按此值升序排列 */
    @Column(nullable = false)
    @Builder.Default
    private Integer orderNum = 0;
}
