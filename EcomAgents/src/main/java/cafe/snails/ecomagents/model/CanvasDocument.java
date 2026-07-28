package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 图片工作区画布的当前文档快照。
 */
@Entity
@Table(name = "canvas_documents")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CanvasDocument {
    @Id
    private Long sessionId;
    @Version
    private Long revision;
    @Column(nullable = false)
    private Integer schemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
