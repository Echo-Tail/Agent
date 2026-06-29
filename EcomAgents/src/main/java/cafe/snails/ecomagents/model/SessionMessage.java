package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 会话消息，可嵌入 {@link Session} 实体。
 * <p>每条消息包含角色（user / assistant）和文本内容。</p>
 */
@Embeddable
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionMessage {
    /** 消息角色：user（用户） 或 assistant（AI 回复） */
    @Column(nullable = false, length = 20)
    private String role;

    /** 消息文本内容 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 消息发送时间 */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** 关联的 FileRecord ID（Agent 生成的文件） */
    @Column(name = "file_id")
    private Long fileId;

    /** 文件名（冗余存储，方便前端显示） */
    @Column(name = "file_name", length = 255)
    private String fileName;
}
