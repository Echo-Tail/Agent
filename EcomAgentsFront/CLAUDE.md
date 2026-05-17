# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

企业电商智能体管理平台 (Enterprise E-commerce Agent Management Platform) — a frontend SPA for managing AI agents. **Vue 3 + Naive UI + TypeScript**.

## Commands

```bash
npm run dev           # Start Vite dev server (port 5173)
npm run build         # Type-check + production build (vue-tsc -b && vite build)
npm run preview       # Preview production build
npm test              # Run Vitest tests once
npm run test:watch    # Vitest in watch mode

# Backend (in EcomAgents/ directory):
cd ../EcomAgents && ./gradlew bootRun  # Start Spring Boot backend on port 8888
```

## Architecture

### Vue 3 SPA (src/)

```
src/
  constants/index.ts      — Centralized constants (storage keys, API base, validation limits)
  types/                   — TypeScript interfaces
    api.ts                 — ApiResponse<T>, LoginRequest, LoginResponse, UserDTO, InviteCode, AiModel
    agent.ts               — Agent, AgentSummary, AgentCreateRequest, AgentUpdateRequest
    session.ts             — Session, SessionSummary, SessionFolder, SseEvent
    knowledge.ts           — KnowledgeBase, KnowledgeDocument
    enums.ts               — Enums for AgentStatus, roles, etc.
  api/                     — Axios API layer (Base URL: /v1)
    request.ts             — Axios instance with token injection + 401 redirect interceptors
    auth.ts                — loginApi, registerApi
    agent.ts               — Agent CRUD APIs
    session.ts             — Sessions/folders CRUD + streamChat SSE client
    user.ts                — listUsersApi, toggleUserStatusApi
    invite.ts              — listInviteCodesApi, batchGenerateApi, deleteInviteCodeApi
    model.ts               — AiModel CRUD APIs
    knowledge.ts           — Knowledge bases/documents CRUD + search + upload
  stores/                  — Pinia stores
    auth.ts                — Token, currentUser, login/register/logout, isAdmin
    theme.ts               — Dark/light toggle, localStorage persistence
    agent.ts               — Agent list, loading/error, summary stats
    chat.ts                — Sessions, folders, folderTree, streaming state
    knowledge.ts           — KBs, documents, search, loading state
  views/                   — Route-level page components
    login/LoginView.vue
    register/RegisterView.vue
    dashboard/DashboardView.vue
    agent/AgentList.vue, AgentCreate.vue
    chat/ChatView.vue
    history/HistoryView.vue
    admin/UserManage.vue, ModelManage.vue
    knowledge/KnowledgeBase.vue
    settings/SettingsView.vue
  components/              — Reusable components
    AgentCard.vue          — Agent card with icon, tags, status, editable mode
    MessageBubble.vue      — Chat message bubble (user/assistant, avatar)
    AgentSelector.vue      — Agent selection modal for starting chat
    DocPreview.vue         — Knowledge document content preview modal
  layouts/
    DefaultLayout.vue      — Sidebar + header + main content area
    BlankLayout.vue        — Full-screen layout (login/register)
  router/index.ts          — Vue Router with 12 routes, beforeEach auth guard
  utils/validation.ts      — validate() function + username/password/email/inviteCode rules
```

### Auth Flow (Vue 3)

- **Login/Register**: Pinia auth store → API calls → token/user stored in localStorage
- **Auth guard**: Vue Router `beforeEach` → redirects to Login if no token
- **Token injection**: Axios request interceptor adds `Authorization: Bearer` header
- **401 handling**: Axios response interceptor clears token and redirects to Login
- **Admin check**: `authStore.isAdmin` (computed from `currentUser.role === 'admin'`)

### API Layer

All frontend API calls use Axios instance from `src/api/request.ts`:
- Base URL: `/v1` (proxied to Spring Boot backend on port 8888 during dev)
- Token injection from `localStorage` via interceptor
- 401 response → auto redirect to `/login`
- Response type: `ApiResponse<T>` with `{ code, message, data }`

### Key Patterns

- **Routing**: Vue Router with named routes, lazy-loaded views, `beforeEach` auth guard
- **State**: Pinia composition stores (setup function style)
- **Theming**: Naive UI `NConfigProvider` with dark/light theme, persists to localStorage
- **Testing**: Vitest + happy-dom, 74 tests across 7 files (validation, stores, components)
- **SSE streaming**: `fetch` + `ReadableStream.getReader()`, SSE `data:` events with JSON

### Known Issues

- Vanilla JS files in `js/` and `pages/` are legacy — kept for reference
