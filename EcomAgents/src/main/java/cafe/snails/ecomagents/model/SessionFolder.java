package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * 会话文件夹实体，映射 session_folders 表。
 * <p>一级文件夹，用于组织和管理会话列表。</p>
 */
@Entity
@Table(name = "session_folders")
@Getter
@Setter
@ToString
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

    /** 排序序号，按此值升序排列 */
    @Column(nullable = false)
    @Builder.Default
    private Integer orderNum = 0;

    /** 创建者用户 ID，默认为 0（兼容历史数据） */
    @Column(columnDefinition = "bigint default 0 not null")
    @Builder.Default
    private Long userId = 0L;
}
