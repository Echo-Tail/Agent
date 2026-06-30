# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## User Preferences

- **语言**: 中文回复。与用户交流、代码注释、commit message 均使用中文。
- **代码索引**: 项目已配置 CodeGraph (`codegraph_*` MCP 工具)。所有符号查询、调用链分析、影响范围评估优先使用 CodeGraph，而非 grep/文件搜索。

## Project Overview

企业电商智能体管理平台 (Enterprise E-commerce Agent Management Platform). A three-module project:

- **`EcomAgents/`** — Java/Spring Boot backend (Gradle, Java 17, AgentScope Java SDK)
- **`EcomAgentsFront/`** — Vue 3 + Naive UI + TypeScript SPA (Vite, Pinia, Vue Router, Vitest)
- **`ShadcnAgentUI/`** — 新的 shadcn-vue 前端 (Vue 3 + shadcn-vue + TypeScript, 逐步替换 EcomAgentsFront)

## Commands

### Frontend (EcomAgentsFront/)

```bash
npm run dev           # Start Vite dev server (port 5173)
npm run build         # Type-check + production build (vue-tsc -b && vite build)
npm run preview       # Preview production build
npm test              # Run Vitest tests (161 tests, 16 files)
```

### Backend (EcomAgents/)

```bash
./gradlew bootRun           # Start Spring Boot backend (port 8888)
./gradlew test              # Run tests (JUnit 5)
./gradlew build             # Build the project
```

### Root

```bash
cli.bat <args>     # Launch Claude CLI with proxy tunnel (port 15236)
```

## Architecture

### Two-Module Layout

```
Agent/                        # Project root (no build of its own)
├── EcomAgents/               # Spring Boot backend (Gradle, Java 17, port 8888)
│   ├── build.gradle          # Web + JPA + H2 + AgentScope starter + Lombok
│   └── src/
│       ├── main/java/.../
│       │   ├── config/       # CORS config, DataInitializer (seed data)
│       │   ├── model/        # JPA entities: User, Agent, Session, SessionFolder, InviteCode, AiModel, ToolConfig
│       │   ├── repository/   # Spring Data JPA repositories
│       │   ├── service/      # Business logic layer
│       │   ├── controller/   # REST controllers at /v1/*
│       │   └── dto/          # ApiResponse, login/register, ToolDefinition
│       └── test/java/.../    # JUnit 5 + Mockito tests
├── EcomAgentsFront/          # Frontend SPA (Vue 3 + Naive UI + TS)
│   ├── src/
│   │   ├── constants/        # Centralized constants (storage keys, API base, limits)
│   │   ├── types/            # TypeScript interfaces (api, agent, session, knowledge)
│   │   ├── api/              # Axios API layer — auth, agent, session, model, tool, knowledge, user, invite, file
│   │   ├── stores/           # Pinia stores (auth, theme, agent, chat, knowledge)
│   │   ├── views/            # 13 route-level page components (+ agent/edit, admin/tools)
│   │   │   ├── admin/        # UserManage, ModelManage, ToolManage
│   │   │   ├── agent/        # AgentList, AgentCreate
│   │   │   ├── chat/         # DirectChatView (default conversation)
│   │   │   ├── history/      # HistoryView
│   │   │   └── ...           # Dashboard, KnowledgeBase, Login, Register, Settings, Logs
│   │   ├── components/       # Reusable: AgentCard, MessageBubble, AgentSelector, DocPreview
│   │   ├── layouts/          # DefaultLayout (sidebar), BlankLayout (fullscreen)
│   │   ├── router/           # Vue Router (14 routes, beforeEach auth guard)
│   │   └── utils/            # Validation utilities
│   └── vitest.config.ts      # Vitest + happy-dom environment
└── cli.bat                   # Proxy tunnel launcher
```

### API Convention

All frontend API calls use Axios instance from `src/api/request.ts`, base URL `/v1` (proxied to Spring Boot port 8888).

- **REST APIs** (`/v1/*`) — CRUD for agents, users, sessions, knowledge bases, models, tools
- **SSE streaming** — `POST /v1/chat/{agentId}/stream` for real-time agent chat

### Frontend Architecture (Vue 3)

- **Routing**: Vue Router with 14 named routes, lazy-loaded components, `beforeEach` auth guard (login redirect + admin-only routes for UserManage/ModelManage/ToolManage)
- **State**: Pinia composition stores (setup function style) for auth, theme, agent, chat, knowledge
- **API layer**: Axios with token injection interceptor + 401 redirect interceptor
- **SSE streaming**: Native `fetch` + `ReadableStream` with SSE `data:` event parsing
- **Theming**: Naive UI `NConfigProvider`, dark/light mode with localStorage persistence
- **Testing**: Vitest + happy-dom, 161 tests across 16 files

### Frontend Key Patterns

- **Component tests**: mount with Naive UI stubs (n-card, n-button, n-tag, etc.)
- **Store tests**: `setActivePinia(createPinia())` + `vi.mock()` for API modules
- **Admin pages**: Table (n-data-table) + Modal (n-modal) pattern — see UserManage/ModelManage/ToolManage
- **Auth**: Pinia auth store → Axios interceptor → Vue Router guard → login redirect

### Domain Model

- **Agent** — AI assistant instance with name, system prompt, tools, model, knowledge base. Each agent has an independent workspace at `EcomAgents/workspace/agent-{id}/`
- **Model** — LLM backend config (admin-managed list, selected per-agent; stored in `ai_models` table)
- **Tool** — Capability registered via AgentScope `@Tool` annotation (admin-managed enable/disable/config in `tool_configs` table). Tools have categories (web/media/browser/terminal_files/memory) and per-tool JSON config (API keys).
- **Session** — Chat history with messages, folder organization, SSE streaming. Session ID format: `sess-{agentId}-{userId}-{uuid}`
- **Knowledge Base** — Document collection for RAG (TXT, MD, JSON uploads)
- **Invite Code** — Registration codes with usage tracking

### Multi-Tenant Design
- **Agent personality (AGENTS.md)**: per-agent workspace isolation (`workspace/agent-{id}/`)
- **Session data**: per-user isolation via DB userId field + userId embedded in sessionId
- **Long-term memory (MEMORY.md)**: shared across all users of the same agent (AgentScope SDK limitation, accepted)

### Tool Management System

- **Backend**: `ToolConfig` JPA entity (id/name/description/category/enabled/configJson), DB-driven `ToolService`, admin API endpoints at `/v1/tools/*`
- **Frontend**: Admin-only `ToolManage.vue` at `/admin/tools` with table, enabled/disable toggle, per-tool config modal (API key for web_search/image_generation)
- **Agent integration**: `AgentCreate.vue` loads tool list from API, shows only enabled tools; tool IDs match backend (e.g. `web_search` not `web`)

### Legacy Files (Vanilla JS — kept for reference)

- `index.html`, `js/` (api.js, auth.js, main.js, etc.), `pages/`, `css/style.css`
- These are from the pre-migration Bootstrap 5 SPA and are NOT used by the Vue 3 app

<!-- superpowers-zh:begin (do not edit between these markers) -->
# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文 review 沟通参考——话术模板、分级标注（必须修复/建议修改/仅供参考）、国内团队常见反模式应对。仅在用户显式 /chinese-code-review 时调用，不要根据上下文自动触发。
- **chinese-commit-conventions**: 中文 commit 与 changelog 配置参考——Conventional Commits 中文适配、commitlint/husky/commitizen 中文模板、conventional-changelog 中文配置。仅在用户显式 /chinese-commit-conventions 时调用，不要根据上下文自动触发。
- **chinese-documentation**: 中文文档排版参考——中英文空格、全半角标点、术语保留、链接格式、中文文案排版指北约定。仅在用户显式 /chinese-documentation 时调用，不要根据上下文自动触发。
- **chinese-git-workflow**: 国内 Git 平台配置参考——Gitee、Coding.net、极狐 GitLab、CNB 的 SSH/HTTPS/凭据/CI 接入差异与镜像同步配置。仅在用户显式 /chinese-git-workflow 时调用，不要根据上下文自动触发。
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发，或在执行实现计划之前使用——通过原生工具或 git worktree 回退机制确保隔离工作区存在
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

如果你认为哪怕只有 1% 的可能性某个 skill 适用于你正在做的事情，你必须调用该 skill 检查。
<!-- superpowers-zh:end -->
