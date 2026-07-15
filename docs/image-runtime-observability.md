# 图片生成运行时监控

图片运行时通过 Spring Boot Actuator 暴露 Micrometer 指标。健康检查位于
`GET /actuator/health`；指标端点 `GET /actuator/prometheus` 需要 ADMIN JWT。

## 核心指标

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `ecomagents_image_runtime_jobs` | Gauge | 按状态统计当前任务数，可观察排队和运行中任务 |
| `ecomagents_image_runtime_worker_active` | Gauge | 当前实例正在执行的任务数 |
| `ecomagents_image_runtime_jobs_completed_total` | Counter | 按供应商、协议、能力、模式和结果统计终态任务 |
| `ecomagents_image_runtime_jobs_duration_seconds` | Histogram | Job 端到端耗时 |
| `ecomagents_image_runtime_provider_duration_seconds` | Histogram | 供应商 submit、poll、cancel 请求耗时 |
| `ecomagents_image_runtime_provider_errors_total` | Counter | 供应商错误，`category` 为 `error` 或 `timeout` |
| `ecomagents_image_runtime_timeouts_total` | Counter | 按供应商和执行阶段统计超时 |
| `ecomagents_image_runtime_retries_total` | Counter | 自动重试调度次数 |
| `ecomagents_image_runtime_jobs_recovered_total` | Counter | 租约过期后恢复的任务数 |
| `ecomagents_image_runtime_jobs_cancellations_finalized_total` | Counter | Worker 恢复流程完成的取消任务数 |

所有标签均为有限枚举或已配置的供应商值，禁止加入 `userId`、prompt、Job ID、模型名称等高基数标签。

## 推荐 PromQL

```promql
# 10 分钟失败率
sum(rate(ecomagents_image_runtime_jobs_completed_total{outcome="failed"}[10m]))
/
clamp_min(sum(rate(ecomagents_image_runtime_jobs_completed_total[10m])), 0.001)

# Job P95 耗时
histogram_quantile(0.95,
  sum by (le, provider, capability) (rate(ecomagents_image_runtime_jobs_duration_seconds_bucket[10m])))

# 供应商 P95 请求耗时
histogram_quantile(0.95,
  sum by (le, provider, operation) (rate(ecomagents_image_runtime_provider_duration_seconds_bucket[10m])))

# 10 分钟超时增量
sum by (provider, phase) (increase(ecomagents_image_runtime_timeouts_total[10m]))

# 当前排队任务
sum(ecomagents_image_runtime_jobs{status="pending"})
```

建议初始告警阈值：10 分钟失败率超过 20%、10 分钟同供应商超时达到 3 次、排队任务持续 10 分钟超过 20、P95 Job 耗时超过业务 SLA。上线运行一周后按真实基线调整。
