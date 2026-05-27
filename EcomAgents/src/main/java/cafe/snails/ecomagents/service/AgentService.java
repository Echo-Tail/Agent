package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.AgentSkillRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.KnowledgeBaseRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent（AI 助手）业务逻辑，支持 CRUD、部分更新、技能同步和范围过滤。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository agentRepository;
    private final AiModelRepository aiModelRepository;
    private final WorkspaceInitService workspaceInitService;
    private final SkillService skillService;
    private final AgentSkillRepository agentSkillRepository;
    private final ToolConfigRepository toolConfigRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 获取 Agent 列表（支持按用户范围过滤）。
     *
     * @param userId 当前用户 ID
     * @param scope  my-仅自己创建的 / plaza-其他人创建的 / null-全部（管理员用）
     */
    public ApiResponse<List<Agent>> listAgents(Long userId, String scope) {
        List<Agent> agents;
        if ("my".equals(scope)) {
            agents = agentRepository.findByCreatedByAndIsSystemFalse(userId);
        } else if ("plaza".equals(scope)) {
            agents = agentRepository.findByCreatedByNotAndIsSystemFalse(userId);
        } else {
            agents = agentRepository.findAll().stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getIsSystem()))
                    .collect(Collectors.toList());
        }
        return ApiResponse.success(agents);
    }

    /** 获取所有 Agent（无过滤，保留向后兼容） */
    public ApiResponse<List<Agent>> listAgents() {
        return ApiResponse.success(agentRepository.findAll());
    }

    /** 根据 ID 获取 Agent 详情 */
    public ApiResponse<Agent> getAgent(Long id) {
        return agentRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "Agent不存在"));
    }

    /** 获取或初始化系统 Agent */
    @Transactional
    public ApiResponse<Agent> getOrInitSystemAgent() {
        return agentRepository.findByIsSystemTrue()
                .map(agent -> {
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
                    workspaceInitService.initWorkspace(saved);
                    return ApiResponse.success(saved);
                });
    }

    /** 创建新 Agent */
    @Transactional
    public ApiResponse<Agent> createAgent(Agent agent, Long userId) {
        if (agent.getModelId() == null) {
            return ApiResponse.error(400, "模型不能为空");
        }
        agent.setId(null);
        agent.setCreatedAt(LocalDate.now());
        agent.setCreatedBy(userId);
        if (agent.getStatus() == null) agent.setStatus("active");
        if (agent.getRagMode() == null) agent.setRagMode("AGENTIC");
        agent.setTools(filterEnabledToolIds(agent.getTools(), null));
        agent.setKnowledgeBaseIds(filterExistingKnowledgeBaseIds(agent.getKnowledgeBaseIds(), null));
        Agent saved = agentRepository.save(agent);

        workspaceInitService.initWorkspace(saved);

        // Sync skills to agent workspace
        if (agent.getSkills() != null) {
            skillService.syncAgentSkillsToWorkspace(saved.getId(), agent.getSkills());
        }

        log.info("Agent created: id={}, name={}", saved.getId(), saved.getName());
        return ApiResponse.success("创建成功", saved);
    }

    /** 部分更新 Agent */
    @Transactional
    public ApiResponse<Agent> updateAgent(Long id, Agent update, Long userId) {
        return agentRepository.findById(id)
                .map(existing -> {
                    if (!hasAgentPermission(existing, userId)) {
                        return ApiResponse.<Agent>error(403, "没有权限修改此 Agent");
                    }
                    boolean promptChanged = false;
                    boolean knowledgeChanged = false;
                    boolean skillsChanged = false;

                    if (update.getName() != null) existing.setName(update.getName());
                    if (update.getIcon() != null) existing.setIcon(update.getIcon());
                    if (update.getAvatar() != null) existing.setAvatar(update.getAvatar());
                    if (update.getDescription() != null) existing.setDescription(update.getDescription());
                    if (update.getSystemPrompt() != null) {
                        existing.setSystemPrompt(update.getSystemPrompt());
                        promptChanged = true;
                    }
                    if (update.getGreeting() != null) existing.setGreeting(update.getGreeting());
                    if (update.getTags() != null) existing.setTags(update.getTags());
                    if (update.getTools() != null) {
                        existing.setTools(filterEnabledToolIds(update.getTools(), id));
                    }
                    if (update.getSkills() != null) {
                        existing.setSkills(update.getSkills());
                        skillsChanged = true;
                    }
                    if (update.getKnowledgeBaseIds() != null) {
                        existing.setKnowledgeBaseIds(filterExistingKnowledgeBaseIds(update.getKnowledgeBaseIds(), id));
                        knowledgeChanged = true;
                    }
                    if (update.getModelId() != null) existing.setModelId(update.getModelId());
                    if (update.getStatus() != null) existing.setStatus(update.getStatus());
                    if (update.getRagMode() != null) existing.setRagMode(update.getRagMode());
                    Agent saved = agentRepository.save(existing);

                    if (promptChanged) {
                        workspaceInitService.updateAgentsMd(id, saved.getSystemPrompt());
                    }
                    if (knowledgeChanged) {
                        workspaceInitService.updateKnowledgeMd(id, null);
                    }
                    if (skillsChanged && saved.getSkills() != null) {
                        skillService.syncAgentSkillsToWorkspace(id, saved.getSkills());
                    }

                    return ApiResponse.success("更新成功", saved);
                })
                .orElse(ApiResponse.error(404, "Agent不存在"));
    }

    /** 删除 Agent */
    @Transactional
    public ApiResponse<Agent> deleteAgent(Long id, Long userId) {
        return agentRepository.findById(id)
                .map(agent -> {
                    if (!hasAgentPermission(agent, userId)) {
                        return ApiResponse.<Agent>error(403, "没有权限删除此 Agent");
                    }
                    agentSkillRepository.deleteByAgentId(id);
                    agentRepository.delete(agent);
                    workspaceInitService.deleteWorkspace(id);
                    log.info("Agent deleted: id={}, name={}", id, agent.getName());
                    return ApiResponse.success("删除成功", agent);
                })
                .orElse(ApiResponse.error(404, "Agent不存在"));
    }

    /** 上传 Agent 头像 */
    @Transactional
    public ApiResponse<String> uploadAvatar(Long id, MultipartFile file, Long userId) {
        var opt = agentRepository.findById(id);
        if (opt.isEmpty()) {
            return ApiResponse.error(404, "Agent不存在");
        }
        Agent agent = opt.get();
        if (!hasAgentPermission(agent, userId)) {
            return ApiResponse.error(403, "没有权限修改此 Agent");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!Set.of("jpg", "jpeg", "png", "gif", "webp").contains(ext)) {
            return ApiResponse.error(400, "仅支持 JPG/PNG/GIF/WEBP 格式");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            return ApiResponse.error(400, "头像文件不能超过 2MB");
        }

        try {
            Path uploadPath = Paths.get("./uploads/agent-avatars").toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedName = "agent-" + id + "-" + UUID.randomUUID() + "." + ext;
            Path targetPath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = "/uploads/agent-avatars/" + storedName;
            agent.setAvatar(avatarUrl);
            agentRepository.save(agent);

            return ApiResponse.success("头像上传成功", avatarUrl);
        } catch (IOException e) {
            return ApiResponse.error(500, "头像上传失败: " + e.getMessage());
        }
    }

    /** 检查当前用户是否有权限操作该 Agent */
    public boolean hasAgentPermission(Agent agent, Long userId) {
        if (agent.getCreatedBy().equals(userId)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private List<String> filterEnabledToolIds(List<String> toolIds, Long agentId) {
        if (toolIds == null) return null;
        if (toolIds.isEmpty()) return new ArrayList<>();

        Set<String> enabledToolIds = toolConfigRepository.findAllById(toolIds).stream()
                .filter(tool -> Boolean.TRUE.equals(tool.getEnabled()))
                .map(tool -> tool.getId())
                .collect(Collectors.toSet());

        List<String> filtered = new ArrayList<>(toolIds.stream()
                .filter(id -> id != null && enabledToolIds.contains(id))
                .distinct()
                .toList());

        Set<String> skipped = new HashSet<>(toolIds);
        skipped.removeAll(filtered);
        skipped.forEach(id -> log.warn(
                "Agent {} skipped disabled or unknown tool binding during save: toolId={}",
                agentId != null ? agentId : "(new)", id));
        return filtered;
    }

    private List<Long> filterExistingKnowledgeBaseIds(List<Long> knowledgeBaseIds, Long agentId) {
        if (knowledgeBaseIds == null) return null;
        if (knowledgeBaseIds.isEmpty()) return new ArrayList<>();

        Set<Long> existingIds = knowledgeBaseRepository.findAllById(knowledgeBaseIds).stream()
                .map(kb -> kb.getId())
                .collect(Collectors.toSet());

        List<Long> filtered = new ArrayList<>(knowledgeBaseIds.stream()
                .filter(id -> id != null && existingIds.contains(id))
                .distinct()
                .toList());

        Set<Long> skipped = new HashSet<>(knowledgeBaseIds);
        skipped.removeAll(filtered);
        skipped.forEach(id -> log.warn(
                "Agent {} removed missing knowledge base binding during save: knowledgeBaseId={}",
                agentId != null ? agentId : "(new)", id));
        return filtered;
    }
}
