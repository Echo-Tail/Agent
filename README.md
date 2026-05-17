# EcomAgents — 企业电商智能体管理平台

Enterprise E-commerce AI Agent Management Platform

A full-stack platform for managing, configuring, and interacting with AI agents in an e-commerce context. Built with AgentScope Java SDK for agent orchestration, featuring a Spring Boot backend and Vue 3 SPA frontend.

## Architecture

```
Agent/
├── EcomAgents/          # Spring Boot backend (Java 17, Gradle)
│   └── src/main/java/cafe/snails/ecomagents/
│       ├── config/       # CORS, security, data initializer, workspace config
│       ├── controller/   # REST controllers (Auth, Agent, Session, Model, Tool, Skill, Knowledge, User)
│       ├── dto/          # Request/response DTOs
│       ├── harness/      # AgentScope harness configuration
│       ├── model/        # JPA entities (Agent, AiModel, Session, User, ToolConfig, SkillIndex, etc.)
│       ├── repository/   # Spring Data JPA repositories
│       ├── security/     # JWT authentication filter + Spring Security config
│       ├── service/      # Business logic layer
│       └── tool/         # AgentScope @Tool annotated tools
├── EcomAgentsFront/      # Vue 3 SPA (TypeScript, Vite, Naive UI)
│   └── src/
│       ├── api/          # Axios HTTP client layer
│       ├── components/   # Reusable UI components
│       ├── constants/    # Shared constants (API, storage keys, limits)
│       ├── layouts/      # DefaultLayout (sidebar), BlankLayout (fullscreen)
│       ├── router/       # Vue Router with auth guard
│       ├── stores/       # Pinia state management (auth, theme, agent, chat, knowledge)
│       ├── types/        # TypeScript interfaces
│       ├── utils/        # Validation utilities
│       └── views/        # Route-level page components (12 routes)
│           ├── admin/    # User, Model, Tool, Skill management
│           ├── agent/    # Agent list and create/edit
│           ├── chat/     # Direct chat with SSE streaming
│           └── ...
```

## Tech Stack

### Backend (EcomAgents/)

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 4.0.6 (Web, JPA, Security, Validation) |
| Language | Java 17 |
| Database | PostgreSQL (production) / H2 (development) |
| ORM | Hibernate 7 with Spring Data JPA |
| Auth | JWT (jjwt 0.12.6) with Spring Security |
| Agent Framework | [AgentScope Java SDK](https://java.agentscope.io/) 1.1.0 |
| Build | Gradle |
| Testing | JUnit 5, Mockito |

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
| Testing | Vitest + happy-dom |
| Streaming | SSE via ReadableStream API |

## Features

- **Agent Management** — Create, edit, list AI agents with system prompts, model assignment, greeting messages, and tags
- **Model Management** — Admin-configured LLM backends (OpenAI, DeepSeek, Qwen, etc.) with per-model API keys, URLs, and parameters
- **Tool Management** — Enable/disable and configure agent tools (web search, image generation, etc.) with per-tool JSON config
- **Skill Management** — Filesystem-based skill system: import from [skills.sh](https://www.skills.sh) URLs or upload ZIP packages; skills are stored as SKILL.md files in `workspace/skills/` and shared globally across all agents
- **Knowledge Base** — Document upload (TXT, MD, JSON) with RAG-powered search
- **Chat** — Real-time SSE streaming conversations with agents
- **User System** — Registration via invite codes, admin/user roles, JWT authentication
- **Session Management** — Chat history with folder organization

## Getting Started

### Prerequisites

- Java 17+
- Node.js 18+
- PostgreSQL 14+ (or use H2 for development)
- npm / pnpm

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

# Start development server (port 8080, proxies /v1 to :8888)
npm run dev

# Production build
npm run build
```

### Default Admin Account

- Username: `admin`
- Password: `123456`

### API Endpoints

The frontend dev server proxies `/v1/*` requests to `http://localhost:8888`. All API routes are prefixed with `/v1`:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/login` | Login |
| POST | `/v1/register` | Register (requires invite code) |
| GET | `/v1/agents` | List agents |
| POST | `/v1/agents` | Create agent |
| GET | `/v1/agents/{id}` | Get agent details |
| PUT | `/v1/agents/{id}` | Update agent |
| DELETE | `/v1/agents/{id}` | Delete agent |
| GET | `/v1/models` | List AI models |
| POST | `/v1/models` | Create model (admin) |
| GET | `/v1/tools` | List tools |
| PUT | `/v1/tools/{id}` | Update tool config (admin) |
| GET | `/v1/skills` | List skills |
| POST | `/v1/skills/import-url` | Import skill from URL |
| POST | `/v1/skills/upload` | Upload skill ZIP |
| DELETE | `/v1/skills/{name}` | Delete skill |
| POST | `/v1/chat/{agentId}/stream` | SSE streaming chat |
| GET | `/v1/sessions` | List sessions (with folder tree) |
| POST | `/v1/sessions` | Create session |

## Project Conventions

- **Backend**: RESTful controllers at `/v1/*`, JPA entities with Lombok, service-layer business logic
- **Frontend**: Composition API `<script setup>` style, Naive UI components, Pinia composition stores
- **Admin pages**: Admin-only routes guarded by Vue Router `beforeEach` + backend security filter
- **Skills**: File-system based in `workspace/skills/{name}/SKILL.md` with YAML frontmatter (`description`, `category`)

## License

ISC
