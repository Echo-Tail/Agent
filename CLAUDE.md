# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

企业电商智能体管理平台 (Enterprise E-commerce Agent Management Platform). A two-module project:

- **`EcomAgents/`** — Java/Spring Boot backend (Gradle, Java 17, AgentScope Java SDK)
- **`EcomAgentsFront/`** — Vue 3 + Naive UI + TypeScript SPA (Vite, Pinia, Vue Router, Vitest)

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

- **Agent** — AI assistant instance with name, system prompt, tools, model, knowledge base
- **Model** — LLM backend config (admin-managed list, selected per-agent; stored in `ai_models` table)
- **Tool** — Capability registered via AgentScope `@Tool` annotation (admin-managed enable/disable/config in `tool_configs` table). Tools have categories (web/media/browser/terminal_files/memory) and per-tool JSON config (API keys).
- **Session** — Chat history with messages, folder organization, SSE streaming
- **Knowledge Base** — Document collection for RAG (TXT, MD, JSON uploads)
- **Invite Code** — Registration codes with usage tracking

### Tool Management System

- **Backend**: `ToolConfig` JPA entity (id/name/description/category/enabled/configJson), DB-driven `ToolService`, admin API endpoints at `/v1/tools/*`
- **Frontend**: Admin-only `ToolManage.vue` at `/admin/tools` with table, enabled/disable toggle, per-tool config modal (API key for web_search/image_generation)
- **Agent integration**: `AgentCreate.vue` loads tool list from API, shows only enabled tools; tool IDs match backend (e.g. `web_search` not `web`)

### Legacy Files (Vanilla JS — kept for reference)

- `index.html`, `js/` (api.js, auth.js, main.js, etc.), `pages/`, `css/style.css`
- These are from the pre-migration Bootstrap 5 SPA and are NOT used by the Vue 3 app
