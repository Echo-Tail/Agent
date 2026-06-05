package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 知识库实体，映射 knowledge_bases 表。
 * <p>知识库是文档的集合，Agent 可关联一个或多个知识库，实现 RAG（检索增强生成）。</p>
 */
@Entity
@Table(name = "knowledge_bases")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBase {
    /** 知识库 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 知识库名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 知识库描述 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 创建日期 */
    @Column(nullable = false)
    private LocalDate createdAt;

    /** 创建者用户 ID */
    @Column(nullable = false)
    private Long createdBy;
}
