package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手（Agent）实体，映射 agents 表。
 * <p>每个 Agent 包含系统提示词、工具列表、知识库关联、模型配置等完整定义。</p>
 */
@Entity
@Table(name = "agents")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {
    /** Agent ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Agent 名称，如"客服助手"、"订单管家" */
    @Column(nullable = false, length = 100)
    private String name;

    /** Bootstrap Icons 图标类名，前端展示用 */
    @Column(length = 50)
    @Builder.Default
    private String icon = "bi-robot";

    /** 自定义头像图片 URL，优先级高于 icon */
    @Column(length = 500)
    private String avatar;

    /** 功能描述文本 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 标签列表，用于前端分类筛选 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_tags", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "tag", length = 50)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /** 系统提示词，定义 Agent 的角色和行为 */
    @Column(columnDefinition = "TEXT", name = "system_prompt")
    private String systemPrompt;

    /** 首次对话时的欢迎语 */
    @Column(length = 200)
    private String greeting;

    /** 启用的工具列表（web_search / image_generation 等） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_tools", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "tool", length = 50)
    private List<String> tools;

    /** 绑定的技能名称列表（从全局技能池复制到 workspace） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_skill_names", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "skill_name", length = 100)
    private List<String> skills;

    /** 关联的知识库 ID 列表 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_knowledge_bases", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "kb_id")
    private List<Long> knowledgeBaseIds;

    /** 使用的 AI 模型 ID（关联 ai_models 表） */
    @Column(name = "model_id")
    private Long modelId;

    /** 状态：active / disabled */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

    /** 是否为系统 Agent（不在用户列表中展示） */
    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;

    /** 创建日期 */
    @Column(nullable = false)
    private LocalDate createdAt;

    /** 创建者用户 ID */
    @Column(nullable = false)
    private Long createdBy;

    /** RAG 检索模式：GENERIC（自动检索）/ AGENTIC（Agent 自主检索） */
    @Column(length = 20)
    @Builder.Default
    private String ragMode = "AGENTIC";
}
