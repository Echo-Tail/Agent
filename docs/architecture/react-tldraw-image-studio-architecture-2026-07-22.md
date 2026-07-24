# React + tldraw 图像创作工作台重构方案

状态：已确认，待原型验证  
日期：2026-07-22  
范围：前端重构、图像创作领域与后端适配；本文不包含业务代码改动

## 1. 背景与目标

现有前端采用 Vue 3，图像生成、图片历史和图像超分以独立页面和表单流程呈现。新的前端采用 React、shadcn/ui 与 tldraw，将图像生成重构为一个持续创作的画布会话。

核心目标是让用户在同一画布中完成：

```text
输入提示词/选择参考图
→ 生成图片
→ 基于原参考图重新生成，或基于结果继续生成
→ 圈选、涂抹并局部修改
→ 高清放大/超分
→ 下载或保存到素材库
```

过程中不得要求用户手动下载结果后重新上传。

## 2. 已确认的产品决策

1. 一个画布对应一个图像创作会话。
2. 首版不支持多人实时协作。
3. 节点关系固定，由系统自动创建，不提供自由工作流编排。
4. Amazon 图片工作台暂不迁移，也不进入新画布流程；现有后端暂时保留。
5. 图像超分作为画布中图片节点的操作，不再作为独立的新前端页面。
6. 首版支持 `GENERATE`、`VARIATION`、`INPAINT`、`UPSCALE`，暂不支持 `OUTPAINT`。
7. 图片和任务不可变；每次修改、重试和超分都产生新版本。
8. tldraw snapshot 只表示画布视图，不作为业务事实的唯一来源。

## 3. MVP 范围

### 3.1 包含

- 创建、打开、重命名、复制和删除图像会话。
- 输入提示词并选择模型、尺寸、质量和输出数量。
- 上传一张或多张参考图片。
- 文生图与基于参考图生成。
- 使用原任务输入修改提示词并重新生成。
- 将某张生成结果作为下一轮基础图继续生成。
- 在图片上使用画笔和橡皮擦制作蒙版并进行局部修改。
- 对选中图片进行 2×/4× 超分。
- 自动保存和恢复画布。
- 页面刷新后恢复运行中的任务和已完成结果。
- 下载结果或保存到素材库。

### 3.2 不包含

- 多人协作、评论和审核流。
- 任意节点和连线编排。
- Amazon 专用图片流程。
- 扩图、完整版本历史和画布分享。
- 移动端完整蒙版编辑。
- 多页画布、PSD 导出和复杂排版。
- 批量自动化工作流。

## 4. 核心用户流程

### 4.1 首次生成

空白画布提供“开始创作”入口。用户输入提示词，可选参考图，选择模型后提交。系统自动创建参考图、任务卡片、关系线和结果区域。

### 4.2 不满意：基于原输入重新生成

用户选择“重新生成”，系统继承原始参考图、模型和参数，打开提示词编辑。新任务作为同源方案向下排列，不使用当前结果作为输入。

### 4.3 部分满意：基于结果继续

用户选择“继续创作”，当前结果成为下一轮 `BASE`，原始参考图可继续作为 `REFERENCE`。用户只需修改提示词并提交。

### 4.4 局部修改

用户选择“局部修改”后进入专注的蒙版模式：图片锁定，用户使用画笔和橡皮擦标记区域，输入修改要求并提交。系统按照原图真实像素尺寸生成黑白 PNG mask，创建 `INPAINT` 任务。

### 4.5 超分

用户选择“高清放大”，设置 2×/4× 与模型支持的参数，创建 `UPSCALE` 任务。结果作为新资产出现在原图右侧，并提供像素级对比入口。

## 5. 画布交互模型

画布只包含三类核心对象：

- 图片：上传图、生成结果、超分结果。
- 任务卡片：生成、续作、局部修改、超分。
- 系统关系线：`Image → Job → Image`，锁定且不可改变端点。

多张结果使用 tldraw Frame 分组。新分支优先向右生长，同源重新生成向下排列。系统不得移动用户已经手动调整过的旧节点。

图片节点提供快捷操作：

```text
重新生成｜继续创作｜局部修改｜高清放大｜下载｜保存到素材库
```

删除键仅表示“从画布移除”，不物理删除任务或资产。

## 6. 前端架构

### 6.1 技术栈

- React + TypeScript + Vite
- React Router
- TanStack Query
- Zustand
- React Hook Form + Zod
- shadcn/ui + Tailwind CSS
- tldraw
- Vitest + Testing Library + Playwright

生产环境使用 tldraw 前必须确认许可证和 license key。开发原型无需生产许可证。

### 6.2 新旧前端

新建独立 React 工程 `AgentWeb/`，不在现有 Vue 工程中混合 React 运行时：

```text
EcomAgents/       Spring Boot 后端
ShadcnAgentUI/    现有 Vue 前端，迁移期继续运行
AgentWeb/         新 React 前端
```

迁移期间建议通过路径或独立入口分流，稳定后再切换正式导航。

### 6.3 状态边界

- TanStack Query：会话、任务、资产、模型能力和用户等后端业务数据。
- tldraw store：shape、frame、arrow、位置、尺寸、蒙版笔迹和撤销记录。
- Zustand：当前操作、选中资产、右侧面板和蒙版编辑模式等临时 UI 状态。

禁止在 Zustand 或 shape 中复制完整任务对象。

### 6.4 关键服务

`CanvasProjectionService` 负责将后端事实投影到画布：

```text
ensureJobShape
ensureOutputShapes
ensureRelationArrows
layoutNewJobBranch
reconcileCanvas
```

组件不得分散地直接创建业务 shape。用户操作通过明确用例执行，例如 `continueFromResult`、`startInpainting` 和 `startUpscale`。

## 7. 领域模型

```text
ImageSession
├── CanvasDocument
├── ImageAsset[]
├── ImageJob[]
│   ├── ImageJobInput[]
│   └── ImageJobOutput[]
└── 业务关系形成的不可变版本树
```

### 7.1 ImageSession

```text
id, user_id, title, status, cover_asset_id,
created_at, updated_at, deleted_at
```

图像会话必须与现有聊天 `Session` 分离。

### 7.2 CanvasDocument

```text
session_id, document_snapshot_json, schema_version,
revision, created_at, updated_at
```

服务端只保存 document snapshot；camera、selection 等 session state 保存到浏览器 IndexedDB。

### 7.3 ImageAsset

```text
id, session_id, type, storage_key, mime_type,
width, height, file_size, sha256, created_by,
created_at, deleted_at
```

资产类型至少包含 `ORIGINAL`、`GENERATED`、`MASK`、`UPSCALED`。图片字节存对象存储或现有文件存储，snapshot 只保存资产引用。

### 7.4 ImageJob

建议从现有 `ImageGenerationJob` 渐进演进：

```text
id, session_id, user_id, parent_job_id, retry_of_job_id,
operation, status, user_instruction, resolved_prompt,
negative_prompt, model_id, model_snapshot_json,
capabilities_snapshot_json, parameters_json,
provider, protocol, remote_model_name, provider_task_token,
progress, error_code, safe_error_message, retryable,
idempotency_key, created_at, started_at, completed_at,
updated_at, version
```

`DRAFT` 只存在于前端；后端状态使用 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`，内部继续保留细粒度执行阶段。

### 7.5 输入输出

输入角色为 `BASE`、`REFERENCE`、`MASK`：

| 操作 | BASE | REFERENCE | MASK |
|---|---:|---:|---:|
| GENERATE | 0 | 0 | 0 |
| VARIATION | 1 | 0～N | 0 |
| INPAINT | 1 | 0～N | 1 |
| UPSCALE | 1 | 0 | 0 |

每个任务可拥有多个独立输出资产。重试创建新任务，不修改旧任务。

## 8. 模型能力适配

前端只提交业务操作，不理解供应商协议。后端根据模型能力和适配器转换为 OpenAI、百炼或其他接口。

每个模型应暴露：

- 支持的操作。
- 最小/最大参考图数量。
- 是否支持 mask。
- 输出数量、尺寸、质量和格式。
- 是否支持负面提示词、seed、参考图权重和提示词扩写。
- 超分倍数、输入/输出像素限制和增强模式。

最终能力由“协议适配器默认能力 + 具体模型配置覆盖”组成。前端据此隐藏或禁用操作，后端仍必须完整校验。

当前 OpenAI 图片适配器已有参考图和 mask 请求基础；百炼适配器已有参考图能力，但 mask 能力需要按具体模型另行实现和声明。

不支持某项操作时不得静默切换模型。前端可推荐兼容模型，由用户确认后继承原图、蒙版和提示词。

## 9. API 草案

### 9.1 会话与工作台

```http
GET    /v1/image-sessions
POST   /v1/image-sessions
GET    /v1/image-sessions/{id}
PATCH  /v1/image-sessions/{id}
DELETE /v1/image-sessions/{id}
POST   /v1/image-sessions/{id}/duplicate
GET    /v1/image-sessions/{id}/workspace
```

### 9.2 画布

```http
GET /v1/image-sessions/{id}/canvas
PUT /v1/image-sessions/{id}/canvas
```

保存携带 `revision` 和 `schemaVersion`。版本冲突返回 `409 CANVAS_REVISION_CONFLICT`，MVP 不自动合并 snapshot。

### 9.3 资产

```http
POST   /v1/image-sessions/{id}/assets
GET    /v1/image-assets/{assetId}
DELETE /v1/image-assets/{assetId}
POST   /v1/image-assets/{assetId}/save-to-library
```

原图 URL 使用鉴权下载或短期签名 URL，不能暴露可猜测的文件路径。

### 9.4 任务

```http
POST /v1/image-sessions/{id}/jobs
GET  /v1/image-jobs/{jobId}
POST /v1/image-jobs/{jobId}/retry
POST /v1/image-jobs/{jobId}/cancel
GET  /v1/image-sessions/{id}/events
```

创建任务使用 `Idempotency-Key`，成功返回 `202 Accepted`。一个会话使用一条 SSE，断线时退化为轮询和状态对账。

## 10. 保存、恢复与对账

画布 document 变化后延迟 1～2 秒保存，保存状态包括 `saved`、`dirty`、`saving`、`offline`、`conflict` 和 `error`。

任务创建和资产上传不经过画布自动保存。任务在后端独立运行，浏览器关闭不影响执行。

重新打开会话时：

1. 加载业务数据和 canvas snapshot。
2. 查询所有非终态任务并恢复 SSE。
3. 对账业务任务与画布节点。
4. 补齐已经完成但尚未投影到画布的结果。
5. 恢复本地 camera/session 状态。

即使 snapshot 损坏，也应能根据任务、输入和输出关系重建基础画布。

## 11. 蒙版设计

蒙版同时保存：

- 与原图真实分辨率一致的黑白 PNG。
- 可继续编辑的矢量笔迹。
- 基础图片 ID 和生成时的原图尺寸。

笔迹使用相对于图片的归一化坐标，不依赖画布位置和显示尺寸。平台统一 mask 黑白语义，由供应商适配器负责必要转换。

提交前验证格式、尺寸、所有权、覆盖比例以及是否为空白。

## 12. 性能、存储与安全

- 画布加载缩略图，对比、蒙版编辑和下载时加载原图。
- snapshot 禁止嵌入 Base64；对 JSON 大小和结构进行校验。
- 单会话设置约 300 个图片/任务节点的软上限。
- 未使用的临时上传定期清理；被任务引用的资产不得物理删除。
- 会话删除采用软删除和延迟清理。
- 所有会话、任务和资产请求都校验用户所有权。
- 验证真实文件类型、文件大小、像素和解压后尺寸。
- API Key 只存在后端。
- 原始供应商错误不得直接返回前端或写入公开日志。

## 13. 费用与错误

任务提交前可展示预计费用，提交时以后端校验为准。使用幂等键、单用户并发限制、额度控制和最大重试次数防止重复消费。

统一安全错误码至少包含：

```text
CONTENT_REJECTED
MODEL_UNAVAILABLE
RATE_LIMITED
INSUFFICIENT_QUOTA
INVALID_INPUT
PROVIDER_TIMEOUT
PROVIDER_ERROR
STORAGE_ERROR
```

失败任务保留在画布，允许理解原因并重试。

## 14. 可观测性

日志与指标使用 `requestId`、`sessionId`、`jobId`、`providerTaskId` 和 `userId` 贯穿链路。

重点监控成功率、排队时间、生成耗时、失败分布、自动重试、SSE 断线、snapshot 保存失败、资产增长和用户费用。现有图片运行监控继续复用，并增加 `operation` 与 `sessionId` 维度。

## 15. 分阶段交付

### 阶段 0：无后端交互原型

验证图片进入画布、自动分支布局、重新生成、基于结果继续、蒙版导出、坐标对齐和超分对比。

### 阶段 1：React 基础设施

建立 `AgentWeb`、鉴权、API client、路由、查询缓存、主题、错误处理和新旧前端并行部署。

### 阶段 2：会话、资产与画布持久化

实现 `ImageSession`、`CanvasDocument`、`ImageAsset`、revision 冲突和自动恢复。

### 阶段 3：统一图片任务

接入 `GENERATE`、`VARIATION`、`INPAINT`、模型能力、幂等任务、SSE、失败重试和画布对账。

### 阶段 4：超分与素材库

接入 `UPSCALE`、结果对比、保存到素材库和下载。

### 阶段 5：替换旧图像页面

新导航切换到 React 画布，旧生图、超分和图片历史页面逐步下线。Amazon 后端继续保留但不出现在新导航。

## 16. 验收标准

- 用户在一个页面内完成参考图、生成、续作、局部修改、超分和下载。
- 连续创作过程中不需要手动下载再上传。
- 刷新页面后布局、任务和结果可以恢复。
- 任务完成但前端中断时，重新进入会话可以自动补齐结果。
- 蒙版与原图像素坐标准确对应。
- 每张结果都能追溯任务、输入图片、模型和参数。
- 删除画布节点不会误删任务和资产。
- 100 个图片/任务节点下保持正常拖动和缩放。
- SSE 不可用时可以降级轮询。
- 旧 Vue 页面保留经过验证的回退路径，直至新工作台达到下线条件。

## 17. 原型前检查项

- 确认 tldraw 商业授权预算与目标部署域名。
- 确认首批可用于 `INPAINT` 和 `UPSCALE` 的实际模型。
- 确认图片存储首版继续使用本地目录还是直接采用对象存储。
- 为阶段 0 准备固定的模拟输入、结果和蒙版验收样例。
- 原型评审通过前，不启动全量 Vue 页面迁移。
