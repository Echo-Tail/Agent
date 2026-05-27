# CONTEXT.md — EcomAgents 企业电商智能体管理平台

## 项目结构

```
Agent/                        # 项目根
├── EcomAgents/               # Spring Boot 后端 (Gradle, Java 17, port 8888)
│   ├── build.gradle
│   └── src/main/java/cafe/snails/ecomagents/
│       ├── config/           # WebConfig (CORS), DataInitializer (种子数据), LlmConfig
│       ├── dto/              # ApiResponse, GroupMemberDTO（含用户名）, 请求/响应 DTO
│       ├── model/            # JPA 实体: User, Agent, Session, SessionMessage, SessionFolder, InviteCode, AiModel, ToolConfig, ChatGroup, GroupMember, GroupAgent, GroupMessage, GroupFile, ChatPrivateMessage, EmojiPack, UserEmojiFavorite
│       ├── repository/       # Spring Data JPA Repositories
│       ├── service/          # 业务逻辑层 + HarnessAgentManager (HarnessAgent 生命周期管理)
│       ├── controller/       # REST 控制器 (/v1/*, /chat/*)
│       ├── harness/          # HarnessAgent 集成层: hooks, workspace 管理, session 映射
│       └── dto/              # ApiResponse, 请求/响应 DTO
├── EcomAgentsFront/          # 前端 SPA (Vue 3 + Naive UI + TypeScript) — 即将废弃
│   ├── vite.config.ts        # Vite 构建配置 + 开发代理
│   ├── tsconfig.json
│   ├── src/
│   │   ├── layouts/          # DefaultLayout (侧边栏) / BlankLayout
│   │   ├── router/           # Vue Router + 导航守卫
│   │   ├── stores/           # Pinia (auth, theme, agent, chat, knowledge)
│   │   ├── api/              # Axios + 各模块 API
│   │   └── views/            # 13 个路由级页面组件
│   └── vitest.config.ts
├── ShadcnAgentUI/            # 新前端 SPA (Vue 3 + shadcn-vue + Tailwind CSS) — 替代 EcomAgentsFront
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── src/
│   │   ├── layouts/          # DefaultLayout (侧边栏) / BlankLayout
│   │   ├── router/           # Vue Router + 导航守卫
│   │   ├── stores/           # Pinia (auth, theme, agent, chat, knowledge)
│   │   ├── api/              # Axios + 各模块 API (含 group API)
│   │   ├── views/            # 页面组件: group/(群列表/群聊天), message/(私信列表/私聊), 登录/注册/Dashboard/Agent/Chat
│   │   └── components/       # MentionInput (@Agent 补全), EmojiPicker, GroupFileDialog, InviteMemberDialog
│   └── components.json       # shadcn-vue 配置
├── cli.bat                   # 代理隧道启动器
└── CONTEXT.md                # 本文档 — 领域模型与架构决策
```

## 核心概念

### Agent（智能体）
用户创建的 AI 助手实例。包含字段：名称、角色设定（System Prompt）、简介/描述、头像/图标、欢迎语、关联模型、关联知识库、关联技能、关联工具、RAG 模式（Generic / Agentic）。
- 每个 Agent 在磁盘上有独立 workspace 目录：`workspace/agent-{id}/`
- Agent 创建时同步初始化 workspace（AGENTS.md, knowledge/, sessions/, skills/ 等）
- HarnessAgent 实例懒加载缓存，每个 Agent 对应一个 HarnessAgent
- **所有权隔离**：Agent 通过 `createdBy` 字段归属创建者。仅创建者和管理员可以编辑/删除 Agent。普通用户只能编辑自己创建的 Agent。
- **Agent 广场**：用户可以在 Agent 广场浏览他人创建的 Agent 并使用其对话，但无法修改他人 Agent 的配置。
- **聊天记录隔离**：聊天记录严格按会话用户隔离，Agent 创建者无法查看使用者的对话。

### 工具（Tool）

**内置工具（第一阶段可用）** — AgentScope HarnessAgent 自动注册，对所有 Agent 默认可用：
- **Filesystem** — read_file, write_file, edit_file, grep_files, glob_files, list_files
- **Memory** — memory_search, memory_get（FTS5 全文搜索 + 持久化跨会话记忆）
- **Session** — session_search, session_list, session_history（基于 JSONL 文件）
- **Sub-agent** — agent_spawn, agent_send, agent_list / sessions_spawn, sessions_send, sessions_list
- **Task** — task_output, task_cancel, task_list
- **Shell（条件性）** — execute（仅当 backend 为 AbstractSandboxFilesystem 时注册）

**外部工具（第二阶段实现）** — 用 AgentScope Java `@Tool` 注解实现的 Java 类：
- Web 搜索（Tavily / Firecrawl）— 已实现
- 图片生成（DALL-E / Stable Diffusion）— 待实现

外部工具的启用/禁用通过 DB `tool_configs` 表管理，在 ToolManage.vue 中配置。

**已移除的外部工具项**（从 `tool_configs` 种子数据中删除）：
- `file_operation` — 由 HarnessAgent 内置 Filesystem 工具覆盖，自动注册
- `memory_read` — 由 HarnessAgent 内置 Memory 工具覆盖，自动注册
- `browser_automation` — 移至技能管理范畴，待定
- `code_execution` — 移至技能管理范畴，待定

### 模型（Model）
LLM 后端配置。由管理员维护可用模型列表（Qwen、OpenAI 兼容接口等），用户在创建 Agent 时从中选择。
每个模型有 `modelType`（TEXT / IMAGE），用于区分文本模型与图片生成模型。

### Token 用量统计
每次 LLM 调用的记录，包含模型、用户、Agent、token 数、时间、成功/失败状态。管理员在 Token 用量页面按日期区间查询：
- 各模型的调用次数
- 各模型的 token 消耗量（prompt_tokens + completion_tokens）
- 图片模型的调用次数

token 计数在 `HarnessChatService.streamChat()` 中完成，使用 jtokkit（Java tiktoken 移植库）对输入输出文本做精确分词计数。

### 会话（Conversation）
用户与 Agent 之间的完整对话记录。
- **消息存储**: HarnessAgent JSONL 文件（`workspace/agent-{id}/sessions/{sessionId}.jsonl` + `.log.jsonl`）
- **会话索引**: `workspace/agent-{id}/sessions/sessions.json`
- **前端展示**: DB `sessions` 表同步存储标题、创建时间等元数据，供前端列表/文件夹展示
- **sessionId**: 使用 `"sess-{agentId}-{uuid}"` 格式

### 知识库（Knowledge Base）
文档集合，Agent 通过 RAG（PgVector 向量检索）增强回答。与 Agent 多对多关联（一个 Agent 可绑定多个知识库，一个知识库可被多个 Agent 引用）。
- **建库权限**：仅管理员可以创建/删除知识库。普通用户可以上传、修改、删除知识库内的文件。
- **文件操作审计**：所有用户的文件上传/修改/删除操作记录到审计日志表（`knowledge_audit_log`），记录用户、操作类型（UPLOAD/MODIFY/DELETE）、知识库 ID、文件名、IP、时间戳。
- **向量存储**：基于 PostgreSQL + PgVector，一张全局 `knowledge_embeddings` 表 + `knowledge_base_id` 列区分归属。查询时按知识库 ID 过滤。
- **RAG 模式**：每个 Agent 独立配置，可选 **Generic**（自动检索注入）或 **Agentic**（Agent 自主决定何时检索），默认 Agentic。
- **支持格式**：TXT、Markdown、JSON 等文本格式
- **知识库内容同步**：文档变更后自动重新生成对应 Agent workspace 的 `knowledge/KNOWLEDGE.md`

### Skill（技能/能力包）
标准化可复用操作指南，Markdown 格式。全局技能池由管理员管理，Agent 创建时可选择绑定技能。

**架构决策 — 文件系统 SSOT + 数据库引用管理：**
- **存储（全局池）**：纯文件系统，`workspace/skills/` 目录为全局技能池 SSOT。每个技能一个子目录（`<skill-name>/SKILL.md` + YAML frontmatter）
- **索引与引用管理**：PostgreSQL `skills` 表记录技能元数据（名称、描述、版本、来源等），`agent_skills` 映射表追踪 Agent 与技能的引用关系
- **Agent 绑定方式**：**复制式绑定**。Agent 创建/编辑时从全局池选择技能，系统将技能内容**复制**到 Agent workspace 的 `skills/<name>/` 目录。Agent 拥有独立副本，修改副本不影响全局池。
- **生命周期管理**：
  - **全局技能更新**：管理员触发更新时，系统查询 `agent_skills` 映射表，展示受影响的 Agent 列表，由管理员决定是否推送到各 Agent workspace
  - **全局技能删除**：管理员触发删除时，系统查询 `agent_skills` 映射表，提示"仍被 N 个 Agent 使用"，管理员可选择：
    - **强制删除**：从全局池删除并从所有 Agent workspace 中清理副本
    - **暂缓删除**：保留全局池，待所有 Agent 解绑后再删除
  - **Agent 解绑技能**：删除 Agent workspace 中的技能副本 + 移除 `agent_skills` 映射记录
- **SDK**：全局池操作基于 AgentScope `FileSystemSkillRepository`。引用管理使用自建 `PgSkillRepository`（PostgreSQL 版，参考 `MysqlSkillRepository`）
- **管理功能**：导入（GitHub URL / ZIP 上传）、列表、更新、删除
- **GitHub 导入**：通过 `GitSkillRepository` 从 GitHub 仓库拉取，沿用 gh-proxy 加速方式
- **UI**：`SkillManage.vue` 仅管理员可见。Agent 创建/编辑页面展示技能选择 UI（从全局技能池勾选）

**与 Tool 的区别：**
- **Tool（工具）** — AI 执行操作的"手脚"，Java `@Tool` 注解实现，per-agent **引用式绑定**
- **Skill（技能）** — AI 的"大脑方法论"，标准操作流程文档（Markdown），per-agent **复制式绑定**

## 用户角色

- **管理员（Admin）** — 管理系统配置、模型、用户、创建知识库、管理全局技能池、查看所有 Agent
- **普通用户（User）** — 创建管理自己的 Agent、使用聊天和 Agent 广场、上传/修改/删除知识库文件

## 认证与授权

- 认证方式: 用户名 + 密码
- Token: JWT, localStorage 持久化
- 注册: 邀请码制
- Vue Router `beforeEach` 守卫 + Axios 401 拦截器

## 架构决策

### 前端通信协议
- **CRUD 管理功能**: REST API `/v1/*`（Spring Boot），Axios 封装
- **Agent 聊天**: SSE (`POST /chat/{agentId}/stream`)，通过 Hook 拦截 HarnessAgent 事件实现步骤级可视化

### 后端架构 — HarnessAgent 集成

```
ChatController
  → HarnessAgentManager.getHarnessAgent(agentId)  // 从缓存取或懒加载
  → 创建 RuntimeContext(sessionId, userId)
  → agent.call(msg, ctx)                           // HarnessAgent ReAct 循环
  → Hook 拦截 PreReasoningEvent / PostToolEvent    // 推 SSE 事件
  → 返回最终 Msg → 推 done 事件
```

**SSE 事件流（基于 Hook 系统）**：
```
type: "reasoning"  → "正在分析需求..."
type: "tool_call"  → { tool: "read_file", status: "running" }
type: "tool_result" → { tool: "read_file", status: "done", summary: "已读取 3 个文件" }
type: "token"      → "最终答案的文本块..."
type: "done"       → 完整文本
```

### Workspace 管理
- 根目录: `workspace/`（相对于 Spring Boot 进程工作目录 `EcomAgents/`）
- 每个 Agent 的子目录: `workspace/agent-{id}/`
- 初始化时机: Agent 创建时（在 AgentService.createAgent 中同步初始化）
- **AGENTS.md** = Agent 的 systemPrompt，双向同步（修改 systemPrompt 时重写文件）
- **MEMORY.md** = HarnessAgent 自动维护的跨会话记忆，**所有用户共享**（框架限制，详见多租户设计）

### 外部工具
- **第一阶段（已完成）**: 仅使用 HarnessAgent 内置工具，Agent 创建界面隐藏工具选择
- **第二阶段（当前）**: 逐个实现外部工具的 Java `@Tool` 类，恢复工具选择 UI
  - `web_search` — 通过 Tavily API 实现网页搜索（Spring WebClient），LLM 可控参数：query（必选）、max_results（可选，默认5）
  - `image_generation` — 待实现
  - `browser_automation` — 已移除，移至技能管理范畴，待定
  - `code_execution` — 已移除，移至技能管理范畴，待定
- **第三阶段（本次重构）**: per-agent 工具绑定，Agent 创建/编辑时从启用的全局工具池中勾选

### HAR 对比（Built-in vs External）

| 维度 | 内置工具 (HarnessAgent) | 外部工具 (第二阶段) |
|------|------------------------|-------------------|
| 注册方式 | 自动注册，无需手动操作 | 手动编写 `@Tool` Java 类 |
| UI 可见性 | 不展示在工具管理界面 | 展示在 ToolManage.vue |
| 启用/禁用控制 | 默认全开，所有 Agent 可用 | DB `tool_configs` 表管理 |
| 配置 | 无（无需 API Key） | 需要配置 API Key（Tavily、Firecrawl 等） |
| 执行位置 | Agent 进程内 | 可在沙箱内隔离运行 |

## 多租户设计

### 数据隔离层级

| 层级 | 隔离粒度 | 方案 | 状态 |
|------|---------|------|------|
| Agent 人格（AGENTS.md） | per-agent | 每个 Agent 独立 workspace：`workspace/agent-{id}/AGENTS.md` | ✅ 已实现 |
| 会话元数据 | per-user | DB `sessions` 表记录 userId，按用户查询过滤 | ✅ 已实现 |
| 会话消息 | per-user | DB `session_messages` 表通过 FK 关联 session，按用户隔离 | ✅ 已实现 |
| 会话文件（JSONL） | per-user | sessionId 嵌入 userId 实现文件级可追溯：`sess-{agentId}-{userId}-{uuid}` | ✅ 已实现 |
| 长期记忆（MEMORY.md） | **per-agent 共享** | MEMORY.md 位于 workspace 根目录，所有用户共享同一份记忆文件 | ⚠️ 接受限制 |

### 记忆共享决策

**决策**: 跨用户记忆共享（2026-05-25）

**原因**: AgentScope Java SDK 的 `MemoryFlushHook` 写死了 `WorkspaceConstants.MEMORY_MD` 路径到 workspace 根目录，且 `MemoryMaintenanceHook` 在同一路径上操作。在无 fork SDK 的前提下，无法实现 per-user MEMORY.md 隔离。

**影响**:
- 同一 Agent 的不同用户之间，长期记忆（MEMORY.md）会互相影响
- 会话级别的内容（消息、历史）严格按用户隔离，不受影响
- 如果两个用户讨论了高度私密的内容，Agent 可能在 MEMORY.md 中合并记忆

**未来可能的改进路径**:
- AgentScope SDK 后续版本支持多租户 memory 时升级
- 或自行实现 Hook 替换 MemoryFlushHook，改为按 userId 读写不同路径

### Session ID 格式

`HarnessAgent` 使用的 sessionId 格式：`sess-{agentId}-{userId}-{uuid}`

其中 `{userId}` 嵌入使 JSONL 文件名可追溯用户归属。旧格式 `sess-{agentId}-{uuid}` 仍在 DB 中保留，仅新创建会话使用新格式。

## SSOT（单一真相源）

| 数据 | 主存储 | 衍生/同步 |
|------|--------|-----------|
| 会话消息 | HarnessAgent JSONL | — |
| 会话元数据 | DB `sessions` 表 | 从 JSONL 摘要同步 |
| Agent 配置 | DB `agents` 表 | AGENTS.md 同步 |
| Agent-技能引用 | DB `agent_skills` 表 | Agent workspace 技能副本的追踪 |
| 工具配置 | DB `tool_configs` 表 | — |
| Agent-工具绑定 | DB `agent_tools` 关联表 | per-agent 工具引用 |
| 模型配置 | DB `ai_models` 表 | — |
| 知识库元数据 | DB `knowledge_bases` 表 | — |
| 知识库文档 | DB `knowledge_documents` 表 | — |
| 知识库向量 | DB `knowledge_embeddings` 表（PgVector） | 从文档异步构建 |
| 知识库审计 | DB `knowledge_audit_log` 表 | 文件操作审计记录 |
| 技能内容（全局池） | `workspace/skills/` 文件系统 | DB `skills` 表（元数据索引） |
| 技能引用关系 | DB `agent_skills` 表 | Agent ←→ 技能映射 |
| 跨会话记忆 | HarnessAgent MEMORY.md（**跨用户共享**） | — |
| 群聊信息 | DB `chat_groups` 表 | — |
| 群成员 | DB `group_members` 表 | — |
| 群 Agent 绑定 | DB `group_agents` 表 | — |
| 群消息 | DB `group_messages` 表 | — |
| 群文件 | DB `group_files` 表 | 磁盘文件元数据 |
| 用户私聊消息 | DB `chat_private_messages` 表 | — |
| 表情包 | DB `emoji_packs` 表 + `user_emoji_favorites` | — |

### 群聊（Group Chat）
多用户多 Agent 的群组聊天空间，独立于一对一的 Session 体系。

#### 核心概念

- **群（Group）** — 聊天室的容器，包含成员列表、绑定的 Agent 列表、消息流。群名、头像由创建者管理。头像图片存储于服务器 `./uploads/` 目录，`ChatGroup.avatar` 字段存其 URL。
- **Agent 头像** — Agent 支持两种头像方式：`icon`（Bootstrap Icons 类名）和 `avatar`（自定义上传图片 URL）。两者并存，`avatar` 优先级高于 `icon`。前端渲染时优先显示 `avatar`，回退到 `icon`。
- **成员角色**：
  - **创建者（CREATOR）** — 创建群的用户，拥有全部管理权限：解散群、踢人、修改群信息。
  - **普通成员（MEMBER）** — 可发送/接收消息、上传下载文件、发送表情包、邀请新用户入群、拉入自己创建的 Agent。
- **群 Agent 绑定** — 成员将自己创建的 Agent 拉入群，群内任何用户都可 `@AgentName` 与该 Agent 一对一对话。Agent 与 Agent 之间暂不支持互艾特。
- **成员信息** — 群成员列表返回的用户信息和 Agent 信息均从各自的实体表实时读取（昵称/名称、头像/图标），不额外存储群内副本。
- **统一成员视图** — `group_members` 表（存用户）和 `group_agents` 表（存 Agent）各自保持独立不合并，查询时通过后端 UNION 合成统一的成员列表，每条记录带 `memberType`（USER / AGENT）区分来源。`GroupMember.role`（CREATOR / MEMBER）仅对用户有意义，Agent 统一视为 MEMBER。
- **邀请逻辑** — 邀请入口显示"可邀请的用户"（系统内所有非群成员的用户）和"可邀请的 Agent"（当前用户创建且未入群的 Agent）。

#### 权限矩阵

| 行为 | 创建者 | 普通成员 | 非成员 |
|------|--------|---------|--------|
| 解散群 | ✅ | ❌ | ❌ |
| 踢人 | ✅ | ❌ | — |
| 邀请新成员 | ✅ | ✅ | — |
| 拉入自己的 Agent | ✅ | ✅ | — |
| 发送消息 | ✅ | ✅ | ❌ |
| 发送文件/表情包 | ✅ | ✅ | ❌ |
| 下载他人文件 | ✅ | ✅ | ❌ |
| 修改群信息 | ✅ | ❌ | ❌ |

#### @Agent 消息格式

艾特 Agent 的消息体使用 Markdown 链接格式：`@[Agent名称](agent:123)`。
- 前端：用户输入 `@` 触发自动补全，选择后插入 `@[名称](agent:id)`
- 后端：解析 `(agent:数字)` 获取精确 Agent ID，异步触发推理
- 渲染：现有 MarkdownRenderer 将 `[Agent名称](agent:123)` 渲染为可点击链接
- 优点：Agent 改名后历史链接仍然有效；同名 Agent 不会混淆

#### 用户私聊（Private Message）

独立于群聊和 Agent 会话的 1 对 1 用户聊天。存储在 `chat_private_messages` 表中。
入口：侧边栏新增「消息」路由 `/messages`，也可通过用户搜索、群成员列表、用户头像触发发起私聊。
初期功能：纯文本 + 文件发送/下载，不做已读未读和撤回。

#### 表情包

- 内置库：系统预置图片表情（emoji_packs 表），用户可在群聊/私聊中使用
- 用户上传自定义表情：延后实现

#### Session（一对一私聊）与 Group Chat 的区别

| 维度 | Session | 群聊 |
|------|---------|------|
| 参与方 | 1 用户 + 1 Agent | N 用户 + N Agent |
| 消息流向 | 用户↔Agent 双向 | 用户↔群、用户 @Agent↔Agent |
| Agent 消息触发 | 用户发送后自动回复 | 仅当被 `@AgentName` 艾特时回复 |
| 文件共享 | 上传后仅在当前对话上下文中使用 | 群内所有成员可下载 |
| 存储 | HarnessAgent JSONL + DB 同步 | 待定 |

## 数据库

| 环境 | 数据库 | 说明 |
|------|--------|------|
| 开发 | PostgreSQL | 需安装 pgvector 扩展 |
| 生产 | PostgreSQL | 需安装 pgvector 扩展 |

## 部署
- **方式**：单体部署（Spring Boot jar + Vite build），本地 Windows 运行
- **沙箱**：暂不启用。AgentScope Java SDK 1.1.0-RC1 支持 DockerFilesystemSpec（每会话独立容器），但当前评估认为复杂度高于收益，推迟实现。
  - Docker 环境：WSL2 Ubuntu 26.04 原生 docker-ce，IP 192.168.2.107
  - 若无沙箱，Agent 的 `execute` 工具会在宿主 Windows 上直接运行脚本（风险接受）
  - 未来如需启用，配置 `agentscope.sandbox.docker.host=tcp://192.168.2.107:2375` + HarnessAgent.Builder.filesystem(DockerFilesystemSpec)
