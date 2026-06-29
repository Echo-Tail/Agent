package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 上传文件记录实体，映射 file_records 表。
 * <p>记录上传文件的元数据，文件本体存储在服务器磁盘上。</p>
 */
@Entity
@Table(name = "file_records")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRecord {

    /** 文件记录 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始文件名 */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** 服务器存储路径 */
    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** MIME 类型 */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** 上传时间 */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /** 上传者用户 ID */
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    /** 对话上下文类型：PRIVATE（私聊）/ AGENT（AI聊天） */
    @Column(name = "context_type", length = 20)
    private String contextType;

    /** 对话上下文 ID：私聊为对方用户 ID，AI 聊天为 Agent ID */
    @Column(name = "context_id")
    private Long contextId;

    /** 文件在聊天中的可访问 URL（指向公开的下载端点，无需认证即可访问） */
    @Transient
    public String getUrl() {
        return "/v1/files/" + id + "/download";
    }
}
