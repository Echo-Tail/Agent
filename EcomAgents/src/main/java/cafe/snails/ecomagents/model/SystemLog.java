package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_logs", indexes = {
        @Index(name = "idx_system_logs_created_at", columnList = "createdAt"),
        @Index(name = "idx_system_logs_level", columnList = "level"),
        @Index(name = "idx_system_logs_category", columnList = "category")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 系统日志实体，保存前后端运行事件、错误、性能耗时和用户上下文。
 */
public class SystemLog {

    /** 日志主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 日志级别，例如 info、warn、error。 */
    @Column(nullable = false, length = 20)
    private String level;

    /** 日志类别，用于按业务域或来源筛选。 */
    @Column(nullable = false, length = 30)
    private String category;

    /** 日志摘要消息。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** 结构化上下文数据，通常保存为 JSON 字符串。 */
    @Column(columnDefinition = "TEXT")
    private String data;

    /** 操作耗时，单位通常为毫秒。 */
    private Long duration;

    /** 触发日志的前端路由或后端接口路径。 */
    @Column(length = 200)
    private String route;

    /** 关联用户 ID，匿名事件为空。 */
    @Column(name = "user_id")
    private Long userId;

    /** 日志创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
