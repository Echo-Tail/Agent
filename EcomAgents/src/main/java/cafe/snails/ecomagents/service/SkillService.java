package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.SkillConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.SkillIndex;
import cafe.snails.ecomagents.repository.SkillIndexRepository;
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
 * 基于文件系统的技能服务。
 * <p>技能存储在 workspace/skills/ 目录下，每个技能一个子目录 <skill-name>/SKILL.md。</p>
 * <p>所有 Agent 共享同一技能池，无需 per-agent 同步。</p>
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final WorkspaceConfig workspaceConfig;
    private final SkillConfig skillConfig;
    private final SkillIndexRepository skillIndexRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    // GitHub 仓库根链接: https://github.com/{owner}/{repo}[.git]
    private static final Pattern GITHUB_REPO_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    // GitHub tree 链接: https://github.com/{owner}/{repo}/tree/{branch}/skills/{path}
    private static final Pattern GITHUB_TREE_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)/tree/[^/]+/(skills/.+)$");

    /**
     * 获取全局技能目录路径。
     */
    public Path getSkillsDir() {
        return Path.of(workspaceConfig.getRoot(), "skills");
    }

    /**
     * 列出所有技能（从索引表读取）。
     */
    public ApiResponse<List<SkillIndex>> listSkills() {
        return ApiResponse.success(skillIndexRepository.findAll());
    }

    /**
     * 从 GitHub URL 导入技能。
     * 支持两种格式：
     * <ul>
     *   <li>仓库根链接 {@code https://github.com/{owner}/{repo}} — 全量扫描仓库下所有 SKILL.md</li>
     *   <li>子目录链接 {@code https://github.com/{owner}/{repo}/tree/{branch}/skills/{name}} — 只导入该技能</li>
     * </ul>
     */
    public ApiResponse<Void> importFromGithubUrl(String url) {
        String trimmed = url.trim();

        // 1. 解析 URL
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

        // 2. 检测 git 安装
        ApiResponse<Void> gitCheck = checkGitInstalled();
        if (gitCheck != null) return gitCheck;

        // 3. 读取已有 lock
        Map<String, JsonNode> existingSkills = readLockSkills();

        // 4. 克隆仓库
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

        // 5. 扫描 SKILL.md 文件
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

        // 6. 逐个验证 + 导入
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
                // 读取 frontmatter
                String content = Files.readString(skillMd);
                String name = extractFrontmatterField(content, "name");
                String description = extractFrontmatterField(content, "description");

                // 验证
                String validationError = validateSkillFormat(name, description);
                if (validationError != null) {
                    errors.add(skillMd.getFileName() + ": " + validationError);
                    continue;
                }

                // 检查是否已存在
                if (existingSkills.containsKey(name)) {
                    skipped.add(name);
                    continue;
                }

                // 计算 skillPath（相对于仓库根）
                String relativeSkillPath = tempDir.relativize(skillMd).toString().replace("\\", "/");

                // 拷贝技能目录
                Path sourceDir = skillMd.getParent();
                Path targetDir = skillsDir.resolve(name);
                if (Files.exists(targetDir)) {
                    deleteRecursively(targetDir);
                }
                copyDirectory(sourceDir, targetDir);

                ImportedSkill is = new ImportedSkill();
                is.name = name;
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

        // 7. 写入 lock
        if (!imported.isEmpty()) {
            writeLockSkills(existingSkills, imported);
        }

        // 8. 清理
        deleteRecursively(tempDir);

        // 9. 刷新索引
        if (!imported.isEmpty()) {
            refreshIndex();
        }

        // 10. 构建结果消息
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
     * ZIP 解压后的一级目录即技能名称，每个一级目录内必须包含 SKILL.md（含 name + description frontmatter）。
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

                // Validate: first-level dirs must contain SKILL.md with valid frontmatter
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

                // Move valid skill directories to workspace/skills/
                try (var dirs = Files.list(stagingDir)) {
                    dirs.filter(Files::isDirectory).forEach(dir -> {
                        Path target = skillsDir.resolve(dir.getFileName());
                        try {
                            if (Files.exists(target)) {
                                deleteRecursively(target);
                            }
                            Files.move(dir, target);
                        } catch (IOException e) {
                            log.warn("Failed to move skill {}: {}", dir.getFileName(), e.getMessage());
                        }
                    });
                }

                refreshIndex();
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
     * 删除技能目录和索引记录。
     */
    @Transactional
    public ApiResponse<Void> deleteSkill(String name) {
        Path skillDir = getSkillsDir().resolve(name);
        if (!Files.isDirectory(skillDir)) {
            return ApiResponse.error(404, "技能不存在: " + name);
        }
        deleteRecursively(skillDir);
        skillIndexRepository.deleteById(name);
        // Also remove from lock
        removeFromLock(name);
        log.info("Skill deleted: {}", name);
        return ApiResponse.success("技能已删除", null);
    }

    /**
     * 刷新技能索引：扫描文件系统，更新 skill_index 表。
     */
    @Transactional
    public void refreshIndex() {
        Path skillsDir = getSkillsDir();
        if (!Files.isDirectory(skillsDir)) {
            skillIndexRepository.deleteAll();
            return;
        }

        Set<String> fsSkills = new HashSet<>();
        try (var dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory)
                    .forEach(dir -> {
                        String name = dir.getFileName().toString();
                        fsSkills.add(name);
                        SkillIndex index = parseSkillFrontmatter(dir);
                        if (index != null) {
                            skillIndexRepository.save(index);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan skills directory: {}", e.getMessage());
        }

        List<SkillIndex> existing = skillIndexRepository.findAll();
        for (SkillIndex idx : existing) {
            if (!fsSkills.contains(idx.getName())) {
                skillIndexRepository.deleteById(idx.getName());
            }
        }
    }

    // ===== Private helpers =====

    /**
     * 检测 git 是否已安装。
     */
    private ApiResponse<Void> checkGitInstalled() {
        try {
            int exitCode = runGitCommand(null, "--version");
            if (exitCode != 0) {
                return ApiResponse.error(400,
                        "未检测到 Git，请先安装 Git: https://git-scm.com/downloads");
            }
            return null;
        } catch (Exception e) {
            return ApiResponse.error(400,
                    "未检测到 Git，请先安装 Git: https://git-scm.com/downloads");
        }
    }

    /**
     * 运行 git 命令并返回退出码。
     */
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

        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
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
     * 在指定目录执行 git 命令并捕获 stdout 第一行。
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

    /**
     * 从文件系统临时目录查找所有 SKILL.md 文件（不区分大小写）。
     */
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
     * 在指定目录下查找 SKILL.md（不区分大小写），只查一级。
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
     * 验证技能格式：name 和 description 必填。
     */
    private String validateSkillFormat(String name, String description) {
        if (name == null || name.isBlank()) {
            return "缺少 name frontmatter";
        }
        if (description == null || description.isBlank()) {
            return "缺少 description frontmatter";
        }
        return null;
    }

    /**
     * 解析 SKILL.md 的 YAML frontmatter，构造 SkillIndex。
     */
    private SkillIndex parseSkillFrontmatter(Path skillDir) {
        String name = skillDir.getFileName().toString();
        Path skillMd = findSkillMdInDir(skillDir);
        if (skillMd == null || !Files.isReadable(skillMd)) {
            return SkillIndex.builder()
                    .name(name)
                    .description("")
                    .category("other")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        try {
            String content = Files.readString(skillMd);
            String description = extractFrontmatterField(content, "description");
            String category = extractFrontmatterField(content, "category");
            return SkillIndex.builder()
                    .name(name)
                    .description(description != null ? description : "")
                    .category(category != null ? category : "other")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } catch (IOException e) {
            log.warn("Failed to read SKILL.md in {}: {}", name, e.getMessage());
            return SkillIndex.builder()
                    .name(name)
                    .description("")
                    .category("other")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 从 YAML frontmatter 提取指定字段。
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

    // ===== Lock file management =====

    private Path getLockFile() {
        return Path.of(workspaceConfig.getRoot(), "skills-lock.json");
    }

    /**
     * 读取 skills-lock.json，返回 skillName → entry 的映射。
     * 兼容 version 1 和 version 2 格式。
     */
    private Map<String, JsonNode> readLockSkills() {
        Path lockFile = getLockFile();
        if (!Files.isReadable(lockFile)) {
            return new LinkedHashMap<>();
        }
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
     * 将新导入的技能写入 lock 文件（version 2 格式）。
     */
    private void writeLockSkills(Map<String, JsonNode> existing, List<ImportedSkill> imported) {
        Path lockFile = getLockFile();
        try {
            Files.createDirectories(lockFile.getParent());

            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", 2);
            ObjectNode skillsNode = root.putObject("skills");

            // Preserve existing entries
            for (Map.Entry<String, JsonNode> entry : existing.entrySet()) {
                skillsNode.set(entry.getKey(), entry.getValue());
            }

            // Add new entries
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
     * 从 lock 文件中移除指定技能。
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

    // ===== File I/O helpers =====

    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(
                java.nio.file.Files.newInputStream(zipFile))) {
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
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
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

    // ===== Internal DTO =====

    private static class ImportedSkill {
        String name;
        String source;
        String sourceUrl;
        String skillPath;
        String commitHash;
    }
}
