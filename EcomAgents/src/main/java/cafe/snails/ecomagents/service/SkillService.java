package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.SkillConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.dto.ApiResponse;
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

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final WorkspaceConfig workspaceConfig;
    private final SkillConfig skillConfig;
    private final SkillsRepository skillsRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    private static final Pattern GITHUB_REPO_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    private static final Pattern GITHUB_TREE_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)/tree/[^/]+/(skills/.+)$");

    /**
     * 获取全局技能目录路径。
     */
    public Path getSkillsDir() {
        return Path.of(workspaceConfig.getRoot(), "skills");
    }

    /**
     * 获取 Agent 的技能目录路径。
     */
    public Path getAgentSkillsDir(Long agentId) {
        return Path.of(workspaceConfig.getRoot(), "agent-" + agentId, "skills");
    }

    /**
     * 列出所有技能（从 Skills 表读取，索引表作为过渡）。
     */
    public ApiResponse<List<Skills>> listSkills() {
        return ApiResponse.success(skillsRepository.findAll());
    }

    /**
     * 获取指定 Agent 已绑定的技能名称列表。
     */
    public List<String> getSkillsForAgent(Long agentId) {
        return agentSkillRepository.findByAgentId(agentId)
                .stream()
                .map(AgentSkill::getSkillName)
                .collect(Collectors.toList());
    }

    /**
     * 同步 Agent 技能绑定到 workspace。
     * <p>对比新旧技能列表，移除解绑技能，复制新增技能到 Agent workspace。</p>
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
     * 从 GitHub URL 导入技能。
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
     */
    public ApiResponse<Void> uploadSkillZip(MultipartFile file) {
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
                extractZip(tempFile, stagingDir);

                List<Path> invalidDirs = new ArrayList<>();
                List<String> formatErrors = new ArrayList<>();
                try (var dirs = Files.list(stagingDir)) {
                    dirs.filter(Files::isDirectory).forEach(dir -> {
                        Path skillMd = findSkillMdInDir(dir);
                        if (skillMd == null) {
                            invalidDirs.add(dir);
                        } else {
                            try {
                                String content = Files.readString(skillMd);
                                String name = extractFrontmatterField(content, "name");
                                String description = extractFrontmatterField(content, "description");
                                String err = validateSkillFormat(name, description);
                                if (err != null) {
                                    formatErrors.add(dir.getFileName() + ": " + err);
                                }
                            } catch (IOException e) {
                                formatErrors.add(dir.getFileName() + ": " + e.getMessage());
                            }
                        }
                    });
                }

                if (!invalidDirs.isEmpty()) {
                    StringBuilder sb = new StringBuilder("以下技能目录缺少 SKILL.md：");
                    for (Path d : invalidDirs) {
                        sb.append(d.getFileName().toString()).append(" ");
                    }
                    return ApiResponse.error(400, sb.toString().trim());
                }

                if (!formatErrors.isEmpty()) {
                    return ApiResponse.error(400,
                            "以下技能目录 SKILL.md 格式有误（需要 name + description frontmatter）：\n"
                                    + String.join("\n", formatErrors));
                }

                List<ImportedSkill> imported = new ArrayList<>();
                try (var dirs = Files.list(stagingDir)) {
                    dirs.filter(Files::isDirectory).forEach(dir -> {
                        Path target = skillsDir.resolve(dir.getFileName());
                        try {
                            if (Files.exists(target)) {
                                deleteRecursively(target);
                            }
                            Files.move(dir, target);
                            String name = dir.getFileName().toString();
                            Path skillMd = findSkillMdInDir(target);
                            String description = "";
                            String category = "other";
                            if (skillMd != null) {
                                String content = Files.readString(skillMd);
                                String desc = extractFrontmatterField(content, "description");
                                String cat = extractFrontmatterField(content, "category");
                                if (desc != null) description = desc;
                                if (cat != null) category = cat;
                            }
                            ImportedSkill is = new ImportedSkill();
                            is.name = name;
                            is.description = description;
                            is.category = category;
                            imported.add(is);
                        } catch (IOException e) {
                            log.warn("Failed to move skill {}: {}", dir.getFileName(), e.getMessage());
                        }
                    });
                }

                if (!imported.isEmpty()) {
                    syncNewSkillsToDb(imported);
                }
                log.info("Skills uploaded from ZIP: {}", file.getOriginalFilename());
                return ApiResponse.success("技能上传成功", null);
            } finally {
                Files.deleteIfExists(tempFile);
                deleteRecursively(stagingDir);
            }
        } catch (IOException e) {
            log.error("Failed to upload skill ZIP: {}", e.getMessage());
            return ApiResponse.error(500, "技能上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除技能。如有 Agent 引用，返回警告信息。
     *
     * @param name  技能名称
     * @param force 为 true 时强制删除（同时清理所有 Agent 引用）
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
            Path globalSkillsDir = getSkillsDir();
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

    private String validateSkillFormat(String name, String description) {
        if (name == null || name.isBlank()) return "缺少 name frontmatter";
        if (description == null || description.isBlank()) return "缺少 description frontmatter";
        return null;
    }

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

    private Path getLockFile() {
        return Path.of(workspaceConfig.getRoot(), "skills-lock.json");
    }

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

    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

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
