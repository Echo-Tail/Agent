# EcomAgents — 企业电商 AI 智能体管理平台

[![Java 17](https://img.shields.io/badge/Java-17-blue?logo=openjdk&style=for-the-badge)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-brightgreen?logo=spring&style=for-the-badge)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue_3-4dba87?logo=vuedotjs&style=for-the-badge)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-blue?logo=typescript&style=for-the-badge)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-316192?logo=postgresql&style=for-the-badge)](https://www.postgresql.org/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&style=for-the-badge)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)]()

[![中文](https://img.shields.io/badge/语言-中文-red?style=for-the-badge)](README.md)
[![English](https://img.shields.io/badge/Language-English-lightgrey?style=for-the-badge)](README_EN.md)

📖 **English version available** → [README_EN.md](README_EN.md)

---

![系统概览](docs/image/EcomAgents.png)

一站式企业级 AI 智能体管理平台，基于 [AgentScope Java SDK](https://java.agentscope.io/) 构建。提供智能体生命周期管理、对话交互、工具集成、知识库、图片生成、群聊社交、多租户隔离等完整功能，适用于电商场景下的 AI 自动化运营。

## 功能特性

### 🤖 智能体核心

- **智能体管理** — 创建、编辑、管理 AI 智能体，支持角色设定（System Prompt）、模型分配、欢迎语和标签，多模型自动切换与故障转移
- **所有权隔离** — "我的 Agent"仅展示自己创建的智能体；"Agent 广场"展示他人创建的智能体，可对话但不可编辑
- **SSE 实时对话** — 基于 Server-Sent Events 的流式对话，支持消息历史回溯、会话文件夹分组

### 🛠️ 工具与扩展

- **模型管理** — 管理员可配置多种 LLM 后端（OpenAI、DeepSeek、Qwen、Claude、Gemini 等），支持独立 API Key、接口地址和模型参数
- **工具管理** — 按需启停工具（网页搜索、图片生成等），支持 JSON 配置，每个 Agent 独立绑定
- **技能管理** — 基于文件系统的技能体系，支持 GitHub URL 导入（git clone）和 ZIP 上传，每个 Agent 独立复制绑定

### 📚 知识库

- **文档管理** — 管理员创建知识库，普通用户管理文档（TXT、MD、JSON 格式），支持上传、编辑和删除
- **向量检索** — PgVector 全文语义检索 + 操作审计日志
- **RAG 模式** — 支持 **AGENTIC**（Agent 自主检索）和 **GENERIC**（自动注入上下文）两种模式

### 🖼️ 多媒体生成

- **图片生成** — 集成 gpt-image-2 模型，支持文本生图、风格选择、参数调节
- **画廊** — 用户可将生成的图片发布到画廊，分享作品、点赞互动

### 💬 社交协作

- **群聊系统** — 创建群组、邀请成员、群内对话、文件共享，支持为群组配置 AI Agent 协作
- **私信系统** — 用户之间的点对点私密消息，支持文件传输

### ⚙️ 平台管理

- **用户管理** — 邀请码注册、管理员/普通用户角色、JWT 认证、用户状态管理
- **工单系统** — 用户提交工单，管理员处理反馈，支持工单状态流转和变更记录
- **系统日志** — 按模块/操作类型/用户筛选的系统操作审计日志
- **Token 用量统计** — 记录和查看各模型 Token 消耗情况，按模型和用户维度统计
- **国际化 (i18n)** — 中文/英文界面切换

## 技术栈

### 后端 (EcomAgents/)

| 组件 | 技术 |
|------|------|
| 框架 | Spring Boot 4.0.6 (Web, JPA, Security, Validation) |
| 语言 | Java 17 |
| 数据库 | PostgreSQL 14+ (开发环境 H2) |
| ORM | Hibernate 7 + Spring Data JPA |
| 认证 | JWT (jjwt 0.12.6) + Spring Security |
| 智能体框架 | [AgentScope Java SDK](https://java.agentscope.io/) 1.1.0-RC1 |
| 向量检索 | PgVector (pgvector) |
| 图片生成 | gpt-image-2 / DALL-E / Stable Diffusion |
| 构建 | Gradle |
| 测试 | JUnit 5 + Mockito（70+ 测试类） |

### 前端 (ShadcnAgentUI/)

| 组件 | 技术 |
|------|------|
| 框架 | Vue 3 (Composition API, `<script setup>`) |
| UI 库 | shadcn-vue v2（基于 Reka UI） |
| 语言 | TypeScript 严格模式 |
| 构建 | Vite 8 |
| 状态管理 | Pinia (composition stores) |
| 路由 | Vue Router 4 + 导航守卫 |
| HTTP 客户端 | Axios（拦截器注入 Token） |
| 测试 | Vitest + happy-dom |
| 流式通信 | SSE via ReadableStream API |
| 国际化 | Vue I18n（中文/英文） |
| 包管理 | pnpm（推荐）/ npm |

## 项目结构

```
Agent/
├── EcomAgents/                          # ← Spring Boot 后端 (Java 17, Gradle, port 8888)
│   └── src/main/java/cafe/snails/ecomagents/
│       ├── config/                      # CORS、安全、数据初始化、LLM 配置、AgentScope 配置
│       ├── controller/                  # REST 控制器 (25 个控制器)
│       ├── dto/                         # 请求/响应 DTO (30+ DTO)
│       ├── exception/                   # 全局异常处理 + 业务错误码
│       ├── harness/                     # AgentScope HarnessAgent + SSE 集成层
│       ├── model/                       # JPA 实体 (30+ 实体)
│       ├── repository/                  # Spring Data JPA 仓储 (25+ Repository)
│       ├── security/                    # JWT + Spring Security 认证体系
│       ├── service/                     # 业务逻辑层 (25+ Service)
│       │   └── rag/                     # RAG 知识检索服务
│       └── tool/                        # AgentScope @Tool 注解工具
├── ShadcnAgentUI/                       # ← Vue 3 SPA 前端 (TS, Vite, shadcn-vue, port 5174)
│   └── src/
│       ├── api/                         # Axios HTTP 客户端层 (17 个 API 模块)
│       ├── components/                  # 业务组件 + shadcn-vue UI 组件
│       │   └── ui/                      # Button, Card, Dialog, Table, Badge 等
│       ├── constants/                   # 共享常量 (API 地址、存储键、验证限制)
│       ├── layouts/                     # DefaultLayout (侧边栏) / BlankLayout (全屏)
│       ├── locales/                     # 国际化 (zh-CN / en)
│       ├── router/                      # Vue Router + 导航守卫
│       ├── stores/                      # Pinia stores (auth/theme/agent/chat/knowledge/unread)
│       ├── types/                       # TypeScript 类型定义 (agent/api/group/knowledge/session/ticket)
│       ├── utils/                       # 验证工具函数
│       └── views/                       # 路由页面组件
│           ├── admin/                   # ModelManage/SkillManage/TicketManage/TokenUsage/
│           │                            # ToolManage/UserManage — 6 个管理页
│           ├── agent/                   # AgentList/AgentPlaza/AgentCreate — 智能体生命周期
│           ├── chat/                    # DirectChatView — 实时流式对话
│           ├── dashboard/               # DashboardView — 系统概览
│           ├── group/                   # GroupListView/GroupChatView — 群聊社交
│           ├── history/                 # HistoryView — 历史记录
│           ├── image/                   # ImageGenerationView/GalleryView — 图片生成+画廊
│           ├── knowledge/               # KnowledgeBase — 知识库管理
│           ├── log/                     # LogViewer — 系统操作审计日志
│           ├── login/                   # 登录
│           ├── message/                 # MessageListView/MessageChatView — 私信系统
│           ├── register/                # 注册
│           ├── settings/                # SettingsView — 个人设置
│           └── ticket/                  # MyTickets — 我的工单
├── EcomAgentsFront/                     # ← 旧版 Vue 3 + Naive UI (弃用中)
├── docs/
│   ├── adr/                             # 架构决策记录 (5 个 ADR)
│   ├── architecture/                    # 架构讨论文档
│   ├── image/                           # 系统截图
│   ├── prd/                             # 产品需求文档
│   └── superpowers/plans/               # 功能规划
├── back/                                # SQL 数据备份 (24 表)
├── io/                                  # AgentScope 运行时工件
├── cli.bat                              # Claude CLI 代理隧道启动器
├── CONTEXT.md                           # 领域模型与架构文档
├── REASONIX.md                          # Reasonix 项目记忆
└── README.md / README_EN.md             # 项目文档
```

## 快速开始

### 前置要求

- Java 17+
- Node.js 18+
- PostgreSQL 14+
- Git（技能导入需要）

### 后端启动

```bash
cd EcomAgents

# 配置数据库连接 (src/main/resources/application.properties)
# 默认使用 PostgreSQL localhost:5432/ecomagents

# 运行测试
./gradlew test

# 启动开发服务器 (端口 8888)
./gradlew bootRun
```

### 前端启动

```bash
cd ShadcnAgentUI

# 安装依赖（推荐 pnpm，也可用 npm）
pnpm install
# npm install

# 运行测试
pnpm test
# npm test

# 启动开发服务器（端口 5174，/v1 和 /chat 代理到后端 8888）
pnpm dev
# npm run dev

# 生产构建
pnpm build
# npm run build
```

### 默认管理员账号

- 用户名: `admin`
- 密码: `123456`

### 技能导入

支持从 GitHub 仓库导入技能（git clone）：

```
https://github.com/{owner}/{repo}                              # 全量导入
https://github.com/{owner}/{repo}/tree/main/skills/{name}      # 导入单个技能
```

同时支持 ZIP 文件上传导入技能。导入后的技能存储在全局技能池中，可在创建/编辑 Agent 时按需绑定。

## API 接口

前端开发服务器将 `/v1/*` 和 `/chat/*` 请求代理到 `http://localhost:8888`。

### 认证与用户

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/login` | 登录 |
| POST | `/v1/register` | 注册（需邀请码） |
| GET | `/v1/users` | 用户列表（管理员） |
| PATCH | `/v1/users/{id}/status` | 切换用户状态（管理员） |
| GET | `/v1/invite-codes` | 邀请码列表（管理员） |
| POST | `/v1/invite-codes` | 生成邀请码（管理员） |

### 智能体

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/agents` | 智能体列表（`?scope=my\|plaza`） |
| POST | `/v1/agents` | 创建智能体 |
| GET | `/v1/agents/{id}` | 智能体详情 |
| PUT | `/v1/agents/{id}` | 更新智能体 |
| DELETE | `/v1/agents/{id}` | 删除智能体 |
| GET | `/v1/agents/{id}/skills` | 获取 Agent 已绑定技能 |
| POST | `/v1/agents/{id}/skills` | 绑定技能到 Agent |
| DELETE | `/v1/agents/{id}/skills/{name}` | 解绑 Agent 技能 |

### 对话与消息

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/chat/{agentId}/stream` | SSE 流式对话 |
| GET | `/v1/sessions` | 会话列表（含文件夹树） |
| POST | `/v1/sessions` | 创建会话 |
| GET | `/v1/sessions/{id}` | 会话详情 |
| DELETE | `/v1/sessions/{id}` | 删除会话 |
| GET | `/v1/session-folders` | 会话文件夹列表 |
| POST | `/v1/session-folders` | 创建会话文件夹 |
| PUT | `/v1/session-folders/{id}` | 更新会话文件夹 |
| DELETE | `/v1/session-folders/{id}` | 删除会话文件夹 |

### 模型管理（管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/models` | 模型列表 |
| POST | `/v1/models` | 创建模型 |
| PUT | `/v1/models/{id}` | 更新模型 |
| DELETE | `/v1/models/{id}` | 删除模型 |

### 工具管理（管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/tools` | 工具列表 |
| PUT | `/v1/tools/{id}` | 更新工具 |
| PATCH | `/v1/tools/{id}/toggle` | 切换工具启用状态 |
| POST | `/v1/tools/{id}/config` | 保存工具配置 |

### 技能管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/skills` | 技能列表 |
| POST | `/v1/skills/import-url` | 从 GitHub URL 导入技能 |
| POST | `/v1/skills/upload` | 上传技能 ZIP 文件 |
| DELETE | `/v1/skills/{name}?force=false` | 删除技能 |

### 知识库

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/knowledge-bases` | 知识库列表 |
| POST | `/v1/knowledge-bases` | 创建知识库（管理员） |
| DELETE | `/v1/knowledge-bases/{id}` | 删除知识库（管理员） |
| GET | `/v1/knowledge-bases/{kbId}/audit-logs` | 知识库审计日志 |
| POST | `/v1/knowledge-bases/{kbId}/documents` | 上传文档 |
| PUT | `/v1/knowledge-bases/{kbId}/documents/{docId}` | 更新文档内容 |
| DELETE | `/v1/knowledge-bases/{kbId}/documents/{docId}` | 删除文档 |

### 图片生成与画廊

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/image/generate` | 请求图片生成 |
| GET | `/v1/image/history` | 图片生成历史 |
| GET | `/v1/gallery/items` | 画廊作品列表 |
| POST | `/v1/gallery/items` | 发布作品到画廊 |
| DELETE | `/v1/gallery/items/{id}` | 删除画廊作品 |
| POST | `/v1/gallery/items/{id}/like` | 点赞/取消点赞 |

### 群聊

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/groups` | 我的群组列表 |
| POST | `/v1/groups` | 创建群组 |
| PUT | `/v1/groups/{id}` | 更新群组 |
| DELETE | `/v1/groups/{id}` | 解散群组 |
| GET | `/v1/groups/{id}/members` | 群成员列表 |
| POST | `/v1/groups/{id}/members` | 添加成员 |
| DELETE | `/v1/groups/{id}/members/{userId}` | 移除成员 |
| GET | `/v1/groups/{id}/messages` | 群消息历史 |
| POST | `/v1/groups/{id}/messages` | 发送群消息 |
| GET | `/v1/groups/{id}/files` | 群文件列表 |
| POST | `/v1/groups/{id}/files` | 上传群文件 |
| DELETE | `/v1/groups/{id}/files/{fileId}` | 删除群文件 |
| GET | `/v1/groups/{id}/agents` | 群 AI Agent 配置 |

### 私信

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/private-messages/contacts` | 联系人列表 |
| GET | `/v1/private-messages/{userId}` | 私信历史 |
| POST | `/v1/private-messages/{userId}` | 发送私信 |
| POST | `/v1/private-messages/upload` | 上传私信文件 |

### 工单系统

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/tickets` | 工单列表 |
| POST | `/v1/tickets` | 创建工单（用户） |
| PUT | `/v1/tickets/{id}` | 更新工单 |
| GET | `/v1/tickets/{id}` | 工单详情 |
| POST | `/v1/tickets/{id}/handle` | 处理工单（管理员） |

### 系统管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/system-logs` | 系统操作日志（管理员） |
| GET | `/v1/token-usage` | Token 用量统计 |
| GET | `/v1/files/upload` | 文件上传 |
| GET | `/v1/emoji` | Emoji 列表 |

## 多租户架构

平台支持多用户使用同一 Agent，数据隔离策略如下：

| 数据层 | 隔离策略 | 实现方式 |
|--------|---------|---------|
| Agent 人格（AGENTS.md） | Per-agent 独立 | 每个 Agent 有独立 workspace：`EcomAgents/workspace/agent-{id}/` |
| 会话元数据 | Per-user 隔离 | DB `sessions` 表记录 userId |
| 会话消息 | Per-user 隔离 | DB `session_messages` 表通过 FK 关联 session |
| 会话文件（JSONL） | Per-user 可追溯 | sessionId 格式：`sess-{agentId}-{userId}-{uuid}` |
| 长期记忆（MEMORY.md） | **Per-agent 共享** | AgentScope SDK 限制，所有用户共享同一份 MEMORY.md |

> 长期记忆共享是已知的框架限制（`MemoryFlushHook` 写死路径到 workspace 根目录），
> 与会话级别数据隔离无关。详见 [CONTEXT.md](./CONTEXT.md)。

## 项目约定

- **后端**: RESTful 控制器 `/v1/*`，JPA 实体 + Lombok，Service 层业务逻辑，全局异常处理
- **前端**: Composition API `<script setup>` 风格，shadcn-vue v2 组件，Pinia composition store，类型严格模式（`noUnusedLocals` + `noUnusedParameters`）
- **权限控制**: Vue Router `beforeEach` + 后端 `JwtAuthenticationFilter` 双重控制，管理员/普通用户角色分离
- **Agent 所有权**: 用户只能修改自己创建的 Agent，可通过 "Agent 广场" 使用他人的 Agent
- **技能系统**: 全局技能池（文件系统 `workspace/skills/{name}/SKILL.md`，YAML frontmatter），创建 Agent 时复制绑定到工作空间
- **知识库**: 管理员创建/删除，普通用户管理文档，PgVector 向量化检索，全程操作审计
- **国际化**: 基于 Vue I18n 的运行时切换，支持中文/英文，通过 Pinia store 持久化语言偏好
- **代码索引**: 项目配置了 [CodeGraph](https://github.com/getcodegraph/codegraph) MCP 服务，提供符号查询、调用链分析和影响范围评估

## 许可证

MIT
