# 图片生成运行时部署与数据库升级

本文说明图片生成 Job Runtime 上线所需的 PostgreSQL 迁移、配置、持久化目录和验证步骤。

## 1. 上线前检查

- Java 17、PostgreSQL，以及可写的持久化上传目录。
- 备份数据库，并确保备份可以恢复。
- 所有实例使用相同的 `MODEL_CREDENTIAL_MASTER_KEY`，且该值不会在重启或扩容时变化。
- 多实例部署时共享 PostgreSQL；上传目录也必须使用共享存储，或确保任务始终能访问创建时保存的输入快照。
- 生产环境禁止启用 Mock Adapter。

可从 [`EcomAgents/.env.example`](../EcomAgents/.env.example) 复制变量清单。应用不会自动读取 `.env` 文件，应由 systemd、容器平台或部署系统注入环境变量。

## 2. 主密钥和凭据

生成 32 字节随机主密钥，并以 Base64 保存：

```bash
openssl rand -base64 32
```

将结果设置为 `MODEL_CREDENTIAL_MASTER_KEY`。此密钥使用 AES-256-GCM 加密模型凭据：

- 不要提交到 Git、镜像或日志。
- 丢失密钥后，数据库中的供应商密钥无法恢复，只能重新录入。
- 不要直接替换主密钥；先通过凭据轮换流程重新加密数据，再切换所有实例。
- 旧 `ai_models.api_key` 不会由 SQL 自动加密迁移。升级后应在模型管理中重新保存凭据并绑定能力配置，确认运行正常后再另行清除旧明文字段。

## 3. Flyway 迁移

启动应用时会按顺序执行：

| 版本 | 内容 |
| --- | --- |
| V1 | 为已有数据库建立 Flyway 基线，并支持新库创建最小 `ai_models` 表 |
| V2 | 模型能力、加密凭据和默认凭据关联 |
| V3 | 图片 Job、输入快照元数据、历史结果关联 |
| V4 | Amazon 图片任务与 Job/生成记录关联 |

已有非空数据库通过 `baseline-on-migrate=true` 标记为版本 1，再执行 V2 至 V4。不要修改已经在某个环境执行过的迁移文件，否则 Flyway 校验会因 checksum 不一致而拒绝启动。

部署步骤：

1. 停止旧版本写入，完成数据库备份。
2. 使用与生产相同的数据库副本预演启动，确认 V2 至 V4 成功。
3. 部署新版本并观察启动日志中的 Flyway 校验和迁移结果。
4. 查询 `flyway_schema_history`，确认最新成功版本为 4。
5. 登录管理员页面，重新保存供应商凭据与模型能力。
6. 提交一条文生图和一条图生图任务，确认 Job、结果文件和监控数据正常。

当前项目的旧业务表仍由 Hibernate `ddl-auto=update` 管理，因此生产配置暂时保留 `update`。在所有旧表都纳入 Flyway 前，不要直接改成 `validate`；完成全量基线后应切换为 `validate`。

## 4. 必填生产配置

| 环境变量 | 说明 |
| --- | --- |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | PostgreSQL 连接 |
| `JWT_SECRET` | JWT 签名密钥，至少 32 字节随机值 |
| `MODEL_CREDENTIAL_MASTER_KEY` | Base64 编码的 32 字节 AES 主密钥 |
| `CORS_ALLOWED_ORIGINS` | 前端正式域名，多个值以逗号分隔 |
| `FILE_UPLOAD_DIR` | 输入快照和生成结果的持久化根目录 |

Worker 参数及默认值见 `.env.example`。建议满足：

- `heartbeat-interval` 小于 `lease-seconds`，通常不超过租约的三分之一。
- 百炼超时覆盖模型正常生成时长；当前默认 600 秒。
- `worker-concurrency` 按供应商并发配额和机器网络能力逐步增加。
- 多实例的 `IMAGE_RUNTIME_WORKER_ID` 必须唯一；留空时会自动生成，但不利于日志追踪。

输入快照清理任务默认每天 03:30 执行：成功任务保留 30 天，失败或取消任务保留 7 天。生成结果记录不由该清理器删除。

## 5. 文件与网络

`FILE_UPLOAD_DIR` 下的 `image-jobs/{jobId}/inputs` 和 `outputs` 必须持久化。反向代理需要允许访问应用暴露的 `/uploads/**`，并允许后端访问供应商 API 和供应商返回的图片地址。

不要将供应商 API Key 单独设置为全局环境变量；通过管理员模型管理页面写入加密凭据。阿里百炼模型可使用各 Workspace 独立的兼容地址，不能强制替换成公共默认地址。

## 6. 健康检查和验收

无需认证的存活检查：

```text
GET /actuator/health
```

`/actuator/metrics`、`/actuator/prometheus` 和 `/v1/admin/image-runtime/**` 需要 ADMIN JWT。指标与告警示例见 [`image-runtime-observability.md`](image-runtime-observability.md)。

最低验收项：

1. 应用启动且 Flyway 无错误。
2. 创建或轮换模型凭据后能再次解密使用。
3. `/v1/image-jobs` 文生图、图生图均能完成。
4. 重启 Worker 后，过期租约任务能够恢复。
5. 输入与输出文件在重启后仍可访问。
6. 管理员监控页面显示成功率、耗时、错误和超时。

## 7. 回滚

Flyway 迁移为向前兼容的新增表/新增列，应用回滚时不要直接删除它们：

1. 停止新版本 Worker，避免继续领取任务。
2. 等待运行中任务结束，或在管理端取消任务。
3. 回滚应用版本，但保留 V2 至 V4 的表和列。
4. 如必须恢复数据库，使用上线前备份整体恢复，而不是手工执行 `DROP TABLE`。

旧同步生图接口已经移除，回滚应用并不会让新客户端自动切回旧接口；前后端版本必须配套发布。
