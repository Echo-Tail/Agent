# 图片生成功能踩坑记录与修复方案

> **日期**: 2026-06-07 | **模块**: ImageGenerationService | **涉及**: 后端 Java + 前端 Vue

---

## 坑一：代理超时导致 SSL 连接中断

### 现象

```
Image generate IO error (122828ms): Unexpected end of file from server
```

生图请求耗时约 2 分钟，然后抛出 `Unexpected end of file from server`，生图失败。

### 排查过程

1. 用 curl 直接调用中转站 API 成功，94 秒拿到完整响应（1.8MB base64 图片）
2. 用 curl 和 Java 的唯一区别：curl 直连，Java 走了本地代理
3. 查看 `build.gradle` 发现代理配置：
   ```groovy
   bootRun {
       jvmArgs = [
           '-Dhttps.proxyHost=127.0.0.1',
           '-Dhttps.proxyPort=15236',
       ]
   }
   ```
4. 代理有约 120 秒超时限制，而生图响应耗时约 94 秒，代理在传输完成前将连接断开

### 修复

1. **去掉代理配置** — 删除 `build.gradle` 中的 `https.proxyHost` / `http.proxyHost`
2. **强制直连** — 所有 API 调用使用 `Proxy.NO_PROXY` 绕过系统代理：
   ```java
   HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
   ```
3. **前端超时放宽** — `300_000` → `600_000`（5 分钟 → 10 分钟）

### 教训

- Spring Boot 应用的代理配置要谨慎，特别是调用外部 API 做长时间操作时
- 代理层可能有隐式超时限制，排查网络问题时要先把代理排除
- 看到 `Unexpected end of file from server` 优先怀疑中间网络设备（代理/负载均衡）而非代码

---

## 坑二：WebClient 处理长时间请求的 200 空响应异常

### 现象

```
Image API returned 200 OK with empty body (236748ms)
```

中转站返回 HTTP 200，但 body 是空的。即使用 `bodyToMono(String.class)` 也无法正确读取。

### 根因

Spring WebClient 在长时间请求（>2 分钟）后，读取响应体时抛出异常进入 `WebClientResponseException` 的 catch 分支。同样的请求用 curl 可以正常获取完整 1.8MB 响应。

猜测是 WebClient 底层 Netty 在长时间空闲后连接状态不一致导致的。

### 修复

将 `generate()` 和 `edit()` 方法从 WebClient 完全切换到 `HttpURLConnection`：

```java
// 之前（WebClient）
String responseJson = client.post()
    .uri(model.getApiUrl() + "/v1/images/generations")
    .bodyValue(requestJson)
    .retrieve()
    .bodyToMono(String.class)
    .timeout(Duration.ofSeconds(timeoutSeconds))
    .block();

// 之后（HttpURLConnection）
HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
// ... 手动控制连接生命周期
try (OutputStream os = conn.getOutputStream()) { ... }
int statusCode = conn.getResponseCode();
try (InputStream in = conn.getInputStream()) {
    responseJson = new String(in.readAllBytes(), UTF_8);
}
```

### 教训

- WebClient 适合短请求和响应式流场景，长时间请求的稳定性不如 HttpURLConnection
- 对超时敏感的外部 API 调用，HttpURLConnection 的手动控制更可靠
- `bodyToMono(String.class).timeout().block()` 的异常处理路径不如 try-catch-finally 直观

---

## 坑三：`response_format` 参数导致 400 错误

### 现象

```
Unknown parameter: 'response_format'
```

中转站返回 HTTP 400，提示 `response_format` 参数不被支持。

### 根因

`gpt-image-2` 模型不支持 `response_format` 参数（这个参数是 dall-e 系列专用的）。我们之前为了兼容 dall-e 的 URL 返回格式加了这个参数。

### 修复

1. **去掉 `response_format`** — 请求体不再传此参数
2. **只处理 `b64_json`** — gpt-image 模型始终返回 base64，不再兼容 dall-e 的 URL 格式
3. **`resolveResultPath()`** — 新增方法，只读取 `data[0].b64_json` 字段

```java
// 请求体
{"model":"gpt-image-2","prompt":"...","n":1}

// 响应解析
JsonNode b64Node = dataNode.get("b64_json");
return saveBase64Image(b64Node.asText(), subDir);
```

### 教训

- 不同模型家族的参数不同，不要假设 dall-e 的参数在 gpt-image 上也能用
- API 返回 `unknown_parameter` 时应触发 fallback 重试（见坑四）

---

## 坑四：模型下架无 fallback 机制

### 现象

```
分组 sora 下模型 gpt-image-2 无可用渠道（distributor）
```

中转站的模型渠道下架后，用户直接看到「图片生成功能暂不可用」的错误。

### 修复

实现三级 fallback 机制：

```
用户生图请求
  ↓
① 主 API（/v1/images/generations 或 /v1/images/edits）
  ↓ 成功? → 返回结果
  ↓ 失败?
② 检测错误类型
  ├─ model_not_found      → 走 fallback
  ├─ unknown_parameter    → 走 fallback
  └─ 其他错误             → 返回正常错误提示
  ↓
③ Fallback Chat Completions
  ├─ 文生图: { model, messages: [{role:"user", content:"生成一张{size}的图片：{prompt}"}] }
  └─ 图生图: { model, messages: [{role:"user", content: [{type:"text",...}, {type:"image_url", image_url:{url:"data:..."}}]}] }
  ↓
  解析 choices[0].message.content 提取图片 URL → 下载 → 保存记录
```

### 关键实现

```java
// 检测是否应触发 fallback
private boolean isModelNotFoundError(String body) {
    // model_not_found → 模型下架
    // unknown_parameter → 参数不兼容
    // Unknown parameter → 文本匹配兜底
}

// fallback 优先级：显式配置 > 复用失败请求的模型信息
String targetUrl = (fallbackApiUrl != null && !fallbackApiUrl.isBlank())
    ? fallbackApiUrl : model.getApiUrl() + "/v1/chat/completions";
```

### 教训

- 外部 API 随时可能下架/变更，业务代码必须有兜底策略
- fitHub/中转站的渠道不可控，fallback 到标准 OpenAI 兼容接口是最保险的
- fallback 的配置应支持「显式指定」和「复用失败信息」两种模式

---

## 坑五：图片编辑接口参数不符合官方标准

### 现象

编辑生图请求中的参数不符合 OpenAI 官方标准。

### 修复

对照官方文档调整：

| 项目 | 改前 | 改后 |
|------|------|------|
| 图片字段名 | `image` | `image[]` |
| 多余参数 | `size`, `quality`, `output_format`, `n` | 全部去掉 |
| Chat Completions 图片格式 | `{"type":"input_image","image_url":"data:..."}` | `{"type":"image_url","image_url":{"url":"data:..."}}` |
| Chat Completions 文本格式 | `{"type":"input_text","text":"..."}` | `{"type":"text","text":"..."}` |

### 教训

- 对接外部 API 前一定要先看官方文档，不要猜参数
- Chat Completions 的 `content` 数组类型名是 `text` 和 `image_url`（不是 `input_text` 和 `input_image`）
- `image_url` 的值是嵌套对象 `{"url": "data:..."}` 而不是直接字符串

---

## 坑六：质量参数传错值

### 现象

`quality` 参数传了 `standard`，但 gpt-image 模型不认这个值。

### 根因

| 模型 | 支持的 quality 值 |
|------|------------------|
| gpt-image 系列 | `auto`, `high`, `medium`, `low` |
| dall-e-3 | `standard`, `hd` |
| dall-e-2 | 仅 `standard` |

前端传了 `standard`，这是 dall-e 的值。

### 修复

前端 `qualityOptions` 已改为正确的值：`high / medium / low / auto`，默认 `high`。

### 教训

- 模型间的参数值差异大，不要混用
- 对接新模型前要确认所有参数的支持范围

---

## 经验总结

### 排查顺序

```
生图失败
  ↓
① 前端还是后端报错？
  ├─ 前端超时     → 检查前端 timeout 配置
  └─ 后端报错     → 查看后端日志
  ↓
② 后端日志类型
  ├─ WebClientResponseException → 检查 API 返回的错误体
  ├─ IOException                → 检查网络/代理/SSL
  ├─ BusinessException          → 检查业务逻辑
  └─ SocketException            → 检查连接重置/超时
  ↓
③ 用 curl 复现
  ├─ curl 成功 → 问题在代码/配置
  └─ curl 失败 → 问题在 API/网络
```

### 配置检查清单

- [ ] `build.gradle` 是否有 `https.proxyHost` 配置
- [ ] `application.properties` 的 `image.timeout-seconds` 是否足够大
- [ ] 前端 `api/image.ts` 的 `timeout` 是否与服务端匹配
- [ ] 数据库 `ai_models` 表中的模型 API URL 和 Key 是否正确
- [ ] `image.fallback.api-url/key` 是否已配置（可选）

### 代码层面

- **不要信赖 WebClient 的长时间请求稳定性** → 用 HttpURLConnection
- **不要假设外部 API 永远可用** → 加 fallback 机制
- **不要假定模型参数通用** → 按模型文档传参
- **响应体可能很大（1.8MB base64）** → 确保超时和缓冲区够用
