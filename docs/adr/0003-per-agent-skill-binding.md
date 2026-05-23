# Per-Agent 技能绑定（文件系统 SSOT + 数据库引用管理）

Supersedes: [ADR-0001](0001-skill-management-pure-filesystem.md)

Status: accepted

## Context

ADR-0001 决策所有 Agent 共享全局技能池、无 per-agent 分配，因为当时没有 per-agent 技能选择的需求。现在业务要求：

1. Agent 创建时从全局技能池选择需要绑定的技能
2. 技能内容**复制**到 Agent 的独立 workspace 中运行（不搞软连接，因 Windows 下需要管理员权限）
3. 全局技能更新/删除时，系统能追踪哪些 Agent 使用了该技能，提供联动操作
4. Agent 可随时解绑技能

## Decision

采用**两层架构**：文件系统作为技能内容的 SSOT，PostgreSQL 作为引用关系的追踪系统。

```
GitHub (GitSkillRepository)
    ↓ git clone
workspace/skills/  ← 文件系统 SSOT（技能内容唯一权威来源）
    ↓ 同步元数据
PostgreSQL (skills 表)  ← 技能元数据索引
    ↓ Agent 创建时选择 → 复制 + 记录引用
workspace/agent-{id}/skills/<name>/  ← Agent 运行时副本
    ↑
PostgreSQL (agent_skills 表)  ← 引用关系追踪
```

## Details

### 全局技能池管理
- 管理员通过 `SkillManage.vue` 从 GitHub（`GitSkillRepository`）或 ZIP 导入技能到 `workspace/skills/`
- 文件系统为 SSOT，`skills` 表作为元数据索引（名称、描述、版本、来源）
- 沿用现有 `skills-lock.json` 去重机制

### Per-Agent 绑定
- Agent 创建/编辑时，用户从全局技能池勾选要绑定的技能
- 系统将技能文件（SKILL.md + resources）复制到 `workspace/agent-{id}/skills/<name>/`
- 在 `agent_skills` 表中记录 `agent_id` 与 `skill_id` 的引用关系

### 更新联动
- 管理员更新全局技能时，查询 `agent_skills` 表获取受影响 Agent 列表
- 管理员选择是否将更新推送到各 Agent workspace（重新复制）

### 删除联动
- 管理员删除全局技能时，查询 `agent_skills` 表
- 如仍有 Agent 引用：提示管理员选择「强制全部删除」或「暂缓，等解绑后再删」
- 强制删除：从所有 Agent workspace 清理副本 + 清理引用记录 + 删除全局池

### 解绑
- 用户从 Agent 编辑页移除技能 → 删除 workspace 副本 + 移除 `agent_skills` 记录

## Considered Options

- **纯文件系统 + 引用追踪（选定）**：技能内容存文件，PostgreSQL 存引用关系。
  - 正面：技能内容本身就是文件（SKILL.md + 附属资源），文件系统是最自然的存储方式；数据库做关系查询天然高效
  - 负面：更新联动时需要遍历 Agent workspace 做文件复制操作

- **全 DB 存储**：技能内容也存入 PostgreSQL。
  - 正面：更新联动只需写数据库，无需文件操作
  - 拒绝原因：技能本质是 Markdown 文件 + 附属资源，存入 DB 需要额外的序列化/反序列化，且失去直接文件编辑的灵活性；AgentScope SDK 的 `FileSystemSkillRepository` 原生支持文件系统

- **纯引用（软连接）**：Agent 直接引用全局池技能，不复制。
  - 正面：零存储冗余，全局更新自动对所有 Agent 生效
  - 拒绝原因：Windows 下创建软链接需要管理员权限，用户环境不可控

## Consequences

- ADR-0001 的「全局所有 Agent 共享技能」决策被废弃
- 新增 `skills` 表（元数据索引）和 `agent_skills` 表（引用关系）
- Agent 创建/编辑页面需要新增技能选择 UI
- 全局技能更新需要实现「推送更新到 Agent workspace」的逻辑
- 磁盘空间会因技能副本产生一定冗余（但技能本质是文本文件，冗余量极小）
- `AgentInitService` / `WorkspaceInitService` 需要扩展技能复制逻辑
