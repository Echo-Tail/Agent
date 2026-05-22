# Repository Guidelines

## Project Structure & Module Organization

This repository contains a full-stack EcomAgents application:

- `EcomAgents/` is the Spring Boot backend. Java source lives in `src/main/java/cafe/snails/ecomagents/`, grouped by `controller`, `service`, `repository`, `model`, `dto`, `security`, `config`, `harness`, and `tool`.
- `EcomAgents/src/test/java/` contains backend JUnit tests mirroring service and controller packages.
- `EcomAgentsFront/` is the Vue 3 + TypeScript frontend. Main source lives in `src/`, with `api`, `components`, `layouts`, `router`, `stores`, `types`, `utils`, and route-level `views`.
- `EcomAgentsFront/src/test/` contains Vitest tests.
- `docs/` contains ADRs, implementation plans, and screenshots/assets.

## Build, Test, and Development Commands

Backend commands run from `EcomAgents/`:

- `./gradlew bootRun` starts the Spring Boot API on port `8888`.
- `./gradlew test` runs the JUnit 5 backend test suite.
- `./gradlew build` compiles, tests, and packages the backend.

Frontend commands run from `EcomAgentsFront/`:

- `npm install` installs frontend dependencies.
- `npm run dev` starts Vite on port `5173` with API proxying to the backend.
- `npm test` runs Vitest once.
- `npm run test:watch` runs Vitest in watch mode.
- `npm run build` type-checks with `vue-tsc` and builds production assets.

## Coding Style & Naming Conventions

Use Java 17 for backend code. Keep Spring classes in existing package layers: controllers expose REST endpoints, services hold business logic, repositories wrap persistence, and DTOs define API shapes. Name tests after the class or workflow under test, for example `AgentServiceTest`.

Frontend code uses Vue 3 Composition API, TypeScript, Pinia, Vue Router, Axios, and Naive UI. Use PascalCase for Vue components such as `AgentCard.vue` and camelCase for functions and variables.

## Testing Guidelines

Add or update tests with behavior changes. Backend tests use Spring Boot Test, JUnit 5, and Spring Security test utilities. Frontend tests use Vitest, happy-dom, and `@vue/test-utils`. Prefer focused tests near the affected package, and run relevant suites before opening a PR.

## Commit & Pull Request Guidelines

Recent history uses Conventional Commit-style prefixes, especially `fix:` and `feat:`. Keep commit messages imperative and scoped to one change, for example `fix: validate disabled models before agent chat`.

Pull requests should include a concise summary, tests run, linked issues when applicable, and screenshots for UI changes. Call out configuration, migration, or security-sensitive changes explicitly.

## Security & Configuration Tips

Do not commit API keys, JWT secrets, database credentials, uploaded documents, or generated logs. Backend configuration lives in `EcomAgents/src/main/resources/application*.properties`; keep local overrides out of version control when they contain secrets.
