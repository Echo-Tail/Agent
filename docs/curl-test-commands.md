# 图片生成接口测试（curl）

> 所有请求需携带 JWT Token，通过 `Authorization: Bearer <token>` 头传递。
> 假设服务运行在 `http://localhost:8888`，Token 为 `<your-token>`。

---

## 1. 文生图 — `POST /v1/images/generations`

```bash
curl -X POST "http://localhost:8888/v1/images/generations" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer <your-token>" ^
  -d "{\"model\":\"gpt-image-2\",\"prompt\":\"一只猫\",\"size\":\"1024x1024\",\"quality\":\"high\",\"n\":1}"
```

### 参数说明

| 参数 | 值 | 说明 |
|------|-----|------|
| `model` | `gpt-image-2` | 图片生成模型 |
| `prompt` | `一只猫` | 图片描述 |
| `size` | `1024x1024` | 图片尺寸 |
| `quality` | `high` / `medium` / `low` / `auto` | 图片质量，gpt-image 有效值 |
| `n` | `1` | 生成数量 |

---

## 2. 图生图 — `POST /v1/images/edits`

```bash
curl -X POST "http://localhost:8888/v1/images/edits" ^
  -H "Authorization: Bearer <your-token>" ^
  -F "model=gpt-image-2" ^
  -F "prompt=把图片改成红色" ^
  -F "image=@C:\path\to\test.png;type=image/png" ^
  -F "size=1024x1024"
```

### 参数说明

| 参数 | 值 | 说明 |
|------|-----|------|
| `model` | `gpt-image-2` | 图片生成模型 |
| `prompt` | `把图片改成红色` | 修改描述 |
| `image` | `@文件路径` | 参考图片（最多 4 张，重复 `-F "image=@..."`） |
| `size` | `1024x1024` | 图片尺寸 |

---

## 3. Fallback — `POST /v1/chat/completions`

文生图（尺寸嵌入 prompt）：

```bash
curl -X POST "http://localhost:8888/v1/chat/completions" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer <your-token>" ^
  -d "{\"model\":\"gpt-image-2\",\"messages\":[{\"role\":\"user\",\"content\":\"生成一张1024x1024的图片：一只猫\"}],\"n\":1}"
```

### 请求体格式

```json
{
  "model": "gpt-image-2",
  "messages": [
    {
      "role": "user",
      "content": "生成一张1024x1024的图片：一只猫"
    }
  ],
  "n": 1
}
```

### 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `model` | `gpt-image-2` | 模型名称 |
| `messages[].content` | — | prompt，尺寸已嵌入：`生成一张{size}的图片：{prompt}` |
| `n` | `1` | 生成数量 |

> 调用 `POST /v1/images/generations` 或 `POST /v1/images/edits` 失败时，服务端会自动触发 fallback 调用外部 OpenAI 兼容的 `/v1/chat/completions` 接口重试。无需手动调用此端点。

---

## 4. 响应格式

### 成功响应（文生图/图生图）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "url": "/uploads/generate/uuid.png",
    "revisedPrompt": "A cat...",
    "timeCostMs": 12345,
    "recordId": 1
  }
}
```

### 成功响应（Chat Completions fallback）

```json
{
  "id": "chatcmpl-abc123def456",
  "object": "chat.completion",
  "created": 1718000000,
  "model": "gpt-image-2",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "![generated image](/uploads/generate/uuid.png)\n\nA cat..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 100,
    "total_tokens": 110
  }
}
```

### 失败响应

```json
{
  "code": 500,
  "message": "图片生成失败（503 SERVICE_UNAVAILABLE），请稍后重试",
  "data": null
}
```
