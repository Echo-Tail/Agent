package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.SkillConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.SkillUploadResult;
import cafe.snails.ecomagents.model.AgentSkill;
import cafe.snails.ecomagents.model.Skills;
import cafe.snails.ecomagents.repository.AgentSkillRepository;
import cafe.snails.ecomagents.repository.SkillsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 技能服务 — 管理全局技能池和 per-Agent 技能绑定。
 * <p>技能内容以文件系统 workspace/skills/ 为 SSOT，Skills 表为元数据索引，
 * AgentSkill 表追踪 Agent 与技能的引用关系。</p>
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final WorkspaceConfig workspaceConfig;
    private final SkillConfig skillConfig;
    private final SkillsRepository skillsRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final ObjectMapper objectMapper;

    /** skills-lock.json 中时间字段使用的 ISO 日期时间格式。 */
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    /** GitHub 仓库 URL 匹配规则，支持 https://github.com/{owner}/{repo}[.git]。 */
    private static final Pattern GITHUB_REPO_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    /** GitHub tree URL 匹配规则，支持直接导入仓库中的 skills 子目录。 */
    private static final Pattern GITHUB_TREE_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)/tree/[^/]+/(skills/.+)$");

    /**
     * 获取全局技能池目录路径（workspace/skills/）。
     * <p>所有已导入的技能以子目录形式存放于此。</p>
     *
     * @return 技能池目录的 Path
     */
    public Path getSkillsDir() {
        return Path.of(workspaceConfig.getRoot(), "skills");
    }

    /**
     * 获取指定 Agent 的技能目录路径（workspace/agent-{id}/skills/）。
     * <p>Agent 绑定的技能会被复制到此目录，与全局技能池隔离。</p>
     *
     * @param agentId Agent ID
     * @return Agent 技能目录的 Path
     */
    public Path getAgentSkillsDir(Long agentId) {
        return Path.of(workspaceConfig.getRoot(), "agent-" + agentId, "skills");
    }

    /**
     * 列出全局技能池中的所有技能元数据。
     * <p>从 Skills 表读取，作为技能目录文件系统的索引。</p>
     *
     * @return 技能元数据列表
     */
    public ApiResponse<List<Skills>> listSkills() {
        return ApiResponse.success(skillsRepository.findAll());
    }

    /**
     * 获取指定 Agent 已绑定的技能名称列表。
     * <p>从 AgentSkill 关联表查询。</p>
     *
     * @param agentId Agent ID
     * @return 技能名称列表
     */
    public List<String> getSkillsForAgent(Long agentId) {
        return agentSkillRepository.findByAgentId(agentId)
                .stream()
                .map(AgentSkill::getSkillName)
                .collect(Collectors.toList());
    }

    /**
     * 同步 Agent 技能绑定到工作区目录。
     * <p>对比新旧技能列表：
     * <ul>
     *   <li>移除已解绑的技能目录并删除 AgentSkill 记录</li>
     *   <li>从全局技能池复制新增技能到 Agent workspace/agent-{id}/skills/</li>
     * </ul>
     * </p>
     *
     * @param agentId    Agent ID
     * @param skillNames 新的技能名称列表（全量替换）
     */
    @Transactional
    public void syncAgentSkillsToWorkspace(Long agentId, List<String> skillNames) {
        Path agentSkillsDir = getAgentSkillsDir(agentId);
        Path globalSkillsDir = getSkillsDir();

        List<AgentSkill> existing = agentSkillRepository.findByAgentId(agentId);
        Set<String> existingNames = existing.stream().map(AgentSkill::getSkillName).collect(Collectors.toSet());
        Set<String> newNames = skillNames != null ? new HashSet<>(skillNames) : new HashSet<>();

        // Remove skills no longer bound
        for (AgentSkill as : existing) {
            if (!newNames.contains(as.getSkillName())) {
                deleteRecursively(agentSkillsDir.resolve(as.getSkillName()));
                agentSkillRepository.delete(as);
                log.info("Skill {} unbound from agent {}", as.getSkillName(), agentId);
            }
        }

        // Add newly bound skills (copy from global pool)
        for (String name : newNames) {
            if (!existingNames.contains(name)) {
                Path source = globalSkillsDir.resolve(name);
                if (Files.isDirectory(source)) {
                    Path target = agentSkillsDir.resolve(name);
                    try {
                        Files.createDirectories(agentSkillsDir);
                        copyDirectory(source, target);
                        agentSkillRepository.save(AgentSkill.builder()
                                .agentId(agentId)
                                .skillName(name)
                                .copiedAt(LocalDateTime.now())
                                .build());
                        log.info("Skill {} copied to agent {}", name, agentId);
                    } catch (IOException e) {
                        log.warn("Failed to copy skill {} to agent {}: {}", name, agentId, e.getMessage());
                    }
                } else {
                    log.warn("Global skill {} not found at {}", name, source);
                }
            }
        }
    }

    /**
     * 从 GitHub 仓库 URL 导入技能。
     * <p>支持两种 URL 格式：
     * <ul>
     *   <li>仓库根 URL（扫描仓库中所有 SKILL.md 并导入）</li>
     *   <li>tree URL（仅导入指定路径下的技能）</li>
     * </ul>
     * 执行流程：git clone → 扫描 SKILL.md → 校验 frontmatter → 复制到全局技能池 →
     * 更新 skills-lock.json → 写入 DB。已存在的同名技能会被跳过。</p>
     *
     * @param url GitHub 仓库 URL
     * @return 导入结果（含成功/跳过/失败统计）
     */
    public ApiResponse<Void> importFromGithubUrl(String url) {
        String trimmed = url.trim();

        Matcher treeMatcher = GITHUB_TREE_PATTERN.matcher(trimmed);
        Matcher repoMatcher = GITHUB_REPO_PATTERN.matcher(trimmed);

        String owner, repo, skillSubPath;
        boolean isSingleSkill;

        if (treeMatcher.matches()) {
            owner = treeMatcher.group(1);
            repo = treeMatcher.group(2);
            skillSubPath = treeMatcher.group(3);
            isSingleSkill = true;
        } else if (repoMatcher.matches()) {
            owner = repoMatcher.group(1);
            repo = repoMatcher.group(2);
            skillSubPath = null;
            isSingleSkill = false;
        } else {
            return ApiResponse.error(400, "不支持的 URL 格式，请输入 GitHub 链接：https://github.com/{owner}/{repo}");
        }

        ApiResponse<Void> gitCheck = checkGitInstalled();
        if (gitCheck != null) return gitCheck;

        Map<String, JsonNode> existingSkills = readLockSkills();

        Path tempDir;
        String commitHash;
        String repoUrl = "https://github.com/" + owner + "/" + repo + ".git";
        try {
            tempDir = Files.createTempDirectory("skill-clone-");
            String ghProxy = skillConfig.getGhProxyUrl();
            String cloneUrl = (ghProxy != null && !ghProxy.isBlank())
                    ? ghProxy + "/" + repoUrl
                    : repoUrl;

            int exitCode = runGitCommand(null, "clone", "--depth", "1", cloneUrl, tempDir.toString());
            if (exitCode != 0) {
                deleteRecursively(tempDir);
                return ApiResponse.error(500, "Git 克隆失败，请检查网络连接和仓库地址");
            }

            commitHash = captureGitOutput(tempDir, "rev-parse", "HEAD");
            if (commitHash == null) {
                commitHash = "unknown";
            }
            log.info("Cloned repo {} at commit {}", repoUrl, commitHash);
        } catch (IOException e) {
            return ApiResponse.error(500, "创建临时目录失败: " + e.getMessage());
        }

        List<Path> skillMdFiles;
        try {
            Path scanRoot = tempDir;
            if (isSingleSkill && skillSubPath != null) {
                scanRoot = tempDir.resolve(skillSubPath);
                if (!Files.isDirectory(scanRoot)) {
                    deleteRecursively(tempDir);
                    return ApiResponse.error(400, "仓库中未找到指定路径: " + skillSubPath);
                }
            }

            skillMdFiles = findSkillMdFiles(scanRoot);
            if (skillMdFiles.isEmpty()) {
                deleteRecursively(tempDir);
                return ApiResponse.error(400, "未找到有效的 SKILL.md 文件");
            }
        } catch (IOException e) {
            deleteRecursively(tempDir);
            return ApiResponse.error(500, "扫描技能文件失败: " + e.getMessage());
        }

        Path skillsDir = getSkillsDir();
        try {
            Files.createDirectories(skillsDir);
        } catch (IOException e) {
            deleteRecursively(tempDir);
            return ApiResponse.error(500, "创建技能目录失败: " + e.getMessage());
        }

        List<ImportedSkill> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Path skillMd : skillMdFiles) {
            try {
                String content = Files.readString(skillMd);
                String name = extractFrontmatterField(content, "name");
                String description = extractFrontmatterField(content, "description");

                String validationError = validateSkillFormat(name, description);
                if (validationError != null) {
                    errors.add(skillMd.getFileName() + ": " + validationError);
                    continue;
                }

                if (existingSkills.containsKey(name)) {
                    skipped.add(name);
                    continue;
                }

                String relativeSkillPath = tempDir.relativize(skillMd).toString().replace("\\", "/");

                Path sourceDir = skillMd.getParent();
                Path targetDir = skillsDir.resolve(name);
                if (Files.exists(targetDir)) {
                    deleteRecursively(targetDir);
                }
                copyDirectory(sourceDir, targetDir);

                ImportedSkill is = new ImportedSkill();
                is.name = name;
                is.description = description;
                is.category = extractFrontmatterField(content, "category");
                is.source = owner + "/" + repo;
                is.sourceUrl = repoUrl;
                is.skillPath = relativeSkillPath;
                is.commitHash = commitHash;
                imported.add(is);

                log.info("Imported skill: {} from {}/{}", name, owner, repo);
            } catch (IOException e) {
                errors.add(skillMd.getFileName() + ": " + e.getMessage());
            }
        }

        if (!imported.isEmpty()) {
            writeLockSkills(existingSkills, imported);
        }

        deleteRecursively(tempDir);

        // Sync imported skills to DB
        if (!imported.isEmpty()) {
            syncNewSkillsToDb(imported);
        }

        StringBuilder msg = new StringBuilder();
        if (!imported.isEmpty()) {
            msg.append("成功导入 ").append(imported.size()).append(" 个技能：");
            msg.append(imported.stream().map(s -> s.name).collect(Collectors.joining("、")));
        }
        if (!skipped.isEmpty()) {
            if (!msg.isEmpty()) msg.append("；");
            msg.append("已存在跳过：").append(String.join("、", skipped));
            msg.append("（如需更新请先删除再导入）");
        }
        if (!errors.isEmpty()) {
            if (!msg.isEmpty()) msg.append("；");
            msg.append("以下文件格式有误已跳过：").append(String.join("; ", errors));
        }

        if (imported.isEmpty() && skipped.isEmpty() && !errors.isEmpty()) {
            return ApiResponse.error(400, "所有技能均未通过格式验证：\n" + String.join("\n", errors));
        }

        return ApiResponse.success(msg.toString(), null);
    }

    /**
     * 从上传的 ZIP 文件导入技能。
     * <p>安全校验：magic byte、Zip-Slip 防护、ZIP 炸弹防护
     * （100MB 总大小 / 10MB 单文件 / 1000 条目 / 100 倍压缩比）。</p>
     * <p>每个子目录必须包含有效的 SKILL.md（含 name + description frontmatter）。</p>
     *
     * @param file 上传的 ZIP 文件
     * @return 导入结果（成功数量/失败详情）
     */
    public ApiResponse<SkillUploadResult> uploadSkillZip(MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "上传文件为空");
        }
        if (file.getOriginalFilename() != null && !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            return ApiResponse.error(400, "仅支持 ZIP 文件");
        }

        Path skillsDir = getSkillsDir();
        try {
            Files.createDirectories(skillsDir);
            Path tempFile = Files.createTempFile("skill-upload-", ".zip");
            file.transferTo(tempFile.toFile());

            Path stagingDir = Files.createTempDirectory("skill-staging-");
            try {
                // 安全解压（含 magic byte、Zip-Slip、ZIP 炸弹校验）
                try {
                    extractZip(tempFile, stagingDir);
                } catch (ZipBombException e) {
                    return ApiResponse.error(400, e.getMessage());
                } catch (SecurityException e) {
                    return ApiResponse.error(400, e.getMessage());
                }

                List<String> importedNames = new ArrayList<>();
                List<SkillUploadResult.FailedItem> failedItems = new ArrayList<>();

                try (var dirs = Files.list(stagingDir)) {
                    dirs.filter(Files::isDirectory).forEach(dir -> {
                        String dirName = dir.getFileName().toString();
                        Path skillMd = findSkillMdInDir(dir);

                        // 校验：必须有 SKILL.md
                        if (skillMd == null) {
                            failedItems.add(new SkillUploadResult.FailedItem(dirName, "缺少 SKILL.md"));
                            return;
                        }

                        // 校验：frontmatter 中的 name、description
                        String content;
                        try {
                            content = Files.readString(skillMd);
                        } catch (IOException e) {
                            failedItems.add(new SkillUploadResult.FailedItem(dirName, "读取 SKILL.md 失败: " + e.getMessage()));
                            return;
                        }
                        String name = extractFrontmatterField(content, "name");
                        String description = extractFrontmatterField(content, "description");
                        String err = validateSkillFormat(name, description);
                        if (err != null) {
                            failedItems.add(new SkillUploadResult.FailedItem(dirName, err));
                            return;
                        }

                        // 导入到 skills 目录
                        Path target = skillsDir.resolve(dirName);
                        try {
                            if (Files.exists(target)) {
                                deleteRecursively(target);
                            }
                            copyDirectory(dir, target);
                            deleteRecursively(dir);
                        } catch (IOException e) {
                            failedItems.add(new SkillUploadResult.FailedItem(dirName, "移动目录失败: " + e.getMessage()));
                            return;
                        }

                        // 读取 metadata
                        String category = "other";
                        String cat = extractFrontmatterField(content, "category");
                        if (cat != null) category = cat;

                        ImportedSkill is = new ImportedSkill();
                        is.name = dirName;
                        is.description = description != null ? description : "";
                        is.category = category;
                        syncSingleSkillToDb(is);

                        importedNames.add(dirName);
                    });
                }

                int totalCount = importedNames.size() + failedItems.size();
                String message;
                if (importedNames.isEmpty() && failedItems.isEmpty()) {
                    message = "ZIP 文件中未找到有效的技能目录";
                } else if (failedItems.isEmpty()) {
                    message = "成功导入 " + importedNames.size() + " 个技能";
                } else if (importedNames.isEmpty()) {
                    message = failedItems.get(0).getReason();
                } else {
                    message = "成功导入 " + importedNames.size() + " 个技能，" + failedItems.size() + " 个失败";
                }

                SkillUploadResult result = SkillUploadResult.builder()
                        .successCount(importedNames.size())
                        .totalCount(totalCount)
                        .imported(importedNames)
                        .failed(failedItems)
                        .build();

                log.info("Skills uploaded from ZIP: {}, success={}, failed={}",
                        file.getOriginalFilename(), importedNames.size(), failedItems.size());
                return ApiResponse.success(message, result);
            } finally {
                Files.deleteIfExists(tempFile);
                deleteRecursively(stagingDir);
            }
        } catch (IOException e) {
            log.error("Failed to upload skill ZIP: {}", e.getMessage());
            return ApiResponse.error(500, "技能上传失败: " + e.getMessage());
        }
    }

    /** 将单个技能写入数据库 */
    private void syncSingleSkillToDb(ImportedSkill s) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Skills existing = skillsRepository.findByName(s.name).orElse(null);
            if (existing != null) {
                existing.setDescription(s.description);
                existing.setCategory(s.category);
                existing.setUpdatedAt(now);
                skillsRepository.save(existing);
            } else {
                Skills skill = Skills.builder()
                        .name(s.name)
                        .description(s.description)
                        .category(s.category != null ? s.category : "other")
                        .version("1.0")
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                skillsRepository.save(skill);
            }
        } catch (Exception e) {
            log.warn("Failed to sync skill {} to DB: {}", s.name, e.getMessage());
        }
    }

    /**
     * 删除技能。
     * <p>非强制模式：如有 Agent 引用则返回错误提示，不执行删除。
     * 强制模式：清除所有 Agent 绑定 → 删除全局技能目录和 DB 记录 → 更新锁文件。</p>
     *
     * @param name  技能名称
     * @param force 是否强制删除（同时清理所有 Agent 引用）
     * @return 操作结果
     */
    @Transactional
    public ApiResponse<Void> deleteSkill(String name, boolean force) {
        List<AgentSkill> bindings = agentSkillRepository.findBySkillName(name);
        if (!bindings.isEmpty() && !force) {
            return ApiResponse.error(400,
                    "该技能正在被 " + bindings.size() + " 个 Agent 使用，"
                            + "请先解绑后再删除，或使用 force=true 强制删除");
        }

        // Force delete: clean all agent bindings
        if (!bindings.isEmpty()) {
            for (AgentSkill as : bindings) {
                Path agentSkillDir = getAgentSkillsDir(as.getAgentId()).resolve(name);
                deleteRecursively(agentSkillDir);
                agentSkillRepository.delete(as);
                log.info("Force removed skill {} from agent {}", name, as.getAgentId());
            }
        }

        // Delete from filesystem
        Path skillDir = getSkillsDir().resolve(name);
        if (Files.isDirectory(skillDir)) {
            deleteRecursively(skillDir);
        }

        // Delete from DB
        skillsRepository.findByName(name).ifPresent(s -> skillsRepository.delete(s));
        removeFromLock(name);

        log.info("Skill deleted: {}", name);
        return ApiResponse.success("技能已删除", null);
    }

    /**
     * 从文件系统重新扫描全局技能池，将缺失的技能元数据补写到数据库。
     */
    @Transactional
    public void refreshIndex() {
        Path skillsDir = getSkillsDir();
        if (!Files.isDirectory(skillsDir)) {
            return;
        }

        try (var dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory)
                    .forEach(dir -> {
                        String name = dir.getFileName().toString();
                        if (skillsRepository.findByName(name).isEmpty()) {
                            Skills skill = parseSkillsFromDir(dir);
                            if (skill != null) {
                                skillsRepository.save(skill);
                            }
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan skills directory: {}", e.getMessage());
        }
    }

    // ===== Private: DB sync helpers =====

    private void syncNewSkillsToDb(List<ImportedSkill> imported) {
        LocalDateTime now = LocalDateTime.now();
        for (ImportedSkill s : imported) {
            Skills skill = Skills.builder()
                    .name(s.name)
                    .description(s.description != null ? s.description : "")
                    .category(s.category != null ? s.category : "other")
                    .version("1.0")
                    .sourceUrl(s.sourceUrl)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            skillsRepository.save(skill);
        }
    }

    /**
     * 从单个技能目录的 SKILL.md frontmatter 解析 Skills 元数据。
     */
    private Skills parseSkillsFromDir(Path skillDir) {
        String name = skillDir.getFileName().toString();
        Path skillMd = findSkillMdInDir(skillDir);
        if (skillMd == null || !Files.isReadable(skillMd)) {
            return null;
        }
        try {
            String content = Files.readString(skillMd);
            String description = extractFrontmatterField(content, "description");
            String category = extractFrontmatterField(content, "category");
            return Skills.builder()
                    .name(name)
                    .description(description != null ? description : "")
                    .category(category != null ? category : "other")
                    .version("1.0")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } catch (IOException e) {
            log.warn("Failed to read SKILL.md in {}: {}", name, e.getMessage());
            return null;
        }
    }

    // ===== Private helpers (unchanged from original) =====

    private ApiResponse<Void> checkGitInstalled() {
        try {
            int exitCode = runGitCommand(null, "--version");
            if (exitCode != 0) {
                return ApiResponse.error(400, "未检测到 Git，请先安装 Git: https://git-scm.com/downloads");
            }
            return null;
        } catch (Exception e) {
            return ApiResponse.error(400, "未检测到 Git，请先安装 Git: https://git-scm.com/downloads");
        }
    }

    private int runGitCommand(Path workingDir, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb;
        if (isWindows) {
            List<String> wrapped = new ArrayList<>();
            wrapped.add("cmd.exe");
            wrapped.add("/c");
            wrapped.add(String.join(" ", command));
            pb = new ProcessBuilder(wrapped);
        } else {
            pb = new ProcessBuilder(command);
        }
        if (workingDir != null) pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /**
     * 执行 Git 命令并捕获首行标准输出，失败时返回空。
     */
    private String captureGitOutput(Path workingDir, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(Arrays.asList(args));
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb;
            if (isWindows) {
                List<String> wrapped = new ArrayList<>();
                wrapped.add("cmd.exe");
                wrapped.add("/c");
                wrapped.add(String.join(" ", command));
                pb = new ProcessBuilder(wrapped);
            } else {
                pb = new ProcessBuilder(command);
            }
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = process.inputReader()) {
                boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    return reader.lines().findFirst().orElse(null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private List<Path> findSkillMdFiles(Path root) throws IOException {
        List<Path> results = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .forEach(results::add);
        }
        return results;
    }

    /**
     * 在指定技能目录根层查找 SKILL.md。
     */
    private Path findSkillMdInDir(Path dir) {
        try (var files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 校验技能 frontmatter 中必须存在的基础字段。
     */
    private String validateSkillFormat(String name, String description) {
        if (name == null || name.isBlank()) return "缺少 name frontmatter";
        if (description == null || description.isBlank()) return "缺少 description frontmatter";
        return null;
    }

    /**
     * 从 SKILL.md YAML frontmatter 中提取指定字段的简单字符串值。
     */
    private String extractFrontmatterField(String content, String field) {
        if (content == null || content.isBlank()) return null;
        if (!content.startsWith("---")) return null;
        int end = content.indexOf("---", 3);
        if (end < 0) return null;
        String frontmatter = content.substring(3, end);
        for (String line : frontmatter.split("\n")) {
            line = line.trim();
            if (line.startsWith(field + ":")) {
                String value = line.substring(field.length() + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * 获取技能导入锁文件路径。
     */
    private Path getLockFile() {
        return Path.of(workspaceConfig.getRoot(), "skills-lock.json");
    }

    /**
     * 读取 skills-lock.json 中的技能条目，保留原始 JSON 节点以便无损回写。
     */
    private Map<String, JsonNode> readLockSkills() {
        Path lockFile = getLockFile();
        if (!Files.isReadable(lockFile)) return new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(lockFile.toFile());
            JsonNode skills = root.get("skills");
            if (skills != null && skills.isObject()) {
                Map<String, JsonNode> result = new LinkedHashMap<>();
                skills.fieldNames().forEachRemaining(name -> result.put(name, skills.get(name)));
                return result;
            }
        } catch (IOException e) {
            log.warn("Failed to read skills-lock.json: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将新增导入技能合并写入 skills-lock.json。
     */
    private void writeLockSkills(Map<String, JsonNode> existing, List<ImportedSkill> imported) {
        Path lockFile = getLockFile();
        try {
            Files.createDirectories(lockFile.getParent());
            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", 2);
            ObjectNode skillsNode = root.putObject("skills");
            for (Map.Entry<String, JsonNode> entry : existing.entrySet()) {
                skillsNode.set(entry.getKey(), entry.getValue());
            }
            String now = LocalDateTime.now().format(ISO_FORMAT);
            for (ImportedSkill s : imported) {
                ObjectNode entry = skillsNode.putObject(s.name);
                entry.put("source", s.source);
                entry.put("sourceType", "github");
                entry.put("sourceUrl", s.sourceUrl);
                entry.put("skillPath", s.skillPath);
                entry.put("commitHash", s.commitHash);
                entry.put("installedAt", now);
                entry.put("updatedAt", now);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(lockFile.toFile(), root);
            log.info("Skills lock updated: {} new skills", imported.size());
        } catch (IOException e) {
            log.error("Failed to write skills-lock.json: {}", e.getMessage());
        }
    }

    /**
     * 从 skills-lock.json 中删除指定技能记录。
     */
    private void removeFromLock(String skillName) {
        Map<String, JsonNode> existing = readLockSkills();
        if (!existing.containsKey(skillName)) return;
        existing.remove(skillName);
        Path lockFile = getLockFile();
        try {
            Files.createDirectories(lockFile.getParent());
            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", 2);
            ObjectNode skillsNode = root.putObject("skills");
            for (Map.Entry<String, JsonNode> entry : existing.entrySet()) {
                skillsNode.set(entry.getKey(), entry.getValue());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(lockFile.toFile(), root);
        } catch (IOException e) {
            log.error("Failed to remove skill {} from lock: {}", skillName, e.getMessage());
        }
    }

    /**
     * 安全解压 ZIP 文件，包含多层防护：
     * <ul>
     *   <li>Magic byte 校验（PK\x03\x04）</li>
     *   <li>Zip-Slip / 目录遍历防护（normalize + startsWith）</li>
     *   <li>ZIP 炸弹防护（总大小 100MB、单文件 10MB、条目数 1000、压缩比 100 倍）</li>
     * </ul>
     *
     * @throws ZipBombException 当检测到 ZIP 炸弹或超出安全限制时抛出
     * @throws SecurityException 当检测到目录遍历攻击时抛出
     */
    private void extractZip(Path zipFile, Path targetDir) throws IOException, ZipBombException {
        // 1. Magic byte 校验
        byte[] header = new byte[4];
        try (var is = Files.newInputStream(zipFile)) {
            int read = is.read(header);
            if (read < 4 || header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                throw new SecurityException("文件格式不正确，不是有效的 ZIP 文件");
            }
        }

        // 2. 安全解压
        long totalUncompressed = 0;
        long totalCompressed = 0;
        int entryCount = 0;
        long maxTotalUncompressed = 100L * 1024 * 1024; // 100 MB
        long maxEntrySize = 10L * 1024 * 1024;          // 10 MB
        int maxEntryCount = 1000;
        int maxCompressionRatio = 100;

        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > maxEntryCount) {
                    throw new ZipBombException("ZIP 文件条目数超过限制（最多 " + maxEntryCount + " 个条目）");
                }

                // Zip-Slip 防护
                String entryName = entry.getName();
                if (entryName == null || entryName.contains("..")) {
                    log.warn("Zip-Slip detected and skipped: {}", entryName);
                    zis.closeEntry();
                    continue;
                }
                Path resolved = targetDir.resolve(entryName).normalize();
                if (!resolved.startsWith(targetDir.normalize())) {
                    log.warn("Zip-Slip detected and skipped: {} resolved to {}", entryName, resolved);
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    zis.closeEntry();
                    continue;
                }

                byte[] buffer = new byte[8192];
                long entryUncompressed = 0;

                Files.createDirectories(resolved.getParent());
                try (var os = Files.newOutputStream(resolved)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        totalCompressed += len;   // ZipInputStream 返回解压后的数据
                        entryUncompressed += len;
                        totalUncompressed += len;

                        // 单文件大小限制
                        if (entryUncompressed > maxEntrySize) {
                            Files.deleteIfExists(resolved);
                            throw new ZipBombException("文件 '" + entryName + "' 解压后超过 " + (maxEntrySize / 1024 / 1024) + " MB 限制");
                        }

                        // 总大小限制
                        if (totalUncompressed > maxTotalUncompressed) {
                            throw new ZipBombException("ZIP 解压总大小超过 " + (maxTotalUncompressed / 1024 / 1024) + " MB 限制");
                        }

                        os.write(buffer, 0, len);
                    }
                }

                // 压缩比检测（仅对非空条目）
                long compressedSize = entry.getCompressedSize();
                if (compressedSize > 0 && entryUncompressed > 0) {
                    long ratio = entryUncompressed / compressedSize;
                    if (ratio > maxCompressionRatio) {
                        Files.deleteIfExists(resolved);
                        throw new ZipBombException("ZIP 炸弹检测：文件 '" + entryName + "' 压缩比 " + ratio + " 倍超过限制");
                    }
                }

                zis.closeEntry();
            }
        }
    }

    /**
     * ZIP 炸弹异常，由 extractZip 在检测到安全威胁时抛出。
     */
    private static class ZipBombException extends Exception {
        /** 使用安全检查失败原因创建异常。 */
        public ZipBombException(String message) {
            super(message);
        }
    }

    /**
     * 递归删除目录及其内容；删除失败时记录警告并继续处理其它文件。
     */
    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException e) {
                            log.warn("Failed to delete {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to walk directory {}: {}", dir, e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                Path dst = target.resolve(source.relativize(src));
                try {
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.warn("Failed to copy {} to {}: {}", src, dst, e.getMessage());
                }
            });
        }
    }

    private static class ImportedSkill {
        String name;
        String description;
        String category;
        String source;
        String sourceUrl;
        String skillPath;
        String commitHash;
    }
}
