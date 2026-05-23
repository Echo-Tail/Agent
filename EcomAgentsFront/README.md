# EcomAgents — Enterprise E-commerce Agent Management Platform

A full-featured SPA for managing AI agents in an enterprise e-commerce context. Built with Vue 3, Naive UI, and TypeScript.

## Tech Stack

- **Framework**: Vue 3 (Composition API + `<script setup>`)
- **UI Library**: Naive UI (tree-shaken components)
- **Language**: TypeScript
- **Build Tool**: Vite
- **Routing**: Vue Router (named routes, lazy-loaded views, auth guard)
- **State Management**: Pinia (composition stores)
- **HTTP Client**: Axios (token injection + 401 redirect interceptors)
- **Testing**: Vitest + happy-dom + @vue/test-utils (167 tests across 17 files)

## Project Structure

```
src/
├── api/                      # Axios API layer (14 modules)
│   ├── request.ts            # Axios instance with interceptors
│   ├── auth.ts               # Login/register APIs
│   ├── agent.ts              # Agent CRUD + scope APIs
│   ├── session.ts            # Session CRUD + SSE streaming
│   ├── user.ts               # User management APIs
│   ├── invite.ts             # Invite code APIs
│   ├── model.ts              # AI model CRUD APIs
│   ├── tool.ts               # Tool management APIs
│   ├── skill.ts              # Skill management APIs
│   ├── knowledge.ts          # Knowledge base CRUD + upload
│   ├── systemLog.ts          # System log query APIs
│   ├── ticket.ts             # Ticket management APIs
│   ├── token-usage.ts        # Token usage stats APIs
│   └── file.ts               # File upload/download APIs
├── components/               # Reusable components
│   ├── AgentCard.vue         # Agent card with icon, tags, status
│   ├── MessageBubble.vue     # Chat message bubble
│   ├── AgentSelector.vue     # Agent selection modal
│   └── DocPreview.vue        # Document preview modal
├── constants/
│   └── index.ts              # Storage keys, API base, validation limits
├── layouts/
│   ├── DefaultLayout.vue     # Sidebar + header + main content
│   └── BlankLayout.vue       # Full-screen layout (login/register)
├── router/
│   └── index.ts              # Named routes, lazy-loaded, auth guard
├── stores/                   # Pinia stores
│   ├── auth.ts               # Auth state, login/register/logout
│   ├── theme.ts              # Dark/light theme toggle
│   ├── agent.ts              # Agent list, CRUD, summary stats
│   ├── chat.ts               # Sessions, folders, streaming state
│   └── knowledge.ts          # KBs, documents, search
├── test/                     # Test files
│   ├── components/
│   ├── stores/
│   └── utils/
├── types/                    # TypeScript definitions (6 modules)
│   ├── api.ts                # ApiResponse, DTOs
│   ├── agent.ts              # Agent, AgentSummary, create/update requests
│   ├── session.ts            # Session, SseEvent
│   ├── knowledge.ts          # KnowledgeBase, KnowledgeDocument
│   ├── ticket.ts             # Ticket, TicketAttachment
│   └── enums.ts              # AgentStatus, roles
├── utils/
│   └── validation.ts         # Form validation rules
├── views/                    # Route-level page components (19 views)
│   ├── login/
│   ├── register/
│   ├── dashboard/
│   ├── agent/
│   │   ├── AgentList.vue     # My Agents (own scope)
│   │   ├── AgentPlaza.vue    # Agent Plaza (others' agents)
│   │   └── AgentCreate.vue   # Create/edit with tools, skills, KB, RAG mode
│   ├── chat/
│   ├── history/
│   ├── admin/
│   │   ├── UserManage.vue
│   │   ├── ModelManage.vue
│   │   ├── ToolManage.vue
│   │   ├── SkillManage.vue
│   │   ├── TicketManage.vue
│   │   └── TokenUsage.vue
│   ├── knowledge/
│   ├── ticket/
│   ├── log/
│   └── settings/
├── App.vue                   # Root component (Naive UI providers)
└── main.ts                   # App entry point
```

## Getting Started

### Prerequisites

- Node.js >= 18
- npm

### Install

```bash
npm install
```

### Development

Start the Vite dev server (hot-reload on port 5173, proxies `/v1/*` and `/chat/*` to backend port 8888):

```bash
npm run dev
```

### Build

Type-check and create a production build:

```bash
npm run build
```

### Preview

Preview the production build locally:

```bash
npm run preview
```

### Test

Run all tests once:

```bash
npm test
```

Watch mode:

```bash
npm run test:watch
```

## Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start Vite dev server (port 5173) |
| `npm run build` | Type-check + production build |
| `npm run preview` | Preview production build |
| `npm test` | Run Vitest tests (167 tests) |
| `npm run test:watch` | Vitest watch mode |

## Architecture

### Frontend-Backend Communication

Two communication channels:

- **Custom REST APIs** (`/v1/*`) — CRUD for agents, users, knowledge bases, models, tools, skills, tickets
- **Real-time chat** (`/chat/:agentId/stream`) — SSE streaming for agent conversations

### Auth Flow

1. Login/Register via Pinia auth store → API calls → token stored in `localStorage`
2. Vue Router `beforeEach` guard redirects unauthenticated users to `/login`
3. Axios request interceptor injects `Authorization: Bearer` header
4. Axios response interceptor catches 401 → clears token → redirects to `/login`

### Key Features

- **Agent Ownership**: Agents are scoped per-user. "My Agents" shows only agents created by the current user. "Agent Plaza" lists agents created by others, available for conversation but not editable.
- **Per-Agent Binding**: Each agent has its own set of tools, skills (copied from global pool), knowledge bases, and RAG mode.
- **Knowledge Base**: Admin-managed knowledge bases with document upload (TXT, MD, JSON), RAG-powered search, PgVector embeddings, and audit logging.
- **Skill Management**: File-system-based skills with GitHub URL import (git clone) and ZIP upload. Skills are stored in the global pool and can be bound to individual agents.

### Testing

167 tests across 17 files covering:

- **Store tests**: Auth, agent, chat, knowledge, theme stores
- **Component tests**: AgentCard interactions (edit, delete, navigation)
- **View tests**: Login, Register, Dashboard, AgentList rendering and state
- **Utility tests**: Form validation rules (username, password, email, invite code)
- **API tests**: Agent scope queries, session management

Test runner: Vitest with happy-dom DOM environment.

## Backend

The corresponding Spring Boot backend lives in `../EcomAgents/` (Gradle, Java 17, port 8888), providing JPA entities, REST controllers, and business logic for all domain models.
