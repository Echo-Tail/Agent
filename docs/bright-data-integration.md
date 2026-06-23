# Bright Data API 集成方案

## 概述

在后端代理 Bright Data Web Scraper API，隐藏 API Key，统一响应格式，
并将每次调用记录落库以便追溯和分析。

## 代理的 Bright Data 端点

| 后端 Service 方法 | Bright Data 端点 | 说明 |
|---|---|---|
| `scrape()` | `POST /datasets/v3/scrape` | 同步抓取，1min 超时，超时降级为异步返回 snapshot_id |
| `trigger()` | `POST /datasets/v3/trigger` | 异步触发，立即返回 snapshot_id |
| `getProgress()` | `GET /datasets/v3/progress/{snapshot_id}` | 轮询快照进度 |
| `downloadSnapshot()` | `GET /datasets/v3/snapshot/{snapshot_id}` | 下载完成的数据 |
| `cancelSnapshot()` | `POST /datasets/v3/snapshot/{snapshot_id}/cancel` | 取消进行中的任务 |
| `listSnapshots()` | `GET /datasets/v3/snapshots` | 列出历史快照 |

## 设计决策

| 决策 | 选择 |
|---|---|
| 认证 | 所有已登录用户可调用，JWT 鉴权 |
| 响应格式 | 结构化 JSON 再包装 |
| 异步轮询 | 前端自行轮询 `progress/{snapshotId}`，超时时间前端指定，默认 5min |
| include_errors | 默认 true |
| 分片下载 | 暂不支持 batch_size/part |
| 数据落库 | 每次调用写入 `bright_data_records` 表 |

## 数据库模型

### bright_data_records

```sql
CREATE TABLE bright_data_records (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    type            VARCHAR(20) NOT NULL,        -- 'scrape' | 'trigger'
    dataset_id      VARCHAR(100),
    snapshot_id     VARCHAR(100),                -- 异步时有值
    status          VARCHAR(20) NOT NULL,        -- success | failed | running | ready
    asin_list       TEXT,                        -- 从输入 URL 中提取的 ASIN 列表，JSON 数组
    request_params  TEXT,                        -- 完整请求参数 JSON
    result_summary  TEXT,                        -- 结果摘要
    dataset_size    INT,                         -- 返回记录数
    time_cost_ms    BIGINT,                      -- 接口耗时（毫秒）
    error_message   TEXT,                        -- 失败原因
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bdr_user_id ON bright_data_records(user_id);
CREATE INDEX idx_bdr_snapshot_id ON bright_data_records(snapshot_id);
CREATE INDEX idx_bdr_status ON bright_data_records(status);
CREATE INDEX idx_bdr_created_at ON bright_data_records(created_at DESC);
```

### JPA 实体字段映射

| Java 字段 | 列名 | 类型 | 说明 |
|---|---|---|---|
| id | id | Long (PK, auto) | 自增主键 |
| userId | user_id | Long | 操作人 |
| type | type | String(20) | scrape / trigger |
| datasetId | dataset_id | String(100) | 数据集 ID |
| snapshotId | snapshot_id | String(100) | 快照 ID |
| status | status | String(20) | success / failed / running / ready |
| asinList | asin_list | TEXT | ASIN 列表 JSON |
| requestParams | request_params | TEXT | 请求参数 JSON |
| resultSummary | result_summary | TEXT | 结果摘要 |
| datasetSize | dataset_size | Integer | 数据量 |
| timeCostMs | time_cost_ms | Long | 耗时 ms |
| errorMessage | error_message | TEXT | 错误信息 |
| createdAt | created_at | LocalDateTime | 创建时间 |
| updatedAt | updated_at | LocalDateTime | 更新时间 |

## Service 层方法签名

```java
// 同步抓取 — 解析成结构化 JSON 返回，自动落库
ApiResponse<BrightDataScrapeResponse> scrape(BrightDataScrapeRequest req, Long userId);

// 异步触发 — 立即返回 snapshot_id，自动落库
ApiResponse<BrightDataTriggerResponse> trigger(BrightDataTriggerRequest req, Long userId);

// 查询快照进度
ApiResponse<BrightDataSnapshotStatus> getProgress(String snapshotId);

// 下载快照数据
ApiResponse<Object> downloadSnapshot(String snapshotId, String format);

// 取消快照
ApiResponse<Void> cancelSnapshot(String snapshotId);

// 列出 Bright Data 侧快照
ApiResponse<List<Map<String, Object>>> listSnapshots(String datasetId, String status, Integer limit);

// 查询本地记录
ApiResponse<Page<BrightDataRecord>> listRecords(Long userId, int page, int size);
```

## 调用流程

### 同步抓取
```
Client → scrape(req) → [record:status=running]
                     → POST /datasets/v3/scrape (BD)
                     → 200: parse JSON → [record:status=success, records]
                     → 202: get snapshot_id → [record:status=running, snapshot_id]
                     → return response
```

### 异步触发
```
Client → trigger(req) → [record:status=running]
                      → POST /datasets/v3/trigger (BD)
                      → get snapshot_id → [record:snapshot_id]
                      → return {snapshot_id}
Client → poll getProgress(snapshot_id) each N seconds
                      → GET /datasets/v3/progress/{snapshot_id} (BD)
                      → return status
Client → when status=ready: downloadSnapshot(snapshot_id)
                      → GET /datasets/v3/snapshot/{snapshot_id} (BD)
                      → return data
```

## ASIN 提取规则

从 input 中每个 URL 字段提取 Amazon ASIN：
- 正则: `/(?:dp|product|gp/product)/([A-Z0-9]{10})`
- 提取结果存入 `asin_list` 字段（JSON 字符串数组）

## 配置项

```properties
brightdata.api-key=                # Bright Data API Key（必填）
brightdata.base-url=https://api.brightdata.com
brightdata.default-dataset-id=     # 默认数据集 ID（可选）
```

## 文件清单

| 文件 | 说明 |
|---|---|
| `config/BrightDataConfig.java` | 配置属性绑定 |
| `model/BrightDataRecord.java` | JPA 实体 |
| `repository/BrightDataRecordRepository.java` | JPA Repository |
| `dto/BrightDataScrapeRequest.java` | 同步抓取请求 |
| `dto/BrightDataScrapeResponse.java` | 同步抓取响应 |
| `dto/BrightDataTriggerRequest.java` | 异步触发请求 |
| `dto/BrightDataTriggerResponse.java` | 异步触发响应 |
| `dto/BrightDataSnapshotStatus.java` | 快照状态 |
| `service/BrightDataService.java` | 服务层实现 |
