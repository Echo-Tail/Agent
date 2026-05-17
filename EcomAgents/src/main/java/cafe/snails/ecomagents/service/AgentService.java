package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Agent（AI 助手）业务逻辑，支持 CRUD 和部分更新。
 * <p>
 * Agent 生命周期关联操作：
 * <ul>
 *   <li>createAgent → 初始化 workspace 目录</li>
 *   <li>updateAgent → 同步 AGENTS.md、knowledge/KNOWLEDGE.md</li>
 *   <li>deleteAgent → 清理 workspace 目录和 HarnessAgent 缓存</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository agentRepository;
    private final AiModelRepository aiModelRepository;
    private final WorkspaceInitService workspaceInitService;

    /** 获取所有 Agent */
    public ApiResponse<List<Agent>> listAgents() {
        return ApiResponse.success(agentRepository.findAll());
    }

    /** 根据 ID 获取 Agent 详情 */
    public ApiResponse<Agent> getAgent(Long id) {
        return agentRepository.findById(id)
                .map(agent -> ApiResponse.success(agent))
                .orElse(ApiResponse.error(404, "Agent不存在"));
    }

    /**
     * 获取或初始化系统 Agent（默认模型绑定助手）。
     * 系统 Agent 不在用户列表中展示，用于直接聊天模式。
     */
    @Transactional
    public ApiResponse<Agent> getOrInitSystemAgent() {
        return agentRepository.findByIsSystemTrue()
                .map(agent -> {
                    // Update model if default model changed
                    aiModelRepository.findByIsDefaultTrue().ifPresent(model -> {
                        if (!model.getId().equals(agent.getModelId())) {
                            agent.setModelId(model.getId());
                            agentRepository.save(agent);
                        }
                    });
                    return ApiResponse.success(agent);
                })
                .orElseGet(() -> {
                    Agent agent = Agent.builder()
                            .name("默认助手")
                            .icon("bi-robot")
                            .isSystem(true)
                            .description("系统默认AI助手")
                            .systemPrompt("你是一个有用的AI助手。")
                            .greeting("你好！需要我为你做些什么？")
                            .status("active")
                            .createdAt(LocalDate.now())
                            .createdBy(0L)
                            .build();
                    aiModelRepository.findByIsDefaultTrue()
                            .ifPresent(model -> agent.setModelId(model.getId()));
                    Agent saved = agentRepository.save(agent);
                    // 系统 Agent 也需要 workspace
                    workspaceInitService.initWorkspace(saved);
                    return ApiResponse.success(saved);
                });
    }

    /** 创建新 Agent，自动填充创建时间和创建人，并初始化 workspace */
    @Transactional
    public ApiResponse<Agent> createAgent(Agent agent) {
        agent.setId(null);
        agent.setCreatedAt(LocalDate.now());
        agent.setCreatedBy(1L);
        if (agent.getStatus() == null) agent.setStatus("active");
        Agent saved = agentRepository.save(agent);

        // 初始化 workspace 目录
        workspaceInitService.initWorkspace(saved);

        log.info("Agent created: id={}, name={}", saved.getId(), saved.getName());
        return ApiResponse.success("创建成功", saved);
    }

    /** 部分更新 Agent，同步 workspace 文件 */
    @Transactional
    public ApiResponse<Agent> updateAgent(Long id, Agent update) {
        return agentRepository.findById(id)
                .map(existing -> {
                    boolean promptChanged = false;
                    boolean knowledgeChanged = false;

                    if (update.getName() != null) existing.setName(update.getName());
                    if (update.getIcon() != null) existing.setIcon(update.getIcon());
                    if (update.getDescription() != null) existing.setDescription(update.getDescription());
                    if (update.getSystemPrompt() != null) {
                        existing.setSystemPrompt(update.getSystemPrompt());
                        promptChanged = true;
                    }
                    if (update.getGreeting() != null) existing.setGreeting(update.getGreeting());
                    if (update.getTags() != null) existing.setTags(update.getTags());
                    if (update.getTools() != null) existing.setTools(update.getTools());
                    if (update.getKnowledgeBaseIds() != null) {
                        existing.setKnowledgeBaseIds(update.getKnowledgeBaseIds());
                        knowledgeChanged = true;
                    }
                    if (update.getModelId() != null) existing.setModelId(update.getModelId());
                    if (update.getStatus() != null) existing.setStatus(update.getStatus());
                    Agent saved = agentRepository.save(existing);

                    // 同步 workspace 文件
                    if (promptChanged) {
                        workspaceInitService.updateAgentsMd(id, saved.getSystemPrompt());
                    }
                    if (knowledgeChanged) {
                        // 知识库内容变更时清空 KNOWLEDGE.md（后续由知识库模块负责更新）
                        workspaceInitService.updateKnowledgeMd(id, null);
                    }

                    return ApiResponse.success("更新成功", saved);
                })
                .orElse(ApiResponse.error(404, "Agent不存在"));
    }

    /** 删除 Agent，清理 workspace 和相关缓存 */
    @Transactional
    public ApiResponse<Agent> deleteAgent(Long id) {
        return agentRepository.findById(id)
                .map(agent -> {
                    agentRepository.delete(agent);

                    // 清理 workspace 目录
                    workspaceInitService.deleteWorkspace(id);

                    log.info("Agent deleted: id={}, name={}", id, agent.getName());
                    return ApiResponse.success("删除成功", agent);
                })
                .orElse(ApiResponse.error(404, "Agent不存在"));
    }
}
