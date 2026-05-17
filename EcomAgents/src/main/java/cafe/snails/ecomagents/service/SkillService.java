package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.SkillIndex;
import cafe.snails.ecomagents.repository.SkillIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final SkillIndexRepository skillIndexRepository;
    private final ObjectMapper objectMapper;

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
     * skills.sh URL 正则：https://www.skills.sh/{org}/skills/{skill-name}
     */
    private static final Pattern SKILLS_SH_PATTERN =
            Pattern.compile("^https?://(?:www\\.)?skills\\.sh/([^/]+)/skills/([^/]+)");

    /**
     * 从 skills.sh URL 导入技能。
     * 解析 URL 提取 org/skill-name，拼接 npx 命令后执行。
     */
    public ApiResponse<Void> importFromSkillsUrl(String url) {
        Matcher m = SKILLS_SH_PATTERN.matcher(url.trim());
        if (!m.matches()) {
            return ApiResponse.error(400, "不支持的 URL 格式，目前仅支持 skills.sh 链接");
        }
        String org = m.group(1);
        String skillName = m.group(2);
        String npxUrl = "https://github.com/" + org + "/skills";
        String cmd = String.format("npx skills add %s --skill %s", npxUrl, skillName);

        Path workspaceRoot = Path.of(workspaceConfig.getRoot());
        try {
            Files.createDirectories(workspaceRoot);
            List<String> command = buildNpxCommand(npxUrl, skillName);
            log.info("Running command: {} in dir: {}", String.join(" ", command), workspaceRoot);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workspaceRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Thread readerThread = new Thread(() -> {
                try (var r = process.inputReader()) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        synchronized (output) {
                            output.append(line).append("\n");
                        }
                        log.info("[npx] {}", line);
                    }
                } catch (IOException ignored) {}
            });
            readerThread.start();

            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
            readerThread.join(5000);
            if (!finished) {
                process.destroyForcibly();
                log.warn("npx skills add timed out. Output:\n{}", output);
                return ApiResponse.error(500, "技能下载超时（5分钟），请稍后重试");
            }
            int exitCode = process.exitValue();
            log.info("npx exit code: {}. Output:\n{}", exitCode, output);
            if (exitCode != 0) {
                return ApiResponse.error(500, "npx skills add 失败，退出码: " + exitCode + "\n" + output);
            }

            // npx skills add outputs to .agents/skills/{skillName} relative to workspace root.
            // Move it to workspace/skills/{skillName}.
            Path agentSkillsDir = workspaceRoot.resolve(".agents/skills");
            Path skillSource = agentSkillsDir.resolve(skillName);
            if (Files.isDirectory(skillSource)) {
                Files.createDirectories(getSkillsDir());
                Path target = getSkillsDir().resolve(skillName);
                if (Files.exists(target)) {
                    deleteRecursively(target);
                }
                Files.move(skillSource, target);
                log.info("Moved skill from {} to {}", skillSource, target);
                // Clean up .agents directory tree
                Path agentsDir = workspaceRoot.resolve(".agents");
                if (Files.isDirectory(agentsDir)) {
                    deleteRecursively(agentsDir);
                }
            } else {
                log.warn("Expected skill directory not found at {}", skillSource);
                log.info("Contents of {}:", workspaceRoot);
                try (var files = Files.list(workspaceRoot)) {
                    files.forEach(f -> log.info("  - {}", f.getFileName()));
                }
            }

            refreshIndex();
            log.info("Skill imported from skills.sh: org={}, skill={}", org, skillName);
            return ApiResponse.success("技能导入成功", null);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to import skill from URL {}: {}", url, e.getMessage());
            return ApiResponse.error(500, "技能导入失败: " + e.getMessage());
        }
    }

    /**
     * 从上传的 ZIP 文件导入技能。
     * ZIP 解压后的一级目录即技能名称，每个一级目录内必须包含 SKILL.md。
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

            // Extract to a temp staging directory first for validation
            Path stagingDir = Files.createTempDirectory("skill-staging-");
            try {
                extractZip(tempFile, stagingDir);

                // Validate: first-level dirs must contain SKILL.md
                List<Path> invalidDirs = new ArrayList<>();
                try (var dirs = Files.list(stagingDir)) {
                    dirs.filter(Files::isDirectory).forEach(dir -> {
                        if (!Files.exists(dir.resolve("SKILL.md"))) {
                            invalidDirs.add(dir);
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
        try {
            deleteRecursively(skillDir);
            skillIndexRepository.deleteById(name);
            log.info("Skill deleted: {}", name);
            return ApiResponse.success("技能已删除", null);
        } catch (IOException e) {
            log.error("Failed to delete skill {}: {}", name, e.getMessage());
            return ApiResponse.error(500, "技能删除失败: " + e.getMessage());
        }
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

        // Collect current skill names from filesystem
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

        // Remove index entries for deleted skills
        List<SkillIndex> existing = skillIndexRepository.findAll();
        for (SkillIndex idx : existing) {
            if (!fsSkills.contains(idx.getName())) {
                skillIndexRepository.deleteById(idx.getName());
            }
        }
    }

    // ===== Private helpers =====

    /**
     * 解析 SKILL.md 的 YAML frontmatter，构造 SkillIndex。
     */
    private SkillIndex parseSkillFrontmatter(Path skillDir) {
        String name = skillDir.getFileName().toString();
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.isReadable(skillMd)) {
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

    /**
     * 构建跨平台的 npx 命令列表。
     * Windows 上 npx 是 .cmd 文件，需要 cmd.exe /c 来执行。
     */
    private List<String> buildNpxCommand(String repoUrl, String skillName) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            return List.of("cmd.exe", "/c", "npx", "-y", "skills", "add", repoUrl, "--skill", skillName);
        }
        return List.of("npx", "-y", "skills", "add", repoUrl, "--skill", skillName);
    }

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
