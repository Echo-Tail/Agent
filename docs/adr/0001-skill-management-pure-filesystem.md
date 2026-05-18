# 技能管理采用纯文件系统而非数据库存储

技能本质是 Markdown 文件（`SKILL.md` + 可选附属资源），不适合将文件内容存入数据库表字段。同时 AgentScope Java SDK 提供了 `FileSystemSkillRepository` 作为第一方支持，无额外依赖。因此决定：以 `workspace/skills/` 目录为技能 SSOT，不建 `skill_pool` 表，仅保留轻量 `skill_index` 表作为文件系统的只读缓存供前端列表展示。

##Considered Options

- **DB 中心（原方案）**：`skill_pool` 表存完整内容 + 元数据，`agent_skills` 关联表记录引用，`SkillSyncService` 同步到 workspace。**拒绝原因**：技能本质是文件，存入 DB 徒增 schema 和维护成本，且需要 per-agent 同步逻辑，复杂度与收益不匹配。
- **纯文件系统（选定）**：所有 Agent 共享全局 `workspace/skills/`，HarnessAgent 统一指向该目录。无 per-agent 分配、无启用/禁用、无关联表。**选定原因**：与技能的文件本质一致，消除同步逻辑，AgentScope SDK 原生支持。
- **DB 存元数据 + 文件存内容**：混合方案，DB 记录管理元数据（enabled、category 等），文件存实际内容。**拒绝原因**：引入的复杂度超过价值，元数据可放在 YAML frontmatter 中。

## Consequences

- 所有 Agent 自动获得全部技能，无法 per-agent 选择/过滤（当前不需要此功能）
- 技能管理 API 从 CRUD 简化为：列表、导入（URL/ZIP/npx）、删除
- 管理员无法"禁用"技能而不删除，只能物理删除
- 前端 `AgentCreate.vue` 不再展示技能选择 UI
- 删除约 5 个后端文件（`SkillPool.java`、`SkillPoolRepository.java`、`SkillPoolService.java`、`SkillPoolController.java`、`SkillSyncService.java`），精简整体架构
