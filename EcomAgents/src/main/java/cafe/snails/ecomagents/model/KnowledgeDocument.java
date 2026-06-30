package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 知识库文档实体，映射 knowledge_documents 表。
 * <p>文档从上传的文件解析而来，内容以文本形式存储在 content 字段中，供 RAG 检索使用。</p>
 */
@Entity
@Table(name = "knowledge_documents")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeDocument {
    /** 文档 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属知识库 ID */
    @Column(name = "kb_id", nullable = false)
    private Long knowledgeBaseId;

    /** 原始文件名 */
    @Column(nullable = false, length = 255)
    private String fileName;

    /** 文件类型扩展名（txt / md / pdf 等） */
    @Column(length = 50)
    private String fileType;

    /** 文档文本内容（CLOB 大字段） */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 字符数，用于统计和展示 */
    @Column(name = "char_count")
    private Integer charCount;

    /** 上传时间 */
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    /** 上传者用户 ID */
    @Column(nullable = false)
    private Long uploadedBy;
}
