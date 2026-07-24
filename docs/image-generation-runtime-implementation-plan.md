# 图片生成运行时实施计划

日期：2026-07-14  
状态：实施中（阶段 1、阶段 2 已完成；阶段 3 Worker 基础、阶段 4 OpenAI Adapter、阶段 5 百炼 Adapter 已落地）  
领域决策来源：[CONTEXT.md](../CONTEXT.md)

## 1. 目标

将现有同步、供应商耦合的 `ImageGenerationService` 深化为 Job-first 图片生成运行时：

- 文生图与图生图使用类型安全命令，共享统一运行时。
- OpenAI-compatible 与阿里百炼通过独立 Adapter 接入。
- 数据库任务表是任务状态 SSOT，后台 Worker 可抢占、续租、恢复和安全重试。
- 模型配置使用可组合能力集合；每项能力独立选择 protocol 和连接覆盖。
- API Key 迁移到独立加密凭据模块。
- 参考图在提交阶段生成不可变任务快照。
- 前端通过 `/v1/image-jobs` 提交并轮询，旧图片接口保留一个迁移周期。

非目标：

- 本计划不同时重构对话运行时。
- 第一阶段不引入消息队列、Redis 或新的 SSE 通道。
- 第一阶段不实现 inpaint、outpaint、视频生成等未来能力。
- 不把供应商原始 DTO、任务 ID、临时 URL 或错误体暴露给调用方。

## 2. 当前风险基线

现有实现必须先用测试锁定，避免重构时改变已上线行为：

- `ImageGenerationService` 写死 `MODEL_NAME = "gpt-image-2"`，选择的 `AiModel.modelName` 未真正生效。
- 文生图、图生图、fallback、并发、下载、落盘、历史和计量集中在单个类中。
- OpenAI 图片路径与参数写死，无法安全承载百炼异步协议。
- `n > 1` 的耗时、计量和记录语义不准确，Amazon 图片任务只保存第一条记录 ID。
- 上传文件缺少统一的真实 MIME、大小、解码、像素和安全文件名校验。
- 旧代码依赖 `HttpURLConnection + Proxy.NO_PROXY` 规避长连接代理中断，迁移 Adapter 时必须保留等价网络策略并增加可配置项。
- 当前依赖 Hibernate `ddl-auto=update`，无法可靠执行数据回填、凭据加密和兼容字段删除。

## 3. 目标调用流

```text
ImageGenerationController / AmazonImageTaskService / ChatCompletionsController
                              |
                              v
                   ImageGenerationRuntime
                  submit / get / results / cancel / retry
                              |
                 +------------+-------------+
                 |                          |
          Job + input snapshots       Adapter registry
                 |                          |
                 v                  (protocol, capability)
       PostgreSQL lease Worker        /                 \
                 |          BailianImageAdapter   OpenAiImageAdapter
                 v
       download -> validate -> persist
                 |
                 v
       ImageGenerationRecord + usage
```

## 4. 数据模型

### 4.1 新表

`model_credentials`

- `id`, `name`, `provider`
- `encrypted_secret`, `encryption_version`, `masked_hint`
- `created_at`, `updated_at`, `last_rotated_at`

`ai_model_capabilities`

- `id`, `model_id`, `capability`, `protocol`
- `model_name_override`, `api_url_override`, `credential_id_override`
- `options_json`
- unique (`model_id`, `capability`)

`image_generation_jobs`

- 身份：`id`, `user_id`, `model_id`, `retry_of_job_id`
- 请求：`mode`, `prompt`, `negative_prompt`, `target_count`, `options_json`
- 非敏感模型快照：`provider`, `protocol`, `remote_model_name`, `api_url`, `capability`, `credential_id`
- 状态：`status`, `execution_phase`, `success_count`, `failure_count`
- Worker：`worker_id`, `lease_until`, `attempt_count`, `next_attempt_at`
- 供应商：加密或受限访问的 `provider_task_token`、`provider_status`
- 错误：`error_code`, `safe_error_message`, `retryable`
- 时间：`created_at`, `started_at`, `completed_at`, `updated_at`
- 并发控制：`version`（乐观锁）

`image_generation_job_inputs`

- `id`, `job_id`, `input_index`, `role`, `source_type`, `source_id`
- `snapshot_path`, `mime_type`, `file_size`, `sha256`
- unique (`job_id`, `input_index`)

### 4.2 扩展现有表

`image_generation_records`

- 增加 `job_id`, `output_index`, `status`, `error_code`, `safe_error_message`
- 增加单图 `time_cost_ms`、供应商安全元数据与真实计量字段
- unique (`job_id`, `output_index`)，旧记录允许 `job_id` 为空并视为成功

`ai_models`

- 增加 `default_credential_id`
- 现有 `model_name`、`api_url` 作为能力配置的默认值
- `api_key`、`model_type` 进入兼容期，完成双读迁移后删除

### 4.3 状态约束与索引

- Job 状态：`PENDING`, `RUNNING`, `SUCCEEDED`, `PARTIALLY_SUCCEEDED`, `FAILED`, `CANCEL_REQUESTED`, `CANCELLED`。
- 执行阶段：`PREPARING`, `SUBMITTING`, `POLLING`, `DOWNLOADING`, `PERSISTING`。
- 领取索引：(`status`, `next_attempt_at`, `lease_until`, `created_at`)。
- 用户查询索引：(`user_id`, `created_at desc`)。
- 输入和输出必须按 index 稳定排序。

## 5. 实施阶段

### 阶段 0：迁移基础与特征测试

任务：

1. 引入 Flyway，基线现有 PostgreSQL；生产将 `ddl-auto` 从 `update` 调整为 `validate`。
2. 为现有文生图、图生图、fallback、部分成功、历史删除、模型选择和用量记录补特征测试。
3. 为 `AmazonImageTaskService` 和 `ChatCompletionsController` 补调用链测试，锁定兼容行为。
4. 建立第三方 Adapter contract-test fixture，保存脱敏的成功、异步、限流、认证失败和异常响应样本。

主要文件：

- `EcomAgents/build.gradle`
- `EcomAgents/src/main/resources/application*.properties`
- `EcomAgents/src/main/resources/db/migration/`
- `ImageGenerationServiceTest.java`
- `AmazonImageTaskServiceTest.java`
- 新增图片 Adapter contract tests

退出标准：

- Flyway 可在空库与现有库基线上启动。
- 现有图片生成测试形成稳定基线，不访问真实供应商网络。

### 阶段 1：能力与加密凭据基础

任务：

1. 新增 `ModelCapability`, `ModelCredential` 及 Repository。
2. 实现 `CredentialCrypto` interface 与 AES-GCM implementation；主密钥仅从环境注入。
3. 新增凭据管理 DTO、控制器和审计；响应只返回 masked hint。
4. 实现 `ResolvedModelCapability`，按“能力级覆盖 → 模型级默认”解析配置。
5. 能力迁移：
   - `TEXT` → `CHAT`
   - `IMAGE` → `TEXT_TO_IMAGE + IMAGE_TO_IMAGE`
   - `MULTIMODAL` → `CHAT + TEXT_TO_IMAGE + IMAGE_TO_IMAGE`
6. 将现有不同 `api_key` 加密迁移为凭据记录；保留 `api_key` 双读但停止新写明文。
7. 模型管理页面改为能力多选、能力级 protocol/模型名/地址/凭据覆盖。

主要文件：

- `model/AiModel.java`
- 新增 `model/AiModelCapability.java`, `model/ModelCredential.java`
- 新增 Repository、凭据 module、迁移脚本
- `service/AiModelService.java`
- `controller/AiModelController.java` 与新增凭据控制器
- `ShadcnAgentUI/src/views/admin/ModelManage.vue`
- `ShadcnAgentUI/src/types/api.ts`, `src/api/model.ts`

退出标准：

- 新建/编辑模型不写明文 API Key。
- 三种旧 `modelType` 映射测试通过。
- 能力级覆盖解析和密钥轮换测试通过。
- 仍使用旧图片路径时行为不变。

### 阶段 2：Job 聚合、输入快照与运行时 interface

任务：

1. 新增 Job、输入、输出状态实体和 Repository。
2. 定义 sealed command：`TextToImageCommand`, `ImageToImageCommand`。
3. 定义 `ImageGenerationRuntime`：`submit`, `get`, `results`, `cancel`, `retry`。
4. 提交事务完成：用户权限、模型启用、能力、参数、参考图读取与安全校验、不可变快照、Job/输出占位记录。
5. 快照写入 `uploads/image-jobs/{jobId}/inputs/{index}`，使用 UUID 文件名并记录 SHA-256。
6. 增加 mock Adapter，先使运行时测试覆盖完整状态变化，不接真实 HTTP。

建议包结构：

```text
service/image/runtime/
  ImageGenerationRuntime.java
  DefaultImageGenerationRuntime.java
  command/
  model/
  worker/
  storage/
  provider/
```

退出标准：

- 无供应商网络时可提交、执行 mock、产生稳定排序结果。
- 无效输入不会创建 `PENDING` Job。
- 用户不能查询、取消或重试他人的 Job。

### 阶段 3：Worker、租约、恢复与清理

任务：

1. 实现 PostgreSQL `FOR UPDATE SKIP LOCKED` 原子领取。
2. 实现 lease、heartbeat、超时回收、`nextAttemptAt` 指数退避。
3. 按执行阶段实现恢复：已保存 taskId 继续轮询；下载和落盘可重试。
4. 实现 `SUBMISSION_OUTCOME_UNKNOWN`，禁止不确定提交自动重试。
5. 实现 best-effort 取消及条件终态更新。
6. 实现输入清理：成功/部分成功 30 天，失败/取消 7 天，运行中不清理。
7. 增加多 Worker 并发测试、租约丢失测试、服务重启恢复测试。

退出标准：

- 两个 Worker 不会领取同一有效租约任务。
- 崩溃恢复不会覆盖已完成终态或重复落盘。
- 清理任务不删除仍被引用的输入和生成结果。

### 阶段 4：OpenAI 图片 Adapter 等价迁移

任务：

1. 将现有 `/images/generations`、`/images/edits`、Chat Completions fallback 拆入 `OpenAiImageAdapter`。
2. 请求使用解析后的 `remoteModelName`，删除 `MODEL_NAME` 硬编码。
3. Adapter 只返回标准化供应商结果，不写数据库、不落盘、不计量。
4. 运行时统一处理 base64、临时 URL 下载、图片解码、尺寸和文件安全校验。
5. 保留可配置的直连/代理策略；默认行为与当前 `Proxy.NO_PROXY` 兼容。
6. 用特征测试对比旧实现与新 Adapter 的请求和结果。

退出标准：

- OpenAI-compatible 文生图、图生图和 fallback 达到现有功能等价。
- 配置的模型名真实进入请求体。
- 所有第三方错误映射为稳定安全错误码。

### 阶段 5：阿里百炼图片 Adapter

任务：

1. 以阿里云官方文档和已安装 `bailian-cli` 的当前版本为准，确认目标模型、请求字段、异步任务状态、轮询、取消、幂等和结果有效期。
2. 实现 `BailianImageAdapter` 的 capability、submit、poll、cancel。
3. 支持 `TEXT_TO_IMAGE` 与 `IMAGE_TO_IMAGE`；参考图按 input role/order 映射。
4. 保存供应商 task token 后再进入轮询；原始响应只保留在受限 debug 诊断中且必须脱敏。
5. 增加录制响应 contract tests；真实测试使用专用测试模型和最小图片数量。

退出标准：

- 百炼文生图和图生图真实调用各成功一次。
- 认证失败、限流、超时、任务失败、临时 URL 下载失败均有确定错误映射。
- 真实测试不输出或持久化 API Key。

### 阶段 6：新 HTTP interface 与前端轮询

后端任务：

- `POST /v1/image-jobs` → `202 + jobId`
- `GET /v1/image-jobs/{id}`
- `GET /v1/image-jobs/{id}/results`
- `POST /v1/image-jobs/{id}/cancel`
- `POST /v1/image-jobs/{id}/retry`
- DTO 不返回凭据、完整供应商任务 token、临时 URL 或原始错误体。

前端任务：

1. 新增 `src/api/image-jobs.ts` 和 Job 类型。
2. `ImageGenerationView.vue` 改为提交后轮询；1 秒起步，退避到 3 秒。
3. 页面刷新后恢复当前用户未完成 Job。
4. 展示总体状态、执行阶段、成功/失败数量、取消、人工重试和部分成功结果。
5. 模型列表按 `TEXT_TO_IMAGE` / `IMAGE_TO_IMAGE` 能力筛选，不按 provider/modelType 判断。
6. 增加 Vitest：提交、轮询、刷新恢复、取消竞争、部分成功、错误提示。

退出标准：

- 浏览器关闭或刷新不影响生成。
- 前端不会因长任务持有十分钟 HTTP 请求。
- 终态后停止轮询，迟到响应不覆盖更新后的 Job。

### 阶段 7：迁移调用方与兼容接口

任务：

1. `AmazonImageTaskService` 保存 Job ID，并关联所有输出记录，不再只保存第一条 record ID。
2. `ChatCompletionsController` 图片路径转调运行时；同步兼容需要有明确等待上限，超时返回 Job ID 而不是重复提交。
3. 现有 `/v1/images/generate`、`/edit` 作为兼容 facade 转调运行时，响应增加 deprecation header。
4. 确认 `AssetService`, `GalleryService`, `ImageSuperResolutionService/JobService` 对扩展后的 `ImageGenerationRecord` 保持兼容。
5. 修正图片用量：按 Job、输出和供应商返回 usage 记录，不用旧 `modelType` 推断能力。

退出标准：

- 所有生产调用方通过运行时 interface，不再直接调用供应商实现。
- 素材库、历史、超分和 Amazon 工作流回归通过。

### 阶段 8：切换、观察与删除旧 implementation

切换策略：

1. 使用 `image.runtime.enabled` 功能开关控制新旧路径。
2. 开发/测试环境先开；生产按管理员或用户小流量启用。
3. 观察 Job 成功率、P95 完成时间、租约恢复、重复提交、下载失败和 Adapter 错误分布。
4. 稳定一个发布周期后停止写旧 `api_key` 与 `model_type`。
5. 再经过一个兼容周期后删除旧字段、旧同步 implementation、fallback 配置和废弃接口。

删除前置条件：

- 无调用旧接口的前端版本。
- 所有模型已有能力和凭据迁移记录。
- 无代码读取 `AiModel.apiKey` 或 `AiModel.modelType`。
- 回滚窗口结束且数据库备份验证完成。

## 6. 测试矩阵

### 运行时 interface

- 文生图、图生图、多参考图角色与稳定顺序。
- 模型不存在、禁用、能力缺失、protocol 不匹配。
- 能力级覆盖与模型级默认解析。
- 多图全部成功、部分成功、全部失败。
- 其他用户越权读取、取消、重试。

### Worker

- 多实例抢占、续租、租约过期和恢复。
- 每个执行阶段崩溃后的恢复行为。
- 不确定提交不自动重试。
- 幂等供应商安全重试。
- 取消与完成竞争。

### Adapter contract

- 同步成功、异步提交与轮询。
- URL、base64、大响应体和无效图片。
- 401/403、429、4xx 参数错误、5xx、连接失败、超时。
- 供应商不支持取消或幂等。
- 日志、异常和 DTO 不泄露密钥。

### 数据与文件

- Flyway 空库、现有库升级和回滚备份演练。
- 凭据加密、错误主密钥、轮换和引用保护。
- 输入 MIME、实际解码、大小、像素、路径穿越、重复引用。
- TTL 清理和生成结果保留。

### 前端

- 能力筛选、能力级配置表单。
- Job 提交、轮询退避、刷新恢复、终态停止。
- 部分成功、取消提示、不确定提交与人工重试。
- 旧历史、素材上传、超分入口和 Amazon 工作台。

## 7. 可观测性

指标：

- Job 数量与状态分布。
- 排队时间、供应商执行时间、下载/落盘时间、总耗时 P50/P95/P99。
- Adapter/模型/能力维度成功率与错误码。
- 重试、租约恢复、`SUBMISSION_OUTCOME_UNKNOWN`、取消不支持次数。
- 生成张数、部分成功数和用量成本。

日志字段：

- `jobId`, `outputIndex`, `userId`, `modelId`, `provider`, `protocol`, `phase`, `attempt`。
- 不记录 prompt 全文、API Key、完整供应商 token、临时 URL 或原始响应。

## 8. 回滚策略

- 所有数据库变更先 additive，兼容期不删除旧字段。
- `image.runtime.enabled=false` 可将新提交切回旧路径；已创建 Job 仍由 Worker 完成或安全取消。
- Adapter 切换与前端切换分开发版。
- 凭据迁移期间保持加密字段优先、旧字段只读 fallback；确认回滚窗口结束后才清除明文。
- 迁移前备份 `ai_models`, `image_generation_records`, Amazon 图片任务相关表和上传目录。

## 9. 建议提交拆分

1. `build: introduce Flyway baseline for PostgreSQL`
2. `feat: add model capabilities and encrypted credentials`
3. `feat: add image generation job domain and input snapshots`
4. `feat: add leased image generation worker`
5. `refactor: extract OpenAI image provider adapter`
6. `feat: add Aliyun Bailian image provider adapter`
7. `feat: expose image job endpoints and polling UI`
8. `refactor: migrate image generation callers to runtime`
9. `chore: remove legacy image generation compatibility path`

每个提交必须包含对应测试；数据库字段删除和旧接口删除不得与新增路径放在同一提交。

## 10. 实施起点

先执行阶段 0，不直接从百炼 HTTP 调用开始。第一条可验证纵向切片应为：

```text
POST /v1/image-jobs
  → 保存 PENDING Job 与输入快照
  → mock Adapter 完成一个 TEXT_TO_IMAGE 输出
  → Worker 落盘并写 ImageGenerationRecord
  → GET status/results 返回稳定结果
  → 前端轮询展示
```

该切片通过后，再以相同 interface 替换 mock 为 OpenAI 和百炼 Adapter。
