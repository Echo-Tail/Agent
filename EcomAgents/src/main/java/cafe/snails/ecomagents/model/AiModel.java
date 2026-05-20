package cafe.snails.ecomagents.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * AI 模型配置实体，映射 ai_models 表。
 * <p>由管理员管理，定义可用的 LLM 后端（如 GPT-4o、DeepSeek、通义千问），
 * 可被多个 Agent 引用。支持设置默认模型和启用/停用。</p>
 */
@Entity
@Table(name = "ai_models")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiModel {
    /** 模型配置 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 显示名称，如"GPT-4o"、"DeepSeek-V3" */
    @Column(nullable = false, length = 100)
    private String name;

    /** 供应商：openai / deepseek / qwen 等 */
    @Column(length = 50)
    @Builder.Default
    private String provider = "openai";

    /** API 模型名，如 gpt-4o、deepseek-chat、qwen-max */
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /** API 请求地址 */
    @Column(name = "api_url", length = 500)
    private String apiUrl;

    /** API 密钥 */
    @Column(name = "api_key", length = 500)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    /** API 格式类型：openai / anthropic */
    @Column(name = "api_type", length = 20)
    @Builder.Default
    private String apiType = "openai";

    /** API 版本路径前缀，如 /v1 或空 */
    @Column(name = "api_version", length = 50)
    @Builder.Default
    private String apiVersion = "/v1";

    /** 最大输出 token 数 */
    @Column(name = "max_tokens")
    @Builder.Default
    private Integer maxTokens = 2048;

    /** 生成温度，控制随机性 */
    @Builder.Default
    private Double temperature = 0.7;

    /** 是否为默认模型（系统优先使用） */
    @Builder.Default
    @Column(name = "is_default")
    private Boolean isDefault = false;

    /** 模型类型：TEXT / IMAGE */
    @Column(name = "model_type", length = 10)
    @Builder.Default
    private String modelType = "TEXT";

    /** 是否启用 */
    @Builder.Default
    private Boolean enabled = true;

    /** 创建日期 */
    @Column(nullable = false)
    private LocalDate createdAt;

    /** 创建者用户 ID */
    @Column(nullable = false)
    private Long createdBy;
}
