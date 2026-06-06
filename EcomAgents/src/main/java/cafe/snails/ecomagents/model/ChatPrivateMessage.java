package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用户私聊消息，映射 chat_private_messages 表。
 */
@Entity
@Table(name = "chat_private_messages")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatPrivateMessage {
    /** 私聊消息主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发送者用户 ID */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** 接收者用户 ID */
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /** 消息文本内容 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 关联的文件 ID（如果此消息包含文件） */
    @Column(name = "file_id")
    private Long fileId;

    /** 是否已读 */
    @Column(name = "is_read")
    @Builder.Default
    private Boolean read = false;

    /** 消息创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 首次持久化前自动补齐消息创建时间。 */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
