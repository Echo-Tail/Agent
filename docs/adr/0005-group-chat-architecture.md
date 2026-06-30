# 群聊与用户私聊采用独立模块设计

## Status

Accepted — 2026-07-XX

## Context

现有系统仅支持单聊会话（1 用户 ↔ 1 Agent），无法满足多用户多 Agent 协作沟通的需求。需要新增：

1. **群聊**（Group Chat）— N 用户 + N Agent 在同一个聊天室内交流，用户可 `@Agent` 触发 Agent 回复
2. **用户私聊**（Private Message）— 1 用户 ↔ 1 用户之间的文字/文件交流

## Constraints

- 与现有 Session（一对一 Agent 会话）体系并行，不修改现有数据模型
- 复用现有文件上传/下载基础设施（FileStorageService）
- Agent 回复复用现有 HarnessAgent 推理能力
- 前端在 ShadcnAgentUI/ 中新增路由，不污染现有 DirectChatView

## Considered Options

### 1. 群聊与 Session 的关系

- **方案 A：群聊继承 Session** — 扩展现有 `Session` 实体，增加 `type` 字段区分单人/群聊，`session_messages` 表增加 `sender_type`。**拒绝原因**：现有 Session 的 userId 字段是单值（归属用户），群聊需要多成员；现有消息存储走 HarnessAgent JSONL，群聊消息不通过 HarnessAgent；耦合过紧，改动风险高。
- **方案 B：独立模块（选定）** — 新表 `chat_groups`、`group_members`、`group_agents`、`group_messages`、`group_files`、`chat_private_messages`，与现有 `sessions` 表完全独立。**选定原因**：零侵入现有架构，群聊的成员管理、消息存储、权限模型与单聊完全不同，独立模块更清晰。

### 2. @Agent 的消息格式

- **方案 A：纯文本正则解析** — 消息体存文本 `@小助手 今天天气`，后端正则匹配 Agent 名称。**拒绝原因**：同名 Agent 无法精确匹配；Agent 改名后历史消息解析失效。
- **方案 B：Markdown 链接格式（选定）** — 前端替换选择结果为 `@[Agent名称](agent:123)`，后端解析 `(agent:数字)` 获取 Agent ID。**选定原因**：与现有 MarkdownRenderer 渲染体系一致；精确匹配不依赖名称；改名后链接仍然有效。

### 3. 群聊实时推送

- **方案 A：前端轮询** — 定时 `GET /v1/groups/{id}/messages?since=timestamp`。**拒绝原因**：延迟高，带宽浪费。
- **方案 B：WebSocket** — 每个群建立 WebSocket 连接。**拒绝原因**：需要额外依赖（Spring WebSocket / STOMP），与现有 SSE 技术栈不一致，增加前端复杂度。
- **方案 C：群级别 SSE（选定）** — `GET /v1/groups/{id}/sse` 返回 SseEmitter，与现有单聊 SSE（`POST /chat/{agentId}/stream`）并列。**选定原因**：复用 Spring Boot 现有 SSE 能力；前端已有 SSE 处理经验；一个群一个连接，多个群多个连接，架构简单。

### 4. 用户私聊定位

- **方案 A：复用现有 Session** — 私聊视为没有 Agent 的 Session。**拒绝原因**：Session 强依赖 agentId 字段，改造成本大于新建；权限模型不同。
- **方案 B：独立路由 `/messages`（选定）** — 新增 `chat_private_messages` 表，侧边栏增加"消息"入口。**选定原因**：与群聊/Agent 私聊并列，导航清晰；独立表易于扩展（已读/未读、消息撤回等）。

## Decision

采用独立模块方案，新增以下数据库表和路由：

### 新增表

| 表 | 用途 |
|------|------|
| `chat_groups` | 群基本信息（名称、头像、创建者） |
| `group_members` | 成员 + 角色（CREATOR / MEMBER） |
| `group_agents` | 群绑定的 Agent（由成员拉入） |
| `group_messages` | 消息（sender_type: USER / AGENT） |
| `group_files` | 群内文件（独立上传面板，不依附于消息） |
| `chat_private_messages` | 用户私聊消息（sender / receiver） |
| `emoji_packs` | 内置表情包库 |
| `user_emoji_favorites` | 用户收藏的表情 |

### 权限模型

| 行为 | 群创建者 | 普通成员 | 非成员 |
|------|---------|---------|-------|
| 解散群 | ✅ | ❌ | ❌ |
| 踢人 | ✅ | ❌ | — |
| 邀请新成员 | ✅ | ✅ | — |
| 拉入自己的 Agent | ✅ | ✅ | — |
| 发消息 | ✅ | ✅ | ❌ |
| 上传/下载文件 | ✅ | ✅ | ❌ |
| 修改群信息 | ✅ | ❌ | ❌ |

### @Agent 格式

消息体存 `@[Agent名称](agent:123)`，后端解析 `(agent:数字)` 后异步触发对应 Agent 推理。

### 前端路由

| 路由 | 说明 |
|------|------|
| `/groups` | 群列表页 |
| `/groups/{id}` | 群聊天页 |
| `/messages` | 用户私信列表 |
| `/messages/{userId}` | 与指定用户的私聊 |

## Consequences

- 需新增约 8 个 JPA 实体、4 个 Controller、6+ 个前端页面组件，属于中大型功能
- 现有 `Session` / `ChatController` / `DirectChatView` 完全不受影响
- 群消息不走 HarnessAgent 的 JSONL 存储，直接存 DB（`group_messages` 表），简化架构
- Agent 回复仍然通过 `HarnessChatService` 走 HarnessAgent 推理，通过群 SSE 广播
- 表情包初期仅做内置库（系统预置图片），用户上传自定义表情包延后
- 私聊不做消息撤回、已读未读等高级功能（初期）
- 群聊不做群公告、管理员等复杂角色体系（仅 CREATOR / MEMBER 两级）
