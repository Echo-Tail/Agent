package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 群内文件，映射 group_files 表。
 * <p>文件本体通过 FileStorageService 存储在磁盘，此表仅存元数据。</p>
 */
@Entity
@Table(name = "group_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupFile {
    /** 群文件记录主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文件所属群 ID。 */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** 上传者用户 ID */
    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    /** 原始文件名 */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** 文件大小（字节） */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** MIME 类型 */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** 服务端文件路径 */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** 文件上传时间。 */
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    /** 首次持久化前自动补齐上传时间。 */
    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }
}
