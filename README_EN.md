# EcomAgents — Enterprise E-commerce AI Agent Management Platform

[![Java 17](https://img.shields.io/badge/Java-17-blue?logo=openjdk&style=for-the-badge)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-brightgreen?logo=spring&style=for-the-badge)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue_3-4dba87?logo=vuedotjs&style=for-the-badge)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-blue?logo=typescript&style=for-the-badge)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-316192?logo=postgresql&style=for-the-badge)](https://www.postgresql.org/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&style=for-the-badge)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)]()

[![中文](https://img.shields.io/badge/中文文档-red?style=for-the-badge)](README.md)
[![English](https://img.shields.io/badge/English-lightgrey?style=for-the-badge)](README_EN.md)

---

![System Preview](docs/image/EcomAgents.png)

A full-stack platform for managing, configuring, and interacting with AI agents in an e-commerce context. Built with [AgentScope Java SDK](https://java.agentscope.io/) for agent orchestration, featuring a Spring Boot backend and Vue 3 SPA frontend.

## Features

- **Agent Management** — Create, edit, and manage AI agents with system prompts, model assignment, greeting messages, and tags
- **Ownership Isolation** — "My Agents" shows only agents created by the current user; "Agent Plaza" lists agents created by others (view and chat only, no editing)
- **Model Management** — Admin-configured LLM backends (OpenAI, DeepSeek, Qwen, etc.) with per-model API keys, URLs, and parameters
- **Tool Management** — Enable/disable and configure agent tools (web search, image generation, etc.) with per-tool JSON config, per-agent binding
- **Skill Management** — Filesystem-based skill system supporting GitHub URL import (git clone) and ZIP upload; skills stored in a global pool and copied per-agent on binding
- **Knowledge Base** — Admin-managed knowledge bases with document upload (TXT, MD, JSON), PgVector-powered RAG search, and full audit logging
- **RAG Modes** — Supports AGENTIC (agent decides when to retrieve) and GENERIC (auto-inject context before each message)
- **Chat** — Real-time SSE streaming conversations with agents
- **User System** — Registration via invite codes, admin/user roles, JWT authentication
- **Session Management** — Chat history with folder organization
- **Ticket System** — User-submitted tickets with admin processing workflow
- **Token Usage** — Track and view token consumption across models

## Tech Stack

### Backend (EcomAgents/)

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 4.0.6 (Web, JPA, Security, Validation) |
| Language | Java 17 |
| Database | PostgreSQL 14+ |
| ORM | Hibernate 7 + Spring Data JPA |
| Auth | JWT (jjwt 0.12.6) + Spring Security |
| Agent Framework | [AgentScope Java SDK](https://java.agentscope.io/) 1.1.0 |
| Vector Search | PgVector (pgvector) |
| Build | Gradle |
| Testing | JUnit 5, Mockito (120 tests, 14 test classes) |

### Frontend (EcomAgentsFront/)

| Component | Technology |
|-----------|-----------|
| Framework | Vue 3 (Composition API) |
| UI Library | Naive UI |
| Language | TypeScript |
| Build | Vite 6 |
| State Management | Pinia |
| Router | Vue Router 4 |
| HTTP Client | Axios |
| Testing | Vitest + happy-dom (167 tests, 17 files) |
| Streaming | SSE via ReadableStream API |

## Project Structure

```
Agent/
├── EcomAgents/                     # Spring Boot backend (Java 17, Gradle, port 8888)
│   └── src/main/java/cafe/snails/ecomagents/
│       ├── config/                 # CORS, security, data initializer, workspace/skill config
│       ├── controller/             # REST controllers (15: Auth/Agent/Session/Model/Tool/Skill/Knowledge/User/Ticket/Log etc.)
│       ├── dto/                    # Request/response DTOs
│       ├── harness/                # AgentScope HarnessAgent integration layer
│       ├── model/                  # JPA entities (25+: Agent, Skills, AgentSkill, AiModel, Session, User, ToolConfig, etc.)
│       ├── repository/             # Spring Data JPA repositories
│       ├── security/               # JWT auth filter + Spring Security config
│       ├── service/                # Business logic (19 services: HarnessAgentManager, VectorEmbeddingService, etc.)
│       └── tool/                   # AgentScope @Tool annotated tools
├── EcomAgentsFront/                # Vue 3 SPA (TypeScript, Vite, Naive UI, port 5173)
│   └── src/
│       ├── api/                    # Axios HTTP client layer (14 modules)
│       ├── components/             # Reusable UI components
│       ├── constants/              # Shared constants (API base, storage keys, validation limits)
│       ├── layouts/                # Layout components (DefaultLayout sidebar, BlankLayout fullscreen)
│       ├── router/                 # Vue Router with auth guard
│       ├── stores/                 # Pinia state management (auth/theme/agent/chat/knowledge)
│       ├── types/                  # TypeScript interfaces
│       ├── utils/                  # Validation utilities
│       └── views/                  # Route-level page components (19 views)
│           ├── admin/              # User, Model, Tool, Skill, Ticket, Token Usage management
│           ├── agent/              # My Agents / Agent Plaza / Create & Edit
│           ├── chat/               # Real-time streaming chat
│           ├── ticket/             # My Tickets
│           ├── log/                # System Logs
│           └── ...                 # Dashboard, Login, Register, Settings
├── cli.bat                         # Claude CLI proxy tunnel launcher
├── docs/image/                     # System screenshots
├── CONTEXT.md                      # Domain model & architecture documentation
└── README.md / README_EN.md        # Project documentation
```

## Quick Start

### Prerequisites

- Java 17+
- Node.js 18+
- PostgreSQL 14+
- Git (required for skill import)

### Backend Setup

```bash
cd EcomAgents

# Configure database in src/main/resources/application.properties
# Default: PostgreSQL at localhost:5432/ecomagents

# Run tests
./gradlew test

# Start development server (port 8888)
./gradlew bootRun
```

### Frontend Setup

```bash
cd EcomAgentsFront

# Install dependencies
npm install

# Run tests
npm test

# Start development server (port 5173, proxies /v1 to :8888)
npm run dev

# Production build
npm run build
```

### Default Admin Account

- Username: `admin`
- Password: `123456`

### Skill Import

Import skills directly from GitHub repositories (git clone):

```
https://github.com/{owner}/{repo}                              # Full scan
https://github.com/{owner}/{repo}/tree/main/skills/{name}      # Single skill
```

ZIP file upload is also supported. Skills are stored in a global pool and can be bound per-agent during creation or editing.

## API Endpoints

The frontend dev server proxies `/v1/*` requests to `http://localhost:8888`. All API routes are prefixed with `/v1`:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/login` | Login |
| POST | `/v1/register` | Register (requires invite code) |
| GET | `/v1/agents` | List agents (supports `?scope=my\|plaza`) |
| POST | `/v1/agents` | Create agent |
| GET | `/v1/agents/{id}` | Get agent details |
| PUT | `/v1/agents/{id}` | Update agent |
| DELETE | `/v1/agents/{id}` | Delete agent |
| GET | `/v1/agents/{id}/skills` | Get agent bound skills |
| GET | `/v1/models` | List AI models |
| POST | `/v1/models` | Create model (admin) |
| GET | `/v1/tools` | List tools |
| PUT | `/v1/tools/{id}` | Update tool (admin) |
| PATCH | `/v1/tools/{id}/toggle` | Toggle tool enabled (admin) |
| POST | `/v1/tools/{id}/config` | Save tool config (admin) |
| GET | `/v1/skills` | List skills |
| POST | `/v1/skills/import-url` | Import skill from URL |
| POST | `/v1/skills/upload` | Upload skill ZIP |
| DELETE | `/v1/skills/{name}?force=false` | Delete skill |
| GET | `/v1/knowledge-bases` | List knowledge bases |
| POST | `/v1/knowledge-bases` | Create knowledge base (admin) |
| DELETE | `/v1/knowledge-bases/{id}` | Delete knowledge base (admin) |
| GET | `/v1/knowledge-bases/{kbId}/audit-logs` | Knowledge base audit logs |
| POST | `/v1/knowledge-bases/{kbId}/documents` | Upload document |
| DELETE | `/v1/knowledge-bases/{kbId}/documents/{docId}` | Delete document |
| POST | `/v1/chat/{agentId}/stream` | SSE streaming chat |
| GET | `/v1/sessions` | List sessions (with folder tree) |
| POST | `/v1/sessions` | Create session |
| GET | `/v1/users` | List users (admin) |
| PATCH | `/v1/users/{id}/status` | Toggle user status (admin) |
| GET | `/v1/system-logs` | System logs (admin) |
| GET | `/v1/tickets` | List tickets |
| POST | `/v1/tickets` | Create ticket |
| GET | `/v1/token-usage` | Token usage statistics |

## Multi-Tenant Architecture

The platform supports multiple users interacting with the same agent. Data isolation strategy:

| Layer | Isolation | Implementation |
|-------|-----------|---------------|
| Agent personality (AGENTS.md) | Per-agent | Each agent has independent workspace: `EcomAgents/workspace/agent-{id}/` |
| Session metadata | Per-user | DB `sessions` table records userId |
| Session messages | Per-user | DB `session_messages` table linked via session FK |
| Session files (JSONL) | Per-user traceable | Session ID format: `sess-{agentId}-{userId}-{uuid}` |
| Long-term memory (MEMORY.md) | **Shared per-agent** | AgentScope SDK limitation — all users share the same MEMORY.md |

> Long-term memory sharing is a known framework limitation (`MemoryFlushHook` hardcodes
> the path to workspace root). Session-level data is strictly user-isolated.
> See [CONTEXT.md](./CONTEXT.md) for details.

## Project Conventions

- **Backend**: RESTful controllers at `/v1/*`, JPA entities with Lombok, service-layer business logic
- **Frontend**: Composition API `<script setup>` style, Naive UI components, Pinia composition stores
- **Access Control**: Dual guard via Vue Router `beforeEach` + backend security filter; admin/user role separation
- **Agent Ownership**: Users can only modify agents they created; "Agent Plaza" allows using others' agents for conversation
- **Skills**: Global skill pool stored in `workspace/skills/{name}/SKILL.md` with YAML frontmatter (`name`, `description`, `category`); copied per-agent on binding
- **Knowledge Base**: Admin-managed lifecycle with PgVector vector search and full operation audit trail

## License

MIT
