# Skill Management: DB → Pure Filesystem

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Replace DB-centric skill management (`skill_pool` table, `SkillSyncService` per-agent sync) with pure filesystem approach: `workspace/skills/` as global shared skill pool, all agents share all skills.

**Architecture:** Remove `SkillPool` entity/repository/service/controller + `SkillSyncService`. Create `SkillIndex` (lightweight FS cache entity), `SkillService` (file operations via AgentScope `FileSystemSkillRepository`), simplified `SkillController` with only list/import/delete. All agents share `workspace/skills/` via symlink in their workspace. Frontend `SkillManage.vue` simplified to list/import/delete only.

**Tech Stack:** Spring Boot 3 / Java 17, H2/PostgreSQL, Vue 3 + Naive UI + TS, AgentScope `FileSystemSkillRepository`

---

### Task 1: Delete old DB-centric skill files + Agent cleanup

**Files:**
- Delete: `EcomAgents/src/main/java/cafe/snails/ecomagents/model/SkillPool.java`
- Delete: `EcomAgents/src/main/java/cafe/snails/ecomagents/repository/SkillPoolRepository.java`
- Delete: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/SkillPoolService.java`
- Delete: `EcomAgents/src/main/java/cafe/snails/ecomagents/controller/SkillPoolController.java`
- Delete: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/SkillSyncService.java`
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/model/Agent.java` (remove `skillIds` field)
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/repository/AgentRepository.java` (remove `findBySkillId()`)
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/AgentService.java` (remove `SkillSyncService` dep + skill sync code)
- Modify: `EcomAgents/src/test/java/cafe/snails/ecomagents/service/AgentServiceTest.java` (remove `SkillSyncService` mock)

- [ ] **Step 1: Delete 5 old skill files**

```bash
rm EcomAgents/src/main/java/cafe/snails/ecomagents/model/SkillPool.java
rm EcomAgents/src/main/java/cafe/snails/ecomagents/repository/SkillPoolRepository.java
rm EcomAgents/src/main/java/cafe/snails/ecomagents/service/SkillPoolService.java
rm EcomAgents/src/main/java/cafe/snails/ecomagents/controller/SkillPoolController.java
rm EcomAgents/src/main/java/cafe/snails/ecomagents/service/SkillSyncService.java
```

- [ ] **Step 2: Remove `skillIds` field from Agent.java**

Remove lines 57-61 (the `skillIds` field + annotation):
```java
    /** 引用的技能 ID 列表（关联 skill_pool 表） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_skills", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "skill_id")
    private List<Long> skillIds;
```

Also add `@Builder.Default` to `tags` field (currently missing it), to match the pattern:
```java
    @Builder.Default
    private List<String> tags;
```

- [ ] **Step 3: Remove `findBySkillId()` from AgentRepository.java**

Delete lines 25-27:
```java
    /** 查找关联了指定技能的所有 Agent */
    @Query("SELECT a FROM Agent a JOIN a.skillIds sId WHERE sId = :skillId")
    List<Agent> findBySkillId(@Param("skillId") Long skillId);
```

- [ ] **Step 4: Remove SkillSyncService from AgentService.java**

Remove `SkillSyncService` field injection (line 36):
```java
    private final SkillSyncService skillSyncService;
```

Update constructor — from 5 params to 4:
```java
    // Before:
    private final AgentRepository agentRepository;
    private final AiModelRepository aiModelRepository;
    private final WorkspaceInitService workspaceInitService;
    private final SkillSyncService skillSyncService;

    // After:
    private final AgentRepository agentRepository;
    private final AiModelRepository aiModelRepository;
    private final WorkspaceInitService workspaceInitService;
```

Remove `skillsChanged` tracking from `updateAgent()` (lines 116, 128-131, 149-152):
```java
    // In updateAgent(), remove:
    boolean skillsChanged = false;
    // and:
    if (update.getSkillIds() != null) {
        existing.setSkillIds(update.getSkillIds());
        skillsChanged = true;
    }
    // and:
    if (skillsChanged) {
        skillSyncService.syncAgentSkills(id, saved.getSkillIds());
    }
```

Remove skill sync from `createAgent()` (lines 100-103):
```java
    // Remove:
    if (saved.getSkillIds() != null && !saved.getSkillIds().isEmpty()) {
        skillSyncService.syncAgentSkills(saved.getId(), saved.getSkillIds());
    }
```

- [ ] **Step 5: Update AgentServiceTest.java**

Remove `SkillSyncService` mock (line 36-38):
```java
    @Mock
    private SkillSyncService skillSyncService;
```

Update constructor call (line 45):
```java
    // Before:
    service = new AgentService(repository, aiModelRepository, workspaceInitService, skillSyncService);
    // After:
    service = new AgentService(repository, aiModelRepository, workspaceInitService);
```

Remove unused import:
```java
    // Remove: import cafe.snails.ecomagents.service.SkillSyncService;
```

- [ ] **Step 6: Run backend tests to verify**

```bash
cd EcomAgents && ./gradlew test --tests "*AgentServiceTest*"
```

Expected: All tests pass.

---

### Task 2: Create SkillIndex entity and repository

**Files:**
- Create: `EcomAgents/src/main/java/cafe/snails/ecomagents/model/SkillIndex.java`
- Create: `EcomAgents/src/main/java/cafe/snails/ecomagents/repository/SkillIndexRepository.java`

- [ ] **Step 1: Create SkillIndex.java**

```java
package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 技能索引表 — 文件系统 workspace/skills/ 的只读缓存。
 * <p>SSOT 是文件系统，此表仅在技能变更时刷新，供前端列表展示。</p>
 */
@Entity
@Table(name = "skill_index")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillIndex {

    @Id
    @Column(length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create SkillIndexRepository.java**

```java
package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.SkillIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 技能索引数据访问层。
 */
@Repository
public interface SkillIndexRepository extends JpaRepository<SkillIndex, String> {
}
```

- [ ] **Step 3: Run compile check**

```bash
cd EcomAgents && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

---

### Task 3: Create SkillService (file-system based)

**Files:**
- Create: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/SkillService.java`
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/WorkspaceInitService.java` (update skills dir handling)

- [ ] **Step 1: Create SkillService.java**

```java
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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
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
     * 从 skills.sh 导入技能（通过 npx skills add）。
     */
    public ApiResponse<Void> importFromNpx(String repo) {
        try {
            ProcessBuilder pb = new ProcessBuilder("npx", "skills", "add", repo);
            pb.directory(getSkillsDir().toFile());
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ApiResponse.error(500, "npx skills add 失败，退出码: " + exitCode);
            }
            refreshIndex();
            log.info("Skill imported via npx: {}", repo);
            return ApiResponse.success("技能导入成功", null);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to import skill via npx: {}", e.getMessage());
            return ApiResponse.error(500, "技能导入失败: " + e.getMessage());
        }
    }

    /**
     * 从 ZIP URL 导入技能。
     */
    public ApiResponse<Void> importFromUrl(String url) {
        try {
            // Download to temp file
            java.net.URL downloadUrl = new java.net.URL(url);
            Path tempFile = Files.createTempFile("skill-import-", ".zip");
            try (var in = downloadUrl.openStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Extract to skills directory
            Path skillsDir = getSkillsDir();
            Files.createDirectories(skillsDir);
            extractZip(tempFile, skillsDir);

            Files.deleteIfExists(tempFile);
            refreshIndex();
            log.info("Skill imported from URL: {}", url);
            return ApiResponse.success("技能导入成功", null);
        } catch (IOException e) {
            log.error("Failed to import skill from URL: {}", e.getMessage());
            return ApiResponse.error(500, "技能导入失败: " + e.getMessage());
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
     * 如果 SKILL.md 不存在或解析失败，返回包含基本信息的索引。
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
     * 从 YAML frontmatter（--- 包裹的部分）提取指定字段。
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
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.isEmpty() ? null : value;
            }
        }
        return null;
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
```

- [ ] **Step 2: Update WorkspaceInitService — use symlink for skills**

In `initWorkspace()`, change the skills directory creation from empty dir to symlink to global skills:
```java
    // Before (line 40):
    Files.createDirectories(agentDir.resolve("skills"));

    // After:
    Path globalSkills = Path.of(workspaceConfig.getRoot(), "skills");
    Files.createDirectories(globalSkills);
    Path agentSkills = agentDir.resolve("skills");
    // Remove if exists from previous init
    if (Files.exists(agentSkills)) {
        try { deleteRecursively(agentSkills); } catch (IOException ignored) {}
    }
    try {
        Files.createSymbolicLink(agentSkills, globalSkills);
    } catch (IOException e) {
        // Symlink not supported (e.g. Windows without dev mode) — fall back to directory
        log.warn("Cannot create symlink for skills, creating directory instead: {}", e.getMessage());
        Files.createDirectories(agentSkills);
    }
```

Need to add `private static final Logger log` field if not present:
```java
    private static final Logger log = LoggerFactory.getLogger(WorkspaceInitService.class);
```

- [ ] **Step 3: Run compile check**

```bash
cd EcomAgents && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

---

### Task 4: Create SkillController (simplified REST API)

**Files:**
- Create: `EcomAgents/src/main/java/cafe/snails/ecomagents/controller/SkillController.java`

- [ ] **Step 1: Create SkillController.java**

```java
package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.SkillIndex;
import cafe.snails.ecomagents.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能控制器 — 基于文件系统的技能管理。
 * 仅提供列表、导入、删除，无 create/edit/toggle。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    /** 列出所有技能 */
    @GetMapping("/skills")
    public ApiResponse<List<SkillIndex>> listSkills() {
        return skillService.listSkills();
    }

    /**
     * 导入技能。
     * body.source: "npx" | "url"
     * body.value: "<owner/repo>" (npx) 或 "https://..." (url)
     */
    @PostMapping("/skills/import")
    public ApiResponse<Void> importSkill(@RequestBody Map<String, String> body) {
        String source = body.get("source");
        String value = body.get("value");
        if (source == null || value == null || value.isBlank()) {
            return ApiResponse.error(400, "请提供 source 和 value");
        }
        return switch (source) {
            case "npx" -> skillService.importFromNpx(value);
            case "url" -> skillService.importFromUrl(value);
            default -> ApiResponse.error(400, "不支持的导入来源: " + source);
        };
    }

    /** 删除技能 */
    @DeleteMapping("/skills/{name}")
    public ApiResponse<Void> deleteSkill(@PathVariable("name") String name) {
        return skillService.deleteSkill(name);
    }
}
```

- [ ] **Step 2: Run compile check**

```bash
cd EcomAgents && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

---

### Task 5: Update frontend types and API layer

**Files:**
- Modify: `EcomAgentsFront/src/types/agent.ts` (remove `skillIds`)
- Modify: `EcomAgentsFront/src/types/api.ts` (update `SkillDefinition` → simplified)
- Modify: `EcomAgentsFront/src/api/skill.ts` (simplify to match new API)

- [ ] **Step 1: Remove skillIds from agent.ts**

Remove `skillIds: number[]` from `Agent`, `AgentCreateRequest`, `AgentUpdateRequest`:
```typescript
// Remove from Agent interface:
  skillIds: number[]

// Remove from AgentCreateRequest interface:
  skillIds?: number[]

// Remove from AgentUpdateRequest interface:
  skillIds?: number[]
```

- [ ] **Step 2: Update SkillDefinition in api.ts**

Replace the old `SkillDefinition` interface:
```typescript
export interface SkillDefinition {
  name: string
  description: string
  category: string
  createdAt: string
  updatedAt: string
}
```

- [ ] **Step 3: Simplify api/skill.ts**

Replace entire file:
```typescript
import http from './request'
import type { ApiResponse } from '../types/api'
import type { SkillDefinition } from '../types/api'

export function listSkillsApi() {
  return http.get<ApiResponse<SkillDefinition[]>>('/skills')
}

export function importSkillApi(source: string, value: string) {
  return http.post<ApiResponse<void>>('/skills/import', { source, value })
}

export function deleteSkillApi(name: string) {
  return http.delete<ApiResponse<void>>(`/skills/${name}`)
}
```

- [ ] **Step 4: TypeScript check**

```bash
cd EcomAgentsFront && npx vue-tsc --noEmit
```

Expected: No type errors.

---

### Task 6: Simplify SkillManage.vue

**Files:**
- Overwrite: `EcomAgentsFront/src/views/admin/SkillManage.vue`

- [ ] **Step 1: Rewrite SkillManage.vue**

Replace file content with simplified version — only listing table, import button (URL/npx input), and delete button:

```vue
<script setup lang="ts">
import { h, ref, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { listSkillsApi, importSkillApi, deleteSkillApi } from '../../api/skill'
import type { SkillDefinition } from '../../types/api'

const message = useMessage()
const dialog = useDialog()

const skills = ref<SkillDefinition[]>([])
const loading = ref(false)

// Import modal
const showImportModal = ref(false)
const importSource = ref<'npx' | 'url'>('npx')
const importValue = ref('')
const importing = ref(false)

const categoryLabels: Record<string, string> = {
  browser: '浏览器',
  development: '开发工具',
  data: '数据分析',
  automation: '自动化',
  communication: '通讯',
  other: '其他',
}

const categoryColors: Record<string, string> = {
  browser: '#f0a020',
  development: '#2080f0',
  data: '#18a058',
  automation: '#d03050',
  communication: '#8050c0',
  other: '#888',
}

async function fetchSkills() {
  loading.value = true
  try {
    const res = await listSkillsApi()
    if (res.data.code === 200) {
      skills.value = res.data.data ?? []
    }
  } catch {
    message.error('加载技能列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchSkills)

function openImport() {
  importSource.value = 'npx'
  importValue.value = ''
  showImportModal.value = true
}

async function handleImport() {
  if (!importValue.value.trim()) {
    message.warning('请输入导入值')
    return
  }
  importing.value = true
  try {
    const res = await importSkillApi(importSource.value, importValue.value.trim())
    if (res.data.code === 200) {
      message.success('技能导入成功')
      showImportModal.value = false
      await fetchSkills()
    } else {
      message.error(res.data.message || '导入失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    importing.value = false
  }
}

function handleDelete(skill: SkillDefinition) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除技能「${skill.name}」吗？该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await deleteSkillApi(skill.name)
        if (res.data.code === 200) {
          message.success('删除成功')
          await fetchSkills()
        } else {
          message.error(res.data.message || '删除失败')
        }
      } catch {
        message.error('网络异常')
      }
    },
  })
}

const columns: DataTableColumn<SkillDefinition>[] = [
  {
    title: '名称',
    key: 'name',
    width: 140,
    ellipsis: { tooltip: true },
  },
  {
    title: '描述',
    key: 'description',
    ellipsis: { tooltip: true },
    minWidth: 160,
  },
  {
    title: '类别',
    key: 'category',
    width: 100,
    render: (row) =>
      row.category
        ? h('span', {
            style: `display:inline-block;padding:1px 10px;border-radius:10px;font-size:12px;background:${categoryColors[row.category] || '#888'};color:#fff;line-height:20px;`,
          }, categoryLabels[row.category] || row.category)
        : h('span', { style: 'color:#999;' }, '未分类'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) =>
      h('button', {
        class: 'n-button n-button--tiny',
        style: 'padding:2px 8px;border:none;border-radius:4px;cursor:pointer;background:#d03050;color:#fff;',
        onClick: () => handleDelete(row),
      }, '删除'),
  },
]
</script>

<template>
  <n-space vertical size="large">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <n-h3 style="margin: 0;">技能管理</n-h3>
      <n-button type="primary" @click="openImport">导入技能</n-button>
    </div>

    <n-data-table
      :columns="columns"
      :data="skills"
      :loading="loading"
      :bordered="true"
      :single-line="false"
      :row-key="(row: SkillDefinition) => row.name"
      striped
    />

    <!-- No skills placeholder -->
    <n-empty v-if="!loading && skills.length === 0" description="暂无技能，点击上方按钮导入" />

    <!-- Import Modal -->
    <n-modal
      v-model:show="showImportModal"
      title="导入技能"
      preset="card"
      style="width: 500px; max-width: 90vw;"
      :mask-closable="false"
      :segmented="true"
    >
      <n-form>
        <n-form-item label="来源">
          <n-radio-group v-model:value="importSource">
            <n-radio value="npx">skills.sh (npx)</n-radio>
            <n-radio value="url">ZIP 下载链接</n-radio>
          </n-radio-group>
        </n-form-item>
        <n-form-item
          :label="importSource === 'npx' ? '仓库地址 (owner/repo)' : 'ZIP 下载 URL'"
          required
        >
          <n-input
            v-model:value="importValue"
            :placeholder="importSource === 'npx' ? '如: vercel-labs/skills' : 'https://...'"
            :disabled="importing"
          />
        </n-form-item>

        <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px;">
          <n-button @click="showImportModal = false" :disabled="importing">取消</n-button>
          <n-button type="primary" :loading="importing" @click="handleImport">
            导入
          </n-button>
        </div>
      </n-form>
    </n-modal>
  </n-space>
</template>
```

- [ ] **Step 2: TypeScript check**

```bash
cd EcomAgentsFront && npx vue-tsc --noEmit
```

Expected: No type errors.

---

### Task 7: Clean up AgentCreate.vue (remove skill selection UI)

**Files:**
- Modify: `EcomAgentsFront/src/views/agent/AgentCreate.vue`

- [ ] **Step 1: Remove skill-related imports and code**

Remove import of `listSkillsApi` and `SkillDefinition`:
```typescript
// Remove from imports:
import { listSkillsApi } from '../../api/skill'
import type { SkillDefinition } from '../../types/api'
```

Remove reactive state for skills:
```typescript
// Remove:
const skillIds = ref<number[]>([])
const availableSkills = ref<SkillDefinition[]>([])
const enabledSkills = computed(() => availableSkills.value.filter((s) => s.enabled))
```

Remove `fetchSkills()` function and `fetchSkills()` call from `onMounted`.

Remove `skillRes` from `fetchAgent()`:
```typescript
// Before:
const [res, modelRes, skillRes] = await Promise.all([
  getAgentApi(agentId.value),
  listModelsApi(),
  listSkillsApi(),
])
if (skillRes.data.code === 200) {
  availableSkills.value = skillRes.data.data ?? []
}

// After:
const [res, modelRes] = await Promise.all([
  getAgentApi(agentId.value),
  listModelsApi(),
])
```

Remove `skillIds.value = a.skillIds || []` from the edit form population.

Remove `skillIds: skillIds.value.length ? skillIds.value : undefined` from payload.

Remove the Skills checkbox template section:
```vue
<!-- Remove entire section: -->
<!-- Skills -->
<n-form-item v-if="enabledSkills.length > 0" label="技能">
  <n-checkbox-group v-model:value="skillIds">
    <n-space>
      <n-checkbox
        v-for="skill in enabledSkills"
        :key="skill.id"
        :value="skill.id"
        :label="skill.name"
      />
    </n-space>
  </n-checkbox-group>
</n-form-item>
```

- [ ] **Step 2: TypeScript check**

```bash
cd EcomAgentsFront && npx vue-tsc --noEmit
```

Expected: No type errors.

---

### Task 8: Update frontend test files

**Files:**
- Modify: `EcomAgentsFront/src/test/stores/agent.test.ts` (remove `skillIds` from mock data)
- Modify: `EcomAgentsFront/src/test/AgentList.test.ts` (remove `skillIds` from mock data)
- Modify: `EcomAgentsFront/src/test/DashboardView.test.ts` (remove `skillIds` from mock data)
- Modify: `EcomAgentsFront/src/test/components/AgentCard.test.ts` (remove `skillIds` from mock data)

- [ ] **Step 1: Remove skillIds from all test mock data**

In each test file, find and remove `skillIds: []` (or `skillIds: [],`) from mock Agent objects.

- [ ] **Step 2: Run frontend tests**

```bash
cd EcomAgentsFront && npm test
```

Expected: All tests pass.

---

### Task 9: Backend integration test

**Files:**
- Create: `EcomAgents/src/test/java/cafe/snails/ecomagents/service/SkillServiceTest.java`

- [ ] **Step 1: Create SkillServiceTest.java**

```java
package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.model.SkillIndex;
import cafe.snails.ecomagents.repository.SkillIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SkillService} 单元测试。
 */
class SkillServiceTest {

    @TempDir
    Path tempDir;

    private SkillService skillService;
    private SkillIndexRepository skillIndexRepository;
    private WorkspaceConfig workspaceConfig;

    @BeforeEach
    void setUp() {
        workspaceConfig = new WorkspaceConfig();
        workspaceConfig.setRoot(tempDir.toString());
        skillIndexRepository = new SkillIndexRepository() {
            private final java.util.Map<String, SkillIndex> store = new java.util.HashMap<>();

            @Override
            public List<SkillIndex> findAll() {
                return List.copyOf(store.values());
            }

            @Override
            public <S extends SkillIndex> S save(S entity) {
                store.put(entity.getName(), entity);
                return entity;
            }

            @Override
            public void deleteById(String id) {
                store.remove(id);
            }

            @Override
            public void deleteAll() {
                store.clear();
            }

            @Override
            public void deleteAll(Iterable<? extends SkillIndex> entities) {
                for (SkillIndex e : entities) store.remove(e.getName());
            }

            @Override
            public <S extends SkillIndex> List<S> saveAll(Iterable<S> entities) {
                for (S e : entities) save(e);
                return (List<S>) List.copyOf(store.values());
            }
        };
        skillService = new SkillService(workspaceConfig, skillIndexRepository, new ObjectMapper());
    }

    @Test
    void getSkillsDir_shouldReturnWorkspaceSkills() {
        assertEquals(tempDir.resolve("skills"), skillService.getSkillsDir());
    }

    @Test
    void listSkills_shouldReturnEmpty_whenNoSkills() {
        var result = skillService.listSkills();
        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void deleteSkill_shouldReturn404_whenNotExists() {
        var result = skillService.deleteSkill("nonexistent");
        assertEquals(404, result.getCode());
    }

    @Test
    void deleteSkill_shouldDeleteSkillDirAndIndex() throws Exception {
        // Create a skill directory and index
        Path skillDir = skillService.getSkillsDir().resolve("test-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: test-skill\ndescription: Test\n---\n\nContent");

        SkillIndex idx = SkillIndex.builder().name("test-skill").description("Test").category("other").build();
        skillIndexRepository.save(idx);

        var result = skillService.deleteSkill("test-skill");
        assertEquals(200, result.getCode());
        assertFalse(Files.exists(skillDir));
        assertTrue(skillIndexRepository.findAll().isEmpty());
    }

    @Test
    void refreshIndex_shouldSyncFsToDb() throws Exception {
        Files.createDirectories(skillService.getSkillsDir().resolve("skill-a"));
        Files.writeString(
                skillService.getSkillsDir().resolve("skill-a").resolve("SKILL.md"),
                "---\nname: skill-a\ndescription: Skill A\ndescription: First skill\ncategory: development\n---\n\nContent");

        Files.createDirectories(skillService.getSkillsDir().resolve("skill-b"));
        Files.writeString(
                skillService.getSkillsDir().resolve("skill-b").resolve("SKILL.md"),
                "---\nname: skill-b\ndescription: Skill B\ncategory: data\n---\n\nContent");

        skillService.refreshIndex();

        var all = skillIndexRepository.findAll();
        assertEquals(2, all.size());
    }
}
```

- [ ] **Step 2: Run backend tests**

```bash
cd EcomAgents && ./gradlew test
```

Expected: All tests pass.
