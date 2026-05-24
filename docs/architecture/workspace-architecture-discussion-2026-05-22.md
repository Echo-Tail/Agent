# Workspace 架构讨论记录

日期: 2026-05-22
参与人: 用户 + Claude
议题: HarnessAgent 生命周期、Workspace 架构、多租户设计

---

## 背景

项目使用 AgentScope Java SDK v1.1.0-RC1 的 `HarnessAgent` 进行 Agent 对话管理。
当前 workspace 结构存在与 AgentScope 标准不一致的问题。

---

## 发现的关键事实

### 标准 Hooks 已默认启用

`HarnessAgent.builder().build()` 在不调用 `disable*` 方法的情况下会注册以下 hooks：

| 优先级 | Hook | 触发时机 | 作用 |
|--------|------|---------|------|
| 0 | `AgentTraceHook` | PreCall/PostCall | 推理追踪 |
| 5 | `MemoryFlushHook` | PostCallEvent | 记忆写入 MEMORY.md + 会话 offload |
| 6 | `MemoryMaintenanceHook` | PostCallEvent | 记忆轮换整合 |
| 80 | `SubagentsHook` | PreCall | 子 agent 编排 |
| 100 | **HarnessHooks (自定义)** | 各事件 | **SSE 推送（per-request 状态）** |
| 900 | `WorkspaceContextHook` | PreCallEvent | 注入 AGENTS.md / MEMORY.md / KNOWLEDGE.md |
| 900 | `SessionPersistenceHook` | PostCallEvent | 持久化会话状态 |

### WorkspaceContextHook 读取的路径（全为固定路径）

- `{workspace}/AGENTS.md`
- `{workspace}/MEMORY.md`
- `{workspace}/knowledge/KNOWLEDGE.md`
- `{workspace}/knowledge/*`（文件列表）

### 当前项目的问题

1. **HarnessAgent 生命周期**: CONTEXT.md 写的是"缓存复用"，但代码是每次请求新建 `HarnessAgent`
2. **HarnessHooks 状态耦合**: SSE emitter、AtomicBoolean、StringBuilder 三个 per-request 对象绑定在 Hook 上
3. **AGENTS.md 格式简陋**: 只写了 `{systemPrompt}\n<!-- Agent ID: {id} -->`，缺少结构化内容
4. **MEMORY.md 始终为空**: 初始化写入 `# Agent Memory\n\n` 后无后续内容
5. **知识库双重存储**: DB `knowledge_documents` 表 + `knowledge/KNOWLEDGE.md` 文件并存
6. **多租户目录混乱**: 磁盘上存在 `agents/{name}/sessions/` 和 `{userId}/agents/{name}/sessions/` 两套路径

---

## HarnessAgent 缓存复用方案

### 问题根因

HarnessHooks 中绑定了 per-request 状态：

```java
public class HarnessHooks implements Hook {
    private final SseEmitter emitter;         // 每次请求不同
    private final AtomicBoolean completed;    // 每次请求不同
    private final StringBuilder partialContent; // 每次请求不同
}
```

这些对象在 `HarnessAgent.builder().hooks(List.of(hooks))` 时注册到 Agent，导致每次请求必须新建 Agent。

### 方案 B：用 RuntimeContext 传 emitter（已确认可行）

AgentScope SDK 支持 `RuntimeContextAware` 接口：

```java
public class HarnessHooks implements Hook, RuntimeContextAware {
    private RuntimeContext runtimeContext;

    @Override
    public void setRuntimeContext(RuntimeContext ctx) {
        this.runtimeContext = ctx;  // 每次 call() 前被框架调用
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        SseEmitter emitter = this.runtimeContext.get("sseEmitter");
        // 使用 emitter 推送 SSE
        return Mono.just(event);
    }
}
```

调用方：

```java
RuntimeContext ctx = RuntimeContext.builder()
    .sessionId(sessionId)
    .userId(String.valueOf(userId))
    .put("sseEmitter", emitter)    // per-request 状态通过 RuntimeContext 传递
    .build();

agent.call(userMsg, ctx);          // 框架自动 setRuntimeContext → 执行 hooks → clear
```

`RuntimeContext.put(String key, Object value)` 基于 `ConcurrentMap<String, Object>`，线程安全。

### 生命周期

```
AgentBase.call() 开始
  → bindRuntimeContextToHooks(ctx)
    → 对每个 RuntimeContextAware hook 调用 setRuntimeContext(ctx)
  → notifyHooks(PreCallEvent)    ← WorkspaceContextHook 注入 system prompt
  → ... ReAct 循环 ...
  → notifyHooks(PostCallEvent/ErrorEvent)  ← SessionPersistenceHook 写回
  → unbindRuntimeContextFromHooks()
    → 对每个 RuntimeContextAware hook 调用 setRuntimeContext(null)
AgentBase.call() 结束
```

---

## 多租户设计讨论

### 用户的需求

1. Agent 可以设置为 **公开** 或 **私有**
   - 公开：所有注册用户可访问
   - 私有：仅创建者和管理员可访问
2. `RuntimeContext.userId` 充分利用
3. 知识库是 **公开的**（共享给所有用户）
4. 尽量按 AgentScope 标准走

### 方案评估

#### 方案 A：NamespaceFactory（存在但过于粗暴）
`AbstractFilesystem` 的 `NamespaceFactory` 会给**所有路径**加 `{userId}/` 前缀。
包括 `AGENTS.md`、`knowledge/` 等本应共享的文件，与方案冲突。

#### 方案 B：userId 嵌入 sessionId（推荐，轻量干净）
- 文件路径保持 AgentScope 标准布局
- sessionId 格式：`sess-{agentId}-{userId}-{uuid}`
- Agent 公开/私有：在 Controller/Service 层做授权检查
- 知识库共享：`KNOWLEDGE.md` 在 workspace 根目录，所有用户共用
- 会话归属：DB sessions 表记录 agentId + userId，userId 从 RuntimeContext 获取

### Agent 公开/私有权限检查层

```
ChatController.streamChat(agentId, ...)
  → 检查 agent.visibility + agent.userId + currentUser.role
  → 如果私有且非创建者且非管理员 → 403
  → HarnessChatService.streamChat(...)
```

---

## 待办优先级

| 优先级 | 事项 | 难度 | 影响范围 |
|--------|------|------|---------|
| P0 | 多租户目录混乱：统一路径规则，消除 `{userId}/agents/` 冗余 | 中 | workspace 文件系统 |
| P0 | HarnessAgent 缓存复用：RuntimeContextAware 方案 | 中 | HarnessAgentManager, HarnessHooks, HarnessChatService |
| P1 | AGENTS.md 格式改进：增加 name/description/guidance 结构 | 低 | WorkspaceInitService |
| P1 | MEMORY.md 记忆持久化验证：确认 MemoryFlushHook 是否正常工作 | 低 | 测试验证 |
| P2 | 清理死代码：移除 `KnowledgeBaseService.buildKnowledgeContext()` | 低 | KnowledgeBaseService |
| P2 | 清理空目录初始化：subagents/ 等无用目录 | 低 | WorkspaceInitService |
| P3 | Windows 符号链接问题 | 中 | WorkspaceInitService |

---

## 下次讨论的切入点

1. 用户先看 AgentScope 源码中：
   - `RuntimeContextAware` 接口
   - `HookEvent` 子类链（PreCallEvent → ReasoningEvent → ActingEvent → ...）
   - `SessionPersistenceHook` / `MemoryFlushHook` 对 userId 的使用
2. 确认方案 B 的可行性
3. 讨论 Agent 公开/私有的具体数据库设计（加字段？已有 roles 表？）
4. 讨论 session 隔离粒度：只是 sessonId 区分？还是需要文件系统隔离？
