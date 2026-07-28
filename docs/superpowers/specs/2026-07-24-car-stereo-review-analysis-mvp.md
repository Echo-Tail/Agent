# Car Stereo 评论洞察 MVP 设计

日期：2026-07-27
状态：Confirmed

## 1. 产品目标

用户输入一个或多个 Amazon US Car Stereo ASIN 后，系统自动完成评论采集、清洗、分析和报告生成：

```text
ASIN → 评论原文 → 用户问题 → 使用场景 → 产品模块
     → 严重程度 → 改进动作 → 商业价值
```

系统的核心价值不是统计好评和差评，而是生成有原文证据支撑、用户可以自行判断取舍的产品改进机会。

## 2. 核心体验

用户唯一必填信息是 ASIN：

```text
粘贴多个 ASIN 或商品链接 → 点击开始分析 → 查看洞察报告和评论证据
```

以下能力由系统自动处理，不作为用户配置项：

- Amazon US 站点与 Car Stereo 类目
- 每个 ASIN 默认采集 200 条评论
- Bright Data 数据集与采集参数
- 默认启用的文本模型
- 固定版本的中文分析提示词
- 每批最多 50 条评论
- 数据去重、失败隔离和检查点重试
- 问题聚合与优先级计算

分析任务不关联 `ProductProfile`，多个 ASIN 对等分析，不要求用户区分本品和竞品。

## 3. 页面结构

### 3.1 分析任务

- 通过弹窗中的一个文本框批量粘贴 ASIN、商品链接或包含 ASIN 的文本
- 自动识别和去重，不限制 ASIN 数量
- 一键启动自动采集和分析
- 展示统一进度，不暴露快照、模型、提示词或批次
- 查看历史任务
- 失败后提供“重新分析”

### 3.2 洞察报告

- 评论总数、平均评分、发现问题数、改进机会数
- 严重程度、使用场景和产品模块分布
- 按影响排序的改进机会
- 每个机会展示中文问题、中文建议、影响评论数和优先级
- 最终是否采纳由用户自行判断，MVP 不要求编辑实施成本或确认版本

### 3.3 评论证据

- 中文用户问题
- 中文改进建议
- 中文分类标签
- 英文评论原文及英文证据片段
- ASIN、评分和置信度
- 支持关键词搜索

## 4. MVP 分析范围

第一版保留六组核心能力：

1. 基础口碑：评分、低星评论和基础趋势。
2. 产品模块：安装适配、CarPlay、Android Auto、蓝牙、音频、屏幕、摄像头和系统稳定性。
3. 用户问题：具体表现、使用场景和 P0～P3 严重程度。
4. 正向卖点：用户明确认可的产品体验。
5. 风险信号：退货意图、安全风险和核心功能不可用。
6. 改进机会：影响范围、建议动作、商业影响和评论证据。

车型、年份和适配信息可以在评论明确提供时提取，但不能推测。复杂根因推断、负责团队、研发排期、自动生成 PRD 和 Listing 建议不进入 MVP。

## 5. 数据与语言原则

- 必须保存评论原始字段和 Bright Data 原始 JSON。
- 每条洞察必须关联一条原始评论。
- `evidence_quote` 必须是评论原文的精确子串。
- 评论标题、正文和证据保持原语言。
- 用户问题、改进动作、机会标题、商业说明统一使用简体中文。
- 一条评论可以拆分为多条原子洞察。
- 没有可执行问题的正面评论不得虚构缺陷。
- 信息不足时降低置信度，不得推测车型、根因或用户意图。

## 6. 内部业务对象

### ReviewAnalysisProject

代表一次 ASIN 分析任务，保存创建用户、状态和时间。`profile_id` 可为空，不再承担产品资料关联。

### ReviewProjectProduct

保存任务中的全部 ASIN。新任务统一使用 `role=product`，默认 `review_limit=200`。

### ProductReview

不可变评论证据，使用 Amazon 评论 ID 或内容哈希幂等去重。

### ReviewAnalysisRun

保存模型、taxonomy 和 prompt 版本，供内部重放与排障。用户无需选择或确认版本。

### ReviewInsight

保存一条评论中的一个原子问题及其原文证据。

### ImprovementOpportunity

聚合相似问题形成可排序的改进机会。优先级是参考信息，不替代用户决策。

## 7. 自动工作流

```text
draft
  → collecting
  → collected
  → analyzing
  → review
```

前端创建任务后立即启动采集；采集完成后自动启动分析。页面重新打开时通过最近的采集批次和分析运行恢复进度。

失败只向用户展示“重新分析”，内部继续使用批次失败隔离和评论级检查点。

## 8. API

```text
POST /v1/review-analysis/projects
Body: {"asins":["Bxxxxx","Bxxxxx"]}

POST /v1/review-analysis/projects/{projectId}/collections
POST /v1/review-analysis/projects/{projectId}/analysis-runs
GET  /v1/review-analysis/projects/{projectId}/analysis-runs/{runId}/dashboard
GET  /v1/review-analysis/projects/{projectId}/analysis-runs/{runId}/opportunities
GET  /v1/review-analysis/projects/{projectId}/analysis-runs/{runId}/insights
```

启动分析接口不接收模型、提示词和批次大小，服务端使用默认文本模型、固定提示词和 50 条批次上限。

## 9. 暂不进入用户流程

- ProductProfile 关联
- 本品与竞品角色选择
- 模型与角色提示词选择
- 采集和分析两个独立操作
- 实施成本编辑
- 洞察人工修正
- 分析版本确认
- 人工抽样验收与放行门槛
- 自动创建 PRD、工单或研发任务

## 10. 验收标准

- 用户只输入有效 ASIN 即可启动完整流程。
- 每次 LLM 请求最多包含 50 条评论。
- 页面刷新后可以恢复正在进行的任务。
- 重复采集不会创建重复评论。
- 每条洞察均可回溯到英文评论原文。
- 分析结论和改进机会默认使用简体中文。
- 用户无需了解或配置模型、提示词、批次和 ProductProfile。
- 采集或分析失败可以通过一个“重新分析”入口恢复。
