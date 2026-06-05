package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话实体，映射 sessions 表。
 * <p>一个会话属于某个 Agent，包含多条消息，可归入文件夹进行组织。</p>
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {
    /** 会话 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的 Agent ID */
    @Column(nullable = false)
    private Long agentId;

    /** 创建者用户 ID，默认为 0（兼容历史数据） */
    @Column(columnDefinition = "bigint default 0 not null")
    @Builder.Default
    private Long userId = 0L;

    /** 会话标题，由用户第一条消息自动生成或手动命名 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 所属文件夹 ID，为 null 表示未归档 */
    private Long folderId;

    /** HarnessAgent 使用的会话 ID（格式：sess-{agentId}-{userId}-{uuid}） */
    @Column(name = "harness_session_id", length = 100)
    private String harnessSessionId;

    /** 标签列表 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_tags", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "tag", length = 50)
    private List<String> tags;

    /** 消息列表，按时间顺序存储 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "session_messages", joinColumns = @JoinColumn(name = "session_id"))
    private List<SessionMessage> messages;

    /** 创建时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 插入前自动初始化时间戳和集合字段 */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (messages == null) messages = new ArrayList<>();
        if (tags == null) tags = new ArrayList<>();
    }

    /** 更新时自动刷新 updatedAt */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
