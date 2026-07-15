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

### ~~画廊（Gallery）~~ [已废弃]
画廊功能已废弃并在清理中，由「公共素材库」取代。相关代码逐步移除：
- `GalleryItem` 实体 / `gallery_items` 表 → 待清理
- `GalleryController` / `GalleryService` / `GalleryItemRepository` → 待清理
- `GalleryView.vue` / `api/gallery.ts` → 待清理
- 侧边栏"画廊"菜单项 → 已移除，替换为"公共素材库"

### 公共素材库（Public Asset Library）
所有用户共享的图片素材池，用于图生图时选取参考图。入口为侧边栏一级菜单"公共素材库"（路由 `/agents/assets`）。

#### 核心概念

- **素材（Asset）** — 单张图片文件，用户手动上传到素材库。支持格式：JPEG、PNG、WebP。存储在 `uploads/assets/` 目录。来源包括：本地上传、从生成结果「上传到素材库」。
- **素材空间（Asset Space）** — 类似文件夹的分类容器，空间名**全局唯一**。所有用户共享空间池，上传时从已有空间列表选择一个，或新建一个。创建者和管理员可以修改/删除空间，普通用户只能使用。
- **所有权隔离**：素材通过 `uploaded_by` 字段归属上传者。普通用户只能删除自己上传的素材。管理员可以删除任何素材。

#### 数据模型

`asset_spaces` 表：
- `id` (PK), `name` (全局唯一), `description` (可选), `created_by` (FK → users), `created_at`, `updated_at`

`public_assets` 表：
- `id` (PK), `file_name` (原始文件名), `file_path` (服务器路径), `file_size` (字节), `mime_type`
- `space_id` (FK → asset_spaces), `uploaded_by` (FK → users), `created_at`

#### 权限矩阵

| 行为 | 上传者 | 其他用户 | 管理员 |
|------|--------|---------|--------|
| 上传素材到任意空间 | ✅ | ✅ | ✅ |
| 删除自己上传的素材 | ✅ | ❌ | ✅ |
| 删除他人上传的素材 | ❌ | ❌ | ✅ |
| 创建素材空间 | ✅ | ✅ | ✅ |
| 修改/删除自己创建的空间 | ✅ | ❌ | ✅ |
| 修改/删除他人创建的空间 | ❌ | ❌ | ✅ |

#### 与图生图的集成
- 图生图编辑 Tab 中，参考图来源增加"从素材库选择"入口
- 选择后打开素材浏览器弹窗（按空间筛选），选中图片后作为参考图自动填充
- 生成结果卡片增加"上传到素材库"按钮，选择目标空间后存入

### 图片生成（Image Generation）
独立于 Agent 体系的图片生成功能，入口为侧边栏"Agent 广场"下方的"图像生成"菜单项（路由 `/agents/image`），所有用户可用。

#### 图片生成运行时（Image Generation Runtime）

统一承载文生图与图生图用例的 Job-first 运行时。调用方提交类型安全的文生图或图生图命令并获得内部 `jobId`，随后查询任务状态与结果；不直接感知供应商任务 ID、轮询协议或临时图片 URL。

- **执行模型**：Job-first。同步供应商可以立即完成；异步供应商由运行时提交并轮询。
- **HTTP interface**：新调用入口为 `POST /v1/image-jobs`（返回 `202 + jobId`），查询入口为 `GET /v1/image-jobs/{jobId}` 和 `/results`，取消与人工重试分别使用 `POST /cancel`、`POST /retry`。
- **前端进度**：首版使用轮询，不新增 SSE。初始每秒查询，长任务逐步退避到每 3 秒；页面刷新后按 jobId 恢复，进入终态后停止。部分成功结果可提前展示。
- **兼容迁移**：现有 `POST /v1/images/generate` 与 `/v1/images/edit` 保留一个迁移周期，内部转调图片生成运行时并标记废弃；新前端只使用 `/v1/image-jobs`。
- **任务调度**：数据库任务表是任务状态 SSOT，后台 Worker 通过抢占与租约执行任务；不以进程内 Future 或线程池状态作为任务真相源。服务重启后可恢复未完成任务。
- **任务状态**：`PENDING` → `RUNNING` → `SUCCEEDED` / `PARTIALLY_SUCCEEDED` / `FAILED`；取消流程使用 `CANCEL_REQUESTED` → `CANCELLED`。
- **取消语义**：取消采用 best-effort。`PENDING` 可立即取消；`RUNNING` 先进入 `CANCEL_REQUESTED`，Adapter 支持远程取消时等待确认，不支持时停止本地后续处理并记录 `PROVIDER_CANCEL_UNSUPPORTED`。取消不承诺停止供应商计费。
- **取消竞争**：终态不可取消；取消与完成并发时使用数据库条件更新，由先成功提交的终态获胜。已经落盘的图片不因取消自动删除，供应商迟到响应不进入正式结果，只写安全审计记录。
- **可靠性**：Worker 使用 PostgreSQL `FOR UPDATE SKIP LOCKED` 原子领取任务，写入 `workerId`、`leaseUntil` 和 `attemptCount`，执行期间续租；失去租约的任务可被重新领取或进入明确失败状态。
- **内部执行阶段**：外部任务状态保持 `RUNNING`，内部记录 `PREPARING`、`SUBMITTING`、`POLLING`、`DOWNLOADING`、`PERSISTING`，用于恢复和判断重试安全性。
- **安全重试**：限流、明确的临时故障、已保存 taskId 的轮询、临时图片下载和本地落盘允许自动重试并使用指数退避。供应商支持幂等键时使用 `jobId + outputIndex`。
- **不确定提交**：供应商可能已接受请求、但本地尚未保存 taskId 时，标记 `SUBMISSION_OUTCOME_UNKNOWN`，禁止自动重新提交，避免重复生成和扣费。人工重试创建新任务并记录 `retryOfJobId`，不覆盖原任务。
- **模型配置 SSOT**：每次调用按 `modelId` 解析当前 `AiModel`。模型新增时不创建或长期注册“每模型实例”，避免地址、密钥、启停状态与内存实例不一致。
- **任务模型快照**：提交任务时保存供应商、远程模型名、请求地址、能力与生成参数等非敏感调用快照，保证任务可复现；不复制 API Key，只保存模型 ID 或凭据引用，Worker 执行时读取最新密钥以支持密钥轮换。
- **凭据模块**：API Key 从 `AiModel` 明文字段迁移到独立 `model_credentials` 表；模型与能力配置只保存 `credentialId`。凭据使用 AES-GCM 加密，主密钥从环境变量或外部密钥管理注入，数据库和仓库均不保存主密钥。
- **凭据安全**：后端只返回 masked hint；Adapter 调用前才解密，原文不进入任务快照、日志或异常。删除仍被引用的凭据时拒绝操作，创建、轮换、删除写入不含秘密的审计记录。
- **凭据迁移与替换**：迁移现有 `AiModel.apiKey` 时按不同密钥创建凭据记录，完成后删除明文字段。本地加密 implementation 后续可替换为 KMS Adapter，而不改变图片生成运行时 interface。
- **配置变更语义**：模型提交后被禁用时，已提交任务继续执行，禁用只阻止新任务；模型名或请求地址后续修改不影响已提交任务。
- **供应商 seam**：运行时按供应商选择无状态 Adapter。首批 Adapter 为阿里百炼与 OpenAI-compatible；第三方请求结构、鉴权、任务轮询、响应解析和错误转换保留在对应 Adapter 内。
- **运行时职责**：模型与能力校验、任务状态、拆批与并发、结果稳定排序、临时结果下载、图片落盘、历史记录、用量统计和安全错误映射。
- **Adapter 职责**：供应商通信。Adapter 不写业务数据库、不保存本地图片、不创建历史记录。
- **参考图快照**：图生图任务在提交阶段将上传文件、公共素材或产品图复制到 `uploads/image-jobs/{jobId}/inputs/{index}`，Worker 只读取任务专属不可变快照，不依赖请求生命周期或可变来源文件。
- **输入记录**：`image_generation_job_inputs` 按 `jobId + inputIndex` 保存输入角色、来源类型、原始来源 ID、快照路径、MIME、文件大小和 SHA-256，用于稳定排序、追溯与完整性校验。
- **提交校验**：创建 `PENDING` 任务前完成参考图读取、格式、大小和安全校验；输入无效时直接拒绝，不把必然失败的任务交给 Worker。
- **输入保留**：`SUCCEEDED` / `PARTIALLY_SUCCEEDED` 的输入快照默认保留 30 天，`FAILED` / `CANCELLED` 默认保留 7 天；`PENDING` / `RUNNING` 禁止清理。周期通过配置调整，并由每日清理任务执行引用检查后删除。
- **输出保留**：生成结果持续保留到用户删除历史记录。供应商临时 URL 和原始响应在任务结束后不保留；任务元数据与安全错误摘要持续保留。
- **默认配置**：`image.job.input-retention-days=30`、`image.job.failed-input-retention-days=7`、`image.job.cleanup-cron=0 30 3 * * *`。
- **结果语义**：多图任务允许部分成功；每张目标图片拥有稳定 index 和独立状态，供应商并发完成顺序不改变结果顺序。
- **能力表达**：文生图与图生图使用不同命令类型，但共享同一个图片生成运行时。模型能力通过 `ai_model_capabilities` 关系表保存可组合能力集合，新逻辑不再依赖单值 `modelType`。
- **首批模型能力**：`CHAT`、`TEXT_TO_IMAGE`、`IMAGE_TO_IMAGE`。模型管理创建/编辑时显式选择；运行时按目标能力筛选和校验模型。
- **能力约束**：模型配置能力必须是对应供应商 Adapter 支持能力的子集；前端按能力决定可用模式和参数，不以 `provider` 判断功能。
- **能力协议**：`ai_model_capabilities` 为每项能力独立记录 `protocol` 和经过 schema 校验的 `optionsJson`。运行时通过 `(protocol, capability)` 选择 Adapter，允许同一模型配置的 CHAT 使用 OpenAI-compatible Chat，而图片能力使用百炼图片任务协议。
- **能力级覆盖**：`AiModel` 保存默认远程模型名、请求地址和凭据引用；`ai_model_capabilities` 可选保存 `modelNameOverride`、`apiUrlOverride`、`credentialRefOverride`。运行时按“能力级覆盖 → 模型级默认”解析不可变调用快照。
- **MULTIMODAL 连接**：同一配置可以让 CHAT 指向专属 MaaS OpenAI-compatible 地址，让 `TEXT_TO_IMAGE` / `IMAGE_TO_IMAGE` 指向百炼图片协议及各自远程模型名，不强制不同能力共享一个 endpoint。
- **Adapter 路由**：`provider` 只承担展示、默认值和供应商校验，不保存 Java 类名，也不决定 implementation；首批协议键为 `OPENAI_COMPATIBLE_CHAT`、`OPENAI_IMAGE`、`BAILIAN_IMAGE`。
- **兼容迁移**：`TEXT` → `CHAT`；`IMAGE` → `TEXT_TO_IMAGE + IMAGE_TO_IMAGE`；`MULTIMODAL` → `CHAT + TEXT_TO_IMAGE + IMAGE_TO_IMAGE`。`modelType` 保留一个迁移周期后删除。

#### 调用架构
```
前端 (/agents/image) → POST /v1/images/generate → 后端 WebClient → PackyAPI
                    → POST /v1/images/edit    → 后端 WebClient → PackyAPI
```
- **后端代理模式**：前端将 prompt 和参考图发到后端，后端调用 PackyAPI，下载生成的图片到本地，返回 URL
- **模型来源**：从 `AiModel` 中查询 `modelType=IMAGE` 且 `enabled=true` 的模型，不通过 ToolConfig 配置
- **认证**：使用 `AiModel.apiKey` 作为 Bearer Token 调用 PackyAPI
- **超时**：由 `image.timeout-seconds` 配置（默认 300 秒），文生图和图生图共用

#### 接口
- **文生图**：`POST /v1/images/generate` — PackyAPI `/v1/images/generations`
- **图生图**：`POST /v1/images/edit` — PackyAPI `/v1/images/edits`，最多 4 张参考图（multipart/form-data 多字段上传）

#### 存储
- 文生图结果：`uploads/generate/` 目录
- 图生图结果：`uploads/edit/` 目录
- 输出格式：`response_format=url`，后端下载后存本地文件，返回服务端 URL

#### 历史记录
- 存储表：`image_generation_records`（新建 JPA 实体）
- 字段：id, userId, mode（GENERATE / EDIT）, prompt, revisedPrompt（API 返回的改写后提示词）, size, quality, resultPath（服务器端图片路径）, timeCostMs（API 调用耗时，毫秒）, createdAt
- 用途：前端 `/agents/image` 页面展示历史生成记录列表

#### 前端参数
| 参数 | 来源 | 用户可见 |
|------|------|---------|
| prompt | 用户输入 | ✅ |
| size | 下拉选择（1024x1024 / 1536x1024 / 1024x1536 / 3840x2160） | ✅ |
| quality | 下拉选择（low / medium / high / auto） | ✅ |
| output_format | 后端固定 `png` | ❌ |
| 参考图（编辑模式） | 本地文件上传，最多 4 张 | ✅ |

### 提示词库（Prompt Library）

团队内部的提示词分享功能，入口为侧边栏"提示词库"菜单项（路由 `/agents/prompts`），所有用户可见。

#### 数据模型 — Prompt

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 自增主键 |
| `prompt` | TEXT, not null | 提示词正文 |
| `category` | VARCHAR(100), not null | 分类 |
| `tags` | VARCHAR(500) | 逗号分隔标签 |
| `coverPath` | VARCHAR(500) | 封面图路径（可选） |
| `createdBy` | Long, not null | 创建者 userId |
| `createdAt` | timestamp | @PrePersist |

#### 权限矩阵

| 行为 | 创建者 | 其他用户 | 管理员 |
|------|--------|---------|--------|
| 浏览全部提示词 | ✅ | ✅ | ✅ |
| 修改自己创建的提示词 | ✅ | ❌ | ✅ |
| 删除自己创建的提示词 | ✅ | ❌ | ✅ |

#### 展示
- 提示词卡片：封面图（上）+ 截断提示词 + 动作按钮（复制/修改/删除/查看）
- 瀑布流布局，一行 4 列
- 搜索与筛选：按创建者（自己/他人）、分类、标签、提示词模糊搜索

### 商品图自动生成器（Product Image Generator）

面向 Amazon 商品图生产的工作流，基于商品数据和参考图产出可人工挑选的商品图候选。
_Avoid_: 单纯称为“提示词生产器”，因为提示词只是中间资产，最终产出是商品图候选。

### ASIN 商品来源（ASIN Product Source）

Amazon 商品素材来源，用于获取商品事实、listing 图片、A+ 图片和商品页面信息。
_Avoid_: 默认把 ASIN 当成最终要生成的目标商品。

### 图片表达来源（Image Expression Source）

用于让多模态模型分析构图、场景、风格、文案结构和卖点表达的图片 URL。
_Avoid_: 称为“参考图”，因为它不一定会作为图生图输入。

### 图片表达结构（Image Expression Structure）

从图片表达来源中提取的可复用视觉 brief，用于描述目标用途、场景、主体关系、构图、风格、文案布局和生成约束。
_Avoid_: 直接称为“prompt”，因为它需要结合产品事实和图生图参考图后才形成最终生成提示词。

### 素材事实（Source Material Facts）

从 ASIN 商品来源或图片表达来源中提取的可参考商品信息和卖点。
_Avoid_: 默认当作自有产品的真实参数。

### 目标产品事实（Target Product Facts）

自有产品的真实参数、功能、配件和限制，是生成图不能违背的事实边界。
_Avoid_: 与 **素材事实** 混用。

### 产品参数文档（Product Parameter Document）

描述自有产品真实参数、功能、配件、兼容性和限制的 Markdown 文档。
_Avoid_: 用竞品 ASIN 数据替代它。

### 产品资料（Product Profile）

系统中维护的自有产品条目，由产品参数文档解析生成，并作为商品图生成任务的目标产品。
_Avoid_: 与 ASIN 商品来源混用。

### 产品资料版本（Product Profile Version）

某次人工确认后的目标产品事实快照，供商品图生成任务引用和复盘。
_Avoid_: 只用最新产品资料解释历史生成结果。

### 默认自有产品图（Default Owned Product Image）

产品资料中长期维护的自有产品图片，可在商品图生成任务中作为图生图参考图。
_Avoid_: 每次生成任务都重复上传同一批产品图。

### 图生图参考图（Image-to-Image Reference）

图生图阶段提交给图片模型、用于约束生成结果的参考图片。
_Avoid_: 与 **图片表达来源** 混用。

### 自有产品图（Owned Product Image）

用户上传的自有商品图片，是最终图生图阶段约束产品外观的主要依据。
_Avoid_: 用 ASIN 或竞品图片替代它作为目标产品身份。

#### 关系

- **商品图自动生成器** 使用 **图片生成（Image Generation）** 作为底层生成能力。
- **商品图自动生成器** 可以沉淀可复用提示词到 **提示词库（Prompt Library）**。
- **提示词库（Prompt Library）** 保存稳定提示词，但不等同于 **商品图自动生成器**。
- **ASIN 商品来源** 和 **图片表达来源** 都是素材来源。
- **图片表达来源** 提供可学习的图片表达结构。
- **素材事实** 可为提示词提供参考卖点，但不能自动替代 **目标产品事实**。
- **产品参数文档** 是 **目标产品事实** 的主要来源。
- **产品资料** 保存一个自有产品的 **目标产品事实**。
- **产品资料版本** 保存一次人工确认后的 **目标产品事实**。
- **默认自有产品图** 属于 **产品资料**，可作为生成任务的 **图生图参考图**。
- **商品图自动生成器** 从 **产品资料** 中选择目标产品。
- **图片表达结构** 与 **目标产品事实** 组合成最终图生图提示词。
- **自有产品图** 通常作为 **图生图参考图**，在生成阶段约束目标产品外观。

### 外部工具
- **第一阶段（已完成）**: 仅使用 HarnessAgent 内置工具，Agent 创建界面隐藏工具选择
- **第二阶段（当前）**: 逐个实现外部工具的 Java `@Tool` 类，恢复工具选择 UI
  - `web_search` — 通过 Tavily API 实现网页搜索（Spring WebClient），LLM 可控参数：query（必选）、max_results（可选，默认5）
  - `image_generation` — 独立页面功能，非 Agent 工具。详见下方"图片生成"
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
入口：侧边栏「消息」路由 `/messages`，也可通过群成员列表、用户头像触发发起私聊。

私聊功能：
- 发送文本消息，支持 Emoji
- 上传文件并作为消息发送（与群聊一致的多文件上传）
- SSE 实时推送
- Markdown 渲染消息内容
- 不做 @提及和撤回

未读消息机制：
- **存储**：`ChatPrivateMessage` 和 `GroupMessage` 实体新增 `read` 布尔字段（默认 false）。用户进入会话时后端标记该会话所有消息为已读。
- **私聊 SSE**：新增基于 userId 的 SSE 端点 `/v1/messages/sse`，消息送达时推送。前端收到后更新 Pinia store 中的未读计数。当前技术栈（SSE）足够，不需要额外 IM 框架。

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


