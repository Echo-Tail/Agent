# EcomAgents 代码审查报告

> **日期**: 2026-06-07 | **范围**: EcomAgents/ (后端) + ShadcnAgentUI/ (前端) | **总量**: 422 个文件

---

## 🔴 P0 — 必须修复（安全/可靠性）

### 1. CORS 配置过于宽松

**文件**: `EcomAgents/src/main/java/cafe/snails/ecomagents/config/WebConfig.java:17-28`

```java
registry.addMapping("/v1/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
registry.addMapping("/chat/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
```

**问题**: `allowedOriginPatterns("*")` 配合 `allowCredentials(true)` 允许任意外部网站携带用户 Cookie/Authorization 头调用任意 API。

**建议**: 替换为具体前端域名白名单（`http://localhost:5174`, `https://yourdomain.com`），或移除 `allowCredentials(true)`。

---

### 2. SSE 广播异常吞事务回滚

**文件**: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/GroupMessageService.java:44-64`

```java
@Transactional                              // ← 事务
msg = groupMessageRepository.save(msg);     // ← 写入 DB
groupSseService.broadcast(...);             // ← SSE 广播（抛异常则事务回滚）
```

**问题**: `groupSseService.broadcast()` 在 `@Transactional` 方法内执行。广播抛异常 → Spring 标记事务为 `rollbackOnly` → 消息保存被回滚，用户以为发成功了但消息丢失。

**衍生问题**: `triggerAgentReplies()` 第 99 行使用 `new Thread(() -> {...}).start()`，高并发时可能耗尽系统线程。

**建议**:
- 将 SSE 广播移到事务外（`@TransactionalEventListener` 或 `TransactionSynchronization`）
- 用 `@Async` + 线程池替代 `new Thread()`

---

### 3. JPA 实体全部使用 `@Data`（Lombok）

**影响**: 全部 28 个 `@Entity` 类

```java
@Data          // 包含 @EqualsAndHashCode
@Entity
public class User { ... }
```

**问题**: Lombok `@Data` 生成的 `equals/hashCode` 包含 `id` 字段。JPA 实体 persist 前 `id=null`，persist 后 `id` 被赋值 → `hashCode()` 变化。如果 persist 前放入了 `HashSet`/`HashMap`，persist 后就找不到了。

**受影响文件**（示例）:
- `EcomAgents/.../model/User.java:13`
- `EcomAgents/.../model/Agent.java:15`
- `EcomAgents/.../model/Ticket.java:14`
- `EcomAgents/.../model/Skills.java`
- `EcomAgents/.../model/AiModel.java`
- 共 28 个模型文件

**建议**: 替换为 `@Getter @Setter @ToString(callSuper=true)` + 基于业务键的手动 `equals/hashCode`，或 `@EqualsAndHashCode(onlyExplicitlyIncluded=true)`。

---

### 4. `HttpURLConnection` 资源泄露

**文件**: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ImageGenerationService.java:377-399`

```java
java.net.HttpURLConnection conn = ...;
imageBytes = conn.getInputStream().readAllBytes();    // InputStream 未关闭
conn.disconnect();                                     // 抛异常时不执行（无 finally）
```

**问题**: 没有 try-with-resources / try-catch-finally，异常时连接不断开、流不关闭。且 `InputStream` 未显式关闭。

**建议**: 用 try-with-resources 包裹 `HttpURLConnection` 和 `InputStream`。

---

## 🟡 P1 — 建议短期修复

### 5. `request.ts:70` — `as any` 打破全部类型安全

**文件**: `ShadcnAgentUI/src/api/request.ts:70`

```ts
return body.data as any
```

**问题**: 所有 API 函数的返回类型声明（`Promise<LoginResponse>`）在运行时形同虚设。TypeScript 以为类型安全，实际全部丢失。

**影响范围**: 所有 API 文件 — `auth.ts`, `agent.ts`, `session.ts`, `model.ts`, `tool.ts`, `skill.ts`, `knowledge.ts`, `ticket.ts`, `systemLog.ts`, `token-usage.ts`, `gallery.ts`, `image.ts`, `group.ts`, `file.ts`, `invite.ts`, `user.ts`

**建议**: 改为 `return body.data as T`（`T` 由 axios 泛型传入）。

---

### 6. 文件上传路径穿越

**文件**: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/FileStorageService.java:73-74`

```java
String storedName = UUID.randomUUID() + "_" + originalName;
Path targetPath = uploadPath.resolve(storedName);
```

**问题**: `originalName` 来自 `MultipartFile.getOriginalFilename()`，如果包含 `../../` 路径穿越字符（例如 `../../etc/crontab`），`Path.resolve()` 会写入目标目录之外。

**衍生问题**: `FileController.java:68` 中 `Paths.get(record.getStoredPath())` 直接使用 DB `storedPath` 读取文件，路径穿越后可达系统文件。

**建议**: 剥离文件名中的路径分隔符，或仅使用 UUID 命名。

---

### 7. 缺少 DTO 参数校验 — 10 处 `@RequestBody` 缺 `@Valid`

| Controller | 行号 | 方法 | 风险 |
|---|---|---|---|
| `AgentController.java` | 42 | `createAgent()` | Agent 对象字段无 @NotBlank 等校验 |
| `AgentController.java` | 50 | `updateAgent()` | 同上 |
| `AiModelController.java` | 42 | `createModel()` | 模型配置（API Key、URL）可传入空值 |
| `AiModelController.java` | 47 | `updateModel()` | 同上 |
| `KnowledgeBaseController.java` | 44 | `createKnowledgeBase()` | 名称和描述无校验 |
| `KnowledgeBaseController.java` | 49 | `updateKnowledgeBase()` | 同上 |
| `ToolController.java` | 34 | `updateTool()` | 工具定义无校验 |
| `SystemLogController.java` | 27 | `createLog()` | `SystemLogRequest` 无校验注解 |
| `GalleryController.java` | 27 | `publish()` | `GalleryPublishRequest` 整 DTO 无注解 |
| `SkillController.java` | 39 | `importFromUrl()` | URL 可为空 |

**建议**: 在 DTO 字段上加 `@NotBlank` / `@NotNull` / `@Size` + 在 Controller 加 `@Valid`。

---

### 8. `EmojiPicker.vue` — DOM 事件未清理

**文件**: `ShadcnAgentUI/src/components/EmojiPicker.vue:62-64`

```ts
onMounted(() => {
  document.addEventListener('click', handleClickOutside)  // ← 无 onUnmounted 反注册
})
```

**问题**: 组件卸载后监听器仍存活。频繁创建/销毁会累积未清理的 handler。

**建议**: 添加 `onUnmounted(() => document.removeEventListener('click', handleClickOutside))`。

---

### 9. SSE 重连定时器未清理 — 2 处

**文件 1**: `ShadcnAgentUI/src/views/group/GroupChatView.vue:155`
**文件 2**: `ShadcnAgentUI/src/views/message/MessageChatView.vue:93`

```ts
setTimeout(() => connectSse(), 3000)  // ← 组件卸载后定时器仍会触发
```

**问题**: 组件卸载后定时器仍会触发 `connectSse()`，在已卸载组件的状态上调用方法。

**建议**: 存储 `timeoutId` 到 ref，在 `onUnmounted` 中 `clearTimeout`。

---

### 10. `GroupSseService` / `PrivateSseService` — ~85% 重复代码

- `EcomAgents/.../service/GroupSseService.java`
- `EcomAgents/.../service/PrivateSseService.java`

**问题**: 两个文件结构完全相同（`ConcurrentHashMap` + `CopyOnWriteArrayList` + `createEmitter` + `broadcast`/`sendToUser`），仅 key 类型不同（groupId vs userId）。

**建议**: 合并为泛型 `SseService<K>`，消除约 200 行重复。

---

## 🟢 P2 — 值得优化

### 11. 路由守卫重复调用 `verifyAuth()`

**文件**: `ShadcnAgentUI/src/router/index.ts:53` 和 `:68`

**问题**: `verifyAuth()` 在 URL 变化时被调用两次，每次产生一个 HTTP 请求。

**建议**: 将第一次 `verifyAuth()` 的结果存入局部变量复用。

---

### 12. 后端管理权限强依赖前端缓存

**文件**: `ShadcnAgentUI/src/stores/auth.ts:30`

```ts
get isAdmin: boolean {
  return currentUser.value?.role?.toLowerCase() === 'admin'
}
```

**问题**: `isAdmin` 来自 `localStorage` 缓存的用户 JSON。管理员后台降级用户后，用户不刷新页面仍可访问管理页面。

**建议**: 后端各管理 API 端点需独立验证角色（当前可能已有，需确认）；前端角色检查作为辅助不可依赖。

---

### 13. 头像上传逻辑重复

- `EcomAgents/.../service/AgentService.java:222-255` — `uploadAvatar()`
- `EcomAgents/.../service/GroupService.java:205-238` — `uploadAvatar()`

**问题**: 两段代码约 90% 相同（ext 提取 → 格式校验 → UUID 命名 → `Files.copy`），仅路径和 `save()` 调用不同。

**建议**: 抽取为 `AvatarUploadHelper.upload(file, subDir, prefix)`。

---

### 14. `HttpClient` 每次新建且不关闭

**文件**: `EcomAgents/.../service/AiModelService.java:225`

```java
HttpClient.newHttpClient()  // 每次校验模型都创建新实例，且不 close()
```

**问题**: Java 17+ `HttpClient` 内部维护连接池，不关闭导致线程泄漏。

**建议**: 提取为类级别的 `static final HttpClient` 单例，或注入为 Spring Bean。

---

### 15. `HarnessChatService.streamChat()` — 110 行单方法

**文件**: `EcomAgents/.../service/HarnessChatService.java:115-225`

**问题**: SSE 创建、Agent 调用、文件标记解析、Token 记录、错误处理全在一个方法。

**建议**: 拆分为 `handleFileMarkers()`, `emitReplyAndComplete()`, `handleError()` 等子方法。

---

### 16. `KnowledgeBaseService` — 方法重复

**文件**: `EcomAgents/.../service/KnowledgeBaseService.java`

- `findRelevantUnitsScored()` — 有 `ScoredUnit` 包装+排序
- `findRelevantUnits()` — 无评分包装

**问题**: 两个方法约 80% 代码相同（doc 加载 → term 提取 → score → filter），仅排序包装不同。

**建议**: 合并为一个方法，用参数控制是否返回评分信息。

---

### 17. 文件下载不走 axios 拦截器

- `ShadcnAgentUI/src/api/file.ts:30` — `downloadFileApi`
- `ShadcnAgentUI/src/api/group.ts:36` — `downloadGroupFileApi`

**问题**: 使用原生 `fetch` 调用，401 时不触发 axios 拦截器的自动登出跳转。

**建议**: 考虑用 axios 的 `responseType: 'blob'` 替代，或手动处理 401。

---

## ✅ 做得好的地方

| 方面 | 评价 |
|---|---|
| **SQL 注入防护** | 全部 JPA 参数化查询（`@Query` + `@Param` 或 CriteriaBuilder），无字符串拼接 |
| **全局异常处理** | `GlobalExceptionHandler`：服务端记录完整堆栈，客户端仅通用消息，无信息泄露 |
| **XSS 防护** | 未发现未转义用户输入直接渲染到 HTML |
| **文件扩展名校验** | `FileStorageService` 白名单覆盖合理 |
| **前端组件设计** | shadcn-vue 模式正确，`InjectionKey` 类型安全的 provide/inject |
| **路由导航** | `beforeEach` 守卫流程清晰（白名单 → token 校验 → 管理员校验） |
| **测试覆盖** | 后端 70+ 测试类、前端 10+ 测试文件 |
| **代码注释** | 后端控制器/服务/配置类均有 JavaDoc 类注释 |

---

## 汇总

| 优先级 | 数量 | 主要问题 |
|---|---|---|
| 🔴 P0 — 必须 | 4 | CORS 过度宽松、事务回滚被吞、@Data 实体、连接泄露 |
| 🟡 P1 — 短期 | 6 | as any 类型安全、路径穿越、缺 @Valid、DOM 内存泄漏、重连定时器、~85% 重复代码 |
| 🟢 P2 — 优化 | 7 | 重复调用 verifyAuth、前端权限缓存、重复上传逻辑、HttpClient 泄漏、长方法、~80% 重复、fetch 不走拦截器 |
