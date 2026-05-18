# EcomAgents — 企业电商智能体管理平台

[![Java 17](https://img.shields.io/badge/Java-17-blue?logo=openjdk&style=for-the-badge)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-brightgreen?logo=spring&style=for-the-badge)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue_3-4dba87?logo=vuedotjs&style=for-the-badge)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-blue?logo=typescript&style=for-the-badge)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-316192?logo=postgresql&style=for-the-badge)](https://www.postgresql.org/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&style=for-the-badge)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-ISC-lightgrey?style=for-the-badge)]()

[![中文](https://img.shields.io/badge/语言-中文-red?style=for-the-badge)](README.md)
[![English](https://img.shields.io/badge/Language-English-lightgrey?style=for-the-badge)](README_EN.md)

📖 **English version available** → [README_EN.md](README_EN.md)

---

![系统概览](docs/image/EcomAgents.png)

一站式企业级 AI 智能体管理平台，基于 [AgentScope Java SDK](https://java.agentscope.io/) 构建。提供智能体生命周期管理、对话交互、工具集成、知识库管理等功能，适用于电商场景下的 AI 自动化运营。

## 功能特性

- **智能体管理** — 创建、编辑、管理 AI 智能体，支持角色设定（System Prompt）、模型分配、欢迎语和标签
- **模型管理** — 管理员可配置多种 LLM 后端（OpenAI、DeepSeek、Qwen 等），支持独立 API Key、接口地址和模型参数
- **工具管理** — 按需启停工具（网页搜索、图片生成等），支持 JSON 配置
- **技能管理** — 基于文件系统的技能体系，支持 GitHub URL 导入（git clone）和 ZIP 上传，全局共享
- **知识库** — 文档上传（TXT、MD、JSON）+ RAG 向量检索
- **对话系统** — SSE 实时流式对话，支持消息历史、文件夹分组
- **用户系统** — 邀请码注册、管理员/普通用户角色、JWT 认证

## 技术栈

### 后端 (EcomAgents/)

| 组件 | 技术 |
|------|------|
| 框架 | Spring Boot 4.0.6 (Web, JPA, Security, Validation) |
| 语言 | Java 17 |
| 数据库 | PostgreSQL 14+（生产）/ H2（开发） |
| ORM | Hibernate 7 + Spring Data JPA |
| 认证 | JWT (jjwt 0.12.6) + Spring Security |
| 智能体框架 | [AgentScope Java SDK](https://java.agentscope.io/) 1.1.0 |
| 构建 | Gradle |
| 测试 | JUnit 5, Mockito |

### 前端 (EcomAgentsFront/)

| 组件 | 技术 |
|------|------|
| 框架 | Vue 3 (Composition API) |
| UI 库 | Naive UI |
| 语言 | TypeScript |
| 构建 | Vite 6 |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| HTTP 客户端 | Axios |
| 测试 | Vitest + happy-dom |
| 流式通信 | SSE via ReadableStream API |

## 项目结构

```
Agent/
├── EcomAgents/                     # Spring Boot 后端 (Java 17, Gradle, port 8888)
│   └── src/main/java/cafe/snails/ecomagents/
│       ├── config/                 # CORS、安全、数据初始化、Workspace/Skill 配置
│       ├── controller/             # REST 控制器 (Auth/Agent/Session/Model/Tool/Skill/Knowledge/User)
│       ├── dto/                    # 请求/响应 DTO
│       ├── harness/                # AgentScope HarnessAgent 集成层
│       ├── model/                  # JPA 实体 (Agent, AiModel, Session, User, ToolConfig, SkillIndex 等)
│       ├── repository/             # Spring Data JPA 仓储
│       ├── security/               # JWT 认证过滤器 + Spring Security 配置
│       ├── service/                # 业务逻辑层 (含 AgentScope 工具注册)
│       └── tool/                   # AgentScope @Tool 注解工具
├── EcomAgentsFront/                # Vue 3 前端 SPA (TypeScript, Vite, Naive UI, port 5173)
│   └── src/
│       ├── api/                    # Axios HTTP 客户端层
│       ├── components/             # 可复用 UI 组件
│       ├── constants/              # 共享常量 (API 地址、存储键、验证限制)
│       ├── layouts/                # 布局组件 (DefaultLayout 侧边栏, BlankLayout 全屏)
│       ├── router/                 # Vue Router + 导航守卫
│       ├── stores/                 # Pinia 状态管理 (auth/theme/agent/chat/knowledge)
│       ├── types/                  # TypeScript 接口定义
│       ├── utils/                  # 验证工具函数
│       └── views/                  # 路由页面组件
│           ├── admin/              # 用户/模型/工具/技能管理
│           ├── agent/              # 智能体列表与创建
│           ├── chat/               # 实时对话
│           └── ...                 # Dashboard, Login, Register, Settings 等
├── cli.bat                         # Claude CLI 代理隧道启动器
├── docs/image/                     # 系统截图
├── CONTEXT.md                      # 领域模型与架构文档
└── README.md / README_EN.md        # 项目文档
```

## 快速开始

### 前置要求

- Java 17+
- Node.js 18+
- PostgreSQL 14+（开发环境可使用 H2）
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
cd EcomAgentsFront

# 安装依赖
npm install

# 运行测试
npm test

# 启动开发服务器 (端口 5173, /v1 代理到 :8888)
npm run dev

# 生产构建
npm run build
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

同时支持 ZIP 文件上传导入技能。

## API 接口

前端开发服务器将 `/v1/*` 请求代理到 `http://localhost:8888`。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/login` | 登录 |
| POST | `/v1/register` | 注册（需邀请码） |
| GET | `/v1/agents` | 智能体列表 |
| POST | `/v1/agents` | 创建智能体 |
| GET | `/v1/agents/{id}` | 智能体详情 |
| PUT | `/v1/agents/{id}` | 更新智能体 |
| DELETE | `/v1/agents/{id}` | 删除智能体 |
| GET | `/v1/models` | 模型列表 |
| POST | `/v1/models` | 创建模型（管理员） |
| GET | `/v1/tools` | 工具列表 |
| PUT | `/v1/tools/{id}` | 更新工具配置（管理员） |
| GET | `/v1/skills` | 技能列表 |
| POST | `/v1/skills/import-url` | 从 URL 导入技能 |
| POST | `/v1/skills/upload` | 上传技能 ZIP |
| DELETE | `/v1/skills/{name}` | 删除技能 |
| POST | `/v1/chat/{agentId}/stream` | SSE 流式对话 |
| GET | `/v1/sessions` | 会话列表 |
| POST | `/v1/sessions` | 创建会话 |

## 项目约定

- **后端**: RESTful 控制器 `/v1/*`，JPA 实体 + Lombok，Service 层业务逻辑
- **前端**: Composition API `<script setup>` 风格，Naive UI 组件，Pinia composition store
- **管理页面**: Vue Router `beforeEach` + 后端安全过滤器双重权限控制
- **技能系统**: 纯文件系统存储 `workspace/skills/{name}/SKILL.md`，YAML frontmatter 含 `name`、`description`、`category`

## 许可证

ISC
