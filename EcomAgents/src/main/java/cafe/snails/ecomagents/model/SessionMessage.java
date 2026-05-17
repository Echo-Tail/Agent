package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 会话消息，可嵌入 {@link Session} 实体。
 * <p>每条消息包含角色（user / assistant）和文本内容。</p>
 */
@Embeddable
@Data
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
}
