# REASONIX.md

## Stack

- **Backend**: Spring Boot 4.0.6 / Java 17 (Gradle), port 8888
- **Frontend (new)**: Vue 3 + TypeScript 6.0 + shadcn-vue 2.7 / Tailwind CSS 4 / Vite 8, port 5174
- **Frontend (legacy)**: EcomAgentsFront/ — Vue 3 + Naive UI, port 5173 (older, being replaced)
- **Key deps**: AgentScope Java SDK 1.1.0-RC1, Pinia, Vue Router, Axios, JWT (jjwt 0.12.6), PostgreSQL + H2 (dev)
- **Test**: Vitest + happy-dom (frontend), JUnit 5 + Mockito (backend)

## Layout

- `EcomAgents/` — Spring Boot backend (Java, Gradle). `src/main/java/cafe/snails/ecomagents/` with config/model/repository/service/controller/dto packages
- `ShadcnAgentUI/` — New shadcn-vue SPA. `src/api/` (Axios), `src/stores/` (Pinia), `src/views/` (pages), `src/components/ui/` (shadcn), `src/types/` (TS interfaces)
- `EcomAgentsFront/` — Legacy Vue 3 + Naive UI frontend (being replaced by ShadcnAgentUI)
- `back/` — SQL dumps for all 24 database tables
- `docs/` — ADRs (`docs/adr/`), architecture notes, PRDs
- `io/` — AgentScope runtime artifacts (core, harness)

## Commands

### Frontend (ShadcnAgentUI/)

```bash
npm run dev           # Vite dev server (port 5174)
npm run build         # vue-tsc -b && vite build
npm run preview       # vite preview
npm test              # vitest run
npm run test:watch    # vitest (watch mode)
```

### Backend (EcomAgents/)

```bash
./gradlew bootRun     # Spring Boot (port 8888)
./gradlew test        # JUnit 5
./gradlew build       # Full build
./gradlew bootRun    # (with JVM proxy args in build.gradle)
```

## Conventions

- **Type imports**: `import type { X } from '@/types/...'` for type-only imports (ubiquitous across ShadcnAgentUI/)
- **Path alias**: `@/` maps to `src/` in both frontends and Vitest config
- **Test placement**: Vitest tests in `ShadcnAgentUI/src/__tests__/` with `.test.ts` suffix; stubs/colocated pattern. Java tests mirror source package under `src/test/java/`
- **Type strictness**: `noUnusedLocals: true`, `noUnusedParameters: true` in tsconfig.app.json. No eslint/prettier — vue-tsc is the only linter
- **shadcn-vue components**: Barrel exports via `src/components/ui/<name>/index.ts` with `VariantProps` from class-variance-authority
- **API layer**: Axios instance from `src/api/request.ts`, base URL `/v1`, token injection interceptor + 401 redirect
- **Java controllers**: REST at `/v1/*`, JPA entities in `model/`, services in `service/`, DTOs in `dto/`

## Watch out for

- **`EcomAgentsFront/` is legacy** — do not add features there; all new work goes in `ShadcnAgentUI/`
- **No eslint/prettier** — type checking from `vue-tsc -b` is the only static analysis; `noUnusedLocals` will fail build
- **shadcn-vue v2** uses reka-ui under the hood, not Naive UI; component patterns differ from EcomAgentsFront/
- **Backend dev DB**: H2 file at `EcomAgents/data/ecomagents.mv.db` (not PostgreSQL in dev)
- **Per-agent workspaces** at `EcomAgents/workspace/agent-{id}/` — contains skills, knowledge, and AGENTS.md/MEMORY.md
- **Vite proxy** in `ShadcnAgentUI/vite.config.ts` forwards `/v1` and `/chat` to `localhost:8888`
