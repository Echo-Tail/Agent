package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Token 用量记录，每次 LLM 调用生成一条记录。
 */
@Entity
@Table(name = "token_usage_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模型 ID */
    @Column(name = "model_id")
    private Long modelId;

    /** 模型名称（冗余，模型被删后仍可追溯） */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /** 模型类型：TEXT / IMAGE */
    @Column(name = "model_type", length = 10)
    private String modelType;

    /** 调用用户 ID */
    @Column(name = "user_id")
    private Long userId;

    /** 使用的 Agent ID */
    @Column(name = "agent_id")
    private Long agentId;

    /** 输入 token 数 */
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    /** 输出 token 数 */
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    /** 总 token 数 */
    @Column(name = "total_tokens")
    private Integer totalTokens;

    /** 是否调用成功 */
    @Column(nullable = false)
    private Boolean success;

    /** 失败时的错误信息 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 调用时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
