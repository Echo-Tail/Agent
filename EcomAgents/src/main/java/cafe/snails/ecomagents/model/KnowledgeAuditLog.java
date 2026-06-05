package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_audit_log")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 知识库审计日志实体，记录用户对知识库文件的上传、删除、重建等操作。
 */
public class KnowledgeAuditLog {

    /** 审计日志主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被操作的知识库 ID。 */
    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    /** 操作用户 ID。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 操作用户名称快照，避免用户改名影响历史记录。 */
    @Column(nullable = false, length = 100)
    private String username;

    /** 操作类型，例如 upload、delete、reindex。 */
    @Column(nullable = false, length = 20)
    private String operation;

    /** 被操作的文件名。 */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** 发起操作的客户端 IP。 */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** 审计记录创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
