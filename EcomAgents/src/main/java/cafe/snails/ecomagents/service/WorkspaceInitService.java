package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.model.Agent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent workspace 目录结构初始化服务。
 * <p>每个 Agent 有独立的 workspace/agent-{id}/ 目录，包含 AGENTS.md、sessions/、knowledge/ 等子目录。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkspaceInitService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(WorkspaceInitService.class);

    /** 工作区根目录配置。 */
    private final WorkspaceConfig workspaceConfig;

    /**
     * 为指定 Agent 初始化 workspace 目录结构。
     * 仅当目录不存在时创建，已存在则跳过（幂等）。
     */
    public void initWorkspace(Agent agent) {
        Path agentDir = getAgentDir(agent.getId());
        try {
            Files.createDirectories(agentDir);

            // Always rewrite AGENTS.md so existing agents get updated instructions
            Files.writeString(agentDir.resolve("AGENTS.md"), buildAgentsMd(agent));
            writeIfNotExists(agentDir.resolve("MEMORY.md"), "# Agent Memory\n\n");

            Files.createDirectories(agentDir.resolve("sessions"));
            Files.createDirectories(agentDir.resolve("knowledge"));
            // Create empty skills/ directory (skills are copied per-agent via SkillService)
            Files.createDirectories(agentDir.resolve("skills"));
            Files.createDirectories(agentDir.resolve("subagents"));

            Path knowledgeMd = agentDir.resolve("knowledge/KNOWLEDGE.md");
            writeIfNotExists(knowledgeMd, "# Knowledge Base\n\n");

            log.info("Workspace initialized for agent {} at {}", agent.getName(), agentDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize workspace for agent " + agent.getId(), e);
        }
    }

    /**
     * 更新 AGENTS.md 文件内容（Agent systemPrompt 变更时调用）。
     */
    public void updateAgentsMd(Long agentId, String systemPrompt) {
        Path agentsMd = getAgentDir(agentId).resolve("AGENTS.md");
        try {
            Files.writeString(agentsMd, buildAgentsMd(systemPrompt, agentId));
            log.debug("AGENTS.md updated for agent {}", agentId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update AGENTS.md for agent " + agentId, e);
        }
    }

    /**
     * 更新 knowledge/KNOWLEDGE.md 文件内容（知识库关联变更时调用）。
     */
    public void updateKnowledgeMd(Long agentId, String knowledgeContent) {
        Path knowledgeMd = getAgentDir(agentId).resolve("knowledge/KNOWLEDGE.md");
        try {
            String content = knowledgeContent != null && !knowledgeContent.isBlank()
                    ? "# Knowledge Base\n\n" + knowledgeContent
                    : "# Knowledge Base\n\n";
            Files.writeString(knowledgeMd, content);
            log.debug("KNOWLEDGE.md updated for agent {}", agentId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update KNOWLEDGE.md for agent " + agentId, e);
        }
    }

    /**
     * 删除 Agent 的 workspace 目录（Agent 删除时调用）。
     */
    public void deleteWorkspace(Long agentId) {
        Path agentDir = getAgentDir(agentId);
        if (Files.exists(agentDir)) {
            try {
                deleteRecursively(agentDir);
                log.info("Workspace deleted for agent {}", agentId);
            } catch (IOException e) {
                log.warn("Failed to delete workspace for agent {}: {}", agentId, e.getMessage());
            }
        }
    }

    /** 获取 Agent workspace 的 Path */
    public Path getAgentDir(Long agentId) {
        return Path.of(workspaceConfig.getRoot(), "agent-" + agentId);
    }

    // ===== Private helpers =====

    private String buildAgentsMd(Agent agent) {
        return buildAgentsMd(agent.getSystemPrompt(), agent.getId());
    }

    /**
     * 构建 Agent 工作区 AGENTS.md 内容，包含系统提示词、文件输出规范和知识库检索规则。
     */
    private String buildAgentsMd(String systemPrompt, Long agentId) {
        String base = (systemPrompt != null ? systemPrompt : "You are a helpful AI assistant.");
        return base
                + "\n\n## File Sending Capability"
                + "\n\nWhen the user asks you to generate a downloadable file (such as Markdown, CSV, JSON, TXT, HTML, etc.), "
                + "wrap the file content in a `<file name=\"filename.ext\">` tag like this:"
                + "\n\n```"
                + "\nHere is the document you requested:"
                + "\n<file name=\"report.md\">"
                + "\n# Report Title"
                + "\n\nContent here..."
                + "\n</file>"
                + "\n```"
                + "\n\nThe file content will be automatically saved and provided as a downloadable link to the user. "
                + "The text outside the `<file>` tags will be displayed normally as your response."
                + "\n\nIMPORTANT: Do NOT create markdown links to workspace paths or local file paths in your response. "
                + "Those paths are not accessible to the user. Just describe the filename in plain text."
                + "\n\n[Knowledge Base Rules]\n"
                + "This agent has knowledge bases bound to it. When the user asks about information in those knowledge bases:\n"
                + "1. ALWAYS use the retrieve_knowledge tool to search for relevant content\n"
                + "2. If results are incomplete, try different search keywords\n"
                + "3. NEVER use file tools (list_files, read_file, execute, etc.) to directly access workspace files\n"
                + "4. Knowledge base files are stored in the workspace directory but are NOT directly accessible\n"
                + "5. If multiple searches still cannot find complete information, honestly report what was found"
                + "\n\nSupported file types: .md, .txt, .json, .csv, .xml, .html, .pdf"
                + "\n\n<!-- Agent ID: " + agentId + " -->\n";
    }

    private void writeIfNotExists(Path file, String content) throws IOException {
        if (Files.notExists(file)) {
            Files.writeString(file, content);
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}: {}", p, e.getMessage());
                        }
                    });
        }
    }
}
