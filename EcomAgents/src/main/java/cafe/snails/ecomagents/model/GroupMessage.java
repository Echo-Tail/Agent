package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 群消息，映射 group_messages 表。
 */
@Entity
@Table(name = "group_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** 发送者 ID（用户 ID 或 Agent ID，由 senderType 区分） */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** 发送者类型：USER 或 AGENT */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SenderType senderType;

    /** 消息文本内容（含 @[名称](agent:id) Markdown 格式） */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 引用的消息 ID（回复某条消息时使用） */
    @Column(name = "reply_to_msg_id")
    private Long replyToMsgId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
