# P0 + P1 修复验证手册

> **日期**: 2026-06-07 | **范围**: 4 个 P0 + 6 个 P1 修复

---

## 1️⃣ CORS 跨域 — 白名单配置化

**文件**: `WebConfig.java`, `application.properties`

| 测试 | 预期结果 |
|------|----------|
| `http://localhost:5174` 访问 | ✅ 正常（在白名单中） |
| `http://127.0.0.1:5174` 访问 | ❌ 被 CORS 拦截（不在白名单） |
| `http://192.168.x.x:5174` 访问 | ✅ 正常（192.168.* 通配） |

如需添加来源 → 修改 `application.properties` 的 `cors.allowed-origins`。

---

## 2️⃣ 群聊消息 + SSE（事务修复 + 服务合并 + 重连清理）

**文件**: `GroupMessageService.java`, `SseService.java`, `GroupChatView.vue`

### 2a. 发送群消息

1. 进入群聊页面
2. 输入消息内容，点击发送
3. **预期**: 消息立即显示在聊天列表中，不用刷新页面

### 2b. @Agent 回复

1. 在输入框中输入 `@[Agent名称](agent:AgentID)` 格式
2. 发送消息
3. **预期**: Agent 在几秒后自动回复，消息通过 SSE 实时推送

### 2c. SSE 断线重连

1. 打开 F12 → Network → 切换 Offline
2. 等待 5 秒
3. 恢复 Online
4. **预期**: SSE 自动重新连接，新消息继续实时推送（控制台无泄漏警告）

---

## 3️⃣ 私聊 SSE（服务合并 + 重连清理）

**文件**: `SseService.java`, `MessageChatView.vue`

1. 进入私聊页面
2. 发送消息
3. **预期**: 接收方实时收到消息推送
4. 测试断线重连（同群聊步骤）
5. 切换页面后再回来 → SSE 正常连接

---

## 4️⃣ 图片生成（HttpURLConnection 修复）

**文件**: `ImageGenerationService.java`

1. 进入「图片生成」页面
2. 输入提示词，点击生成
3. **预期**: 图片生成成功并显示
4. 连续生成 10 张图
5. **预期**: 服务端日志无 `Too many open files` 错误，图片均正常生成

---

## 5️⃣ 文件上传（路径穿越修复）

**文件**: `FileStorageService.java`

### 5a. 正常上传

1. Agent 编辑 → 上传头像 → 选一个普通图片
2. **预期**: 上传成功，头像正常显示

### 5b. 路径穿越测试（可选）

1. 将文件名改为 `../../etc/passwd.png`（或包含 `../` 的路径）
2. 上传
3. **预期**: 文件安全写入 `uploads/` 目录内，不会穿越到系统目录

### 5c. 知识库文档上传

1. 进入知识库 → 上传 TXT/MD/JSON 文件
2. **预期**: 上传成功，内容可检索

---

## 6️⃣ 表情选择器（内存泄漏修复）

**文件**: `EmojiPicker.vue`

1. 打开群聊或私聊
2. 点击表情按钮打开面板
3. 选一个表情插入到输入框
4. **预期**: 面板关闭，表情插入
5. 多次快速切换页面（聊天 → 设置 → 聊天 → 群聊）
6. **预期**: 操作流畅，控制台无异常，无内存累积

---

## 7️⃣ 表单校验补全（@Valid）

**文件**: 6 个 Controller + 3 个 DTO

| 页面 | 操作 | 预期结果 |
|------|------|----------|
| 创建 Agent | 不填名称，直接提交 | 前端/后端提示必填错误 |
| 创建模型 | 不填 API Key 或 URL 提交 | 提示必填 |
| 创建知识库 | 不填名称提交 | 提示必填 |
| 配置工具 | 不填关键字段提交 | 提示必填 |
| 发布画廊 | recordId 为空提交 | 后端返回 400 |
| 模型验证 | 空 URL 或 Key 提交 | 后端返回校验失败 |
| 系统日志（内部） | 无 level/category/message | 后端拒绝 |

---

## 8️⃣ 类型安全包装（编译期改动）

**影响范围**: 无 UI 可验证。所有 API 调用保持运行时行为不变，TypeScript 类型推断更准确。

---

## 9️⃣ JPA 实体 @Data 替换（编译期改动）

**影响范围**: 无 UI 可验证。修复了 HashSet/HashMap 中 JPA 实体 hashCode 变化导致找不到元素的潜在 bug。

---

## 测试覆盖分析

### 已有测试（改动后自动适配）

| 测试文件 | 状态 | 说明 |
|----------|------|------|
| `SseServiceTest.java` | ✅ 已更新 | GroupSseService/PrivateSseService → SseService |
| `GroupMessageServiceTest.java` | ✅ 已更新 | 字段名+构造函数适配 |
| `PrivateMessageControllerTest.java` | ✅ 已更新 | 字段名+ verify 调用适配 |

### 推荐新增测试用例

| 优先级 | 文件 | 测试点 | 原因 |
|--------|------|--------|------|
| 🔴 高 | `FileStorageServiceTest` | `sanitizeFileName()` 路径穿越防护 | 新增方法，安全关键 |
| 🟡 中 | `SseServiceTest` | Emitter 完成/超时/错误时自动清理 | 已有测试太薄 |
| 🟢 低 | `GroupMessageServiceTest` | SSE broadcast 抛异常时消息保存不受影响 | 事务修复的关键验证 |

### 不需要改动的测试

| 测试文件 | 原因 |
|----------|------|
| `ImageGenerationDownloadTest` | 集成测试，测试 HTTP 下载连通性，不涉及 try-with-resources 语法 |
| `AiModelControllerTest` / `ToolControllerTest` | 现有测试走 mock，不测试 @Valid 校验失败路径 |
| `KnowledgeBaseControllerTest` | 同上 |
