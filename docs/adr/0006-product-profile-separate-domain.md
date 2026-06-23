# 产品资料作为独立领域，与商品图生成任务拆分

## Status

Accepted — 2026-07-XX

## Context

商品图自动生成器需要管理两类性质完全不同的数据：

1. **产品资料（Product Profile）** — 自有产品的真实参数、功能、配件和兼容性信息。是长期资产，需要版本化、多轮人工确认、跨任务复用。生命周期独立于任何一次生成任务。
2. **商品图生成任务（Image Task）** — 一次性的工作流实例，围绕某个已确认的产品资料执行素材采集、表达分析、prompt 编辑和图片生成。任务结束后保留生成结果，但产品资料的生命周期远长于单个任务。

初始实现的 `AmazonImageTask` 单表设计将产品事实（`productFactsJson`）直接嵌入任务实体，通过 Bright Data 回填竞品 ASIN 信息作为产品事实来源，没有独立的产品资料库和版本控制。

## Constraints

- 产品事实不能混入竞品素材事实（避免生成误导图）
- 同一个产品需要支持多次生成任务引用
- 产品资料需要支持版本化，生成任务引用确认版本的快照
- 不允许修改已确认版本（避免历史任务的产品事实被静默变更）
- 与现有 SSOT 体系一致：DB `ProductProfile` 表为主存储，`ProductProfileVersion` 为快照

## Considered Options

### 1. 保持单表设计（拒绝）

将产品事实保留在 `AmazonImageTask.productFactsJson` 中，新增 `productProfileId` 字段引用外部产品资料表。

- **优点**：改动最小，现有数据无需迁移
- **缺点**：产品事实与任务耦合过紧，无法跨任务复用；缺少版本化语义；多任务引用同一产品时重复录入；升级产品参数时无法追溯哪些任务用了旧版
- **拒绝原因**：PRD 明确将产品资料定义为长期资产，单表方案违背了"产品资料生命周期独立于任务"的核心域要求

### 2. 独立领域 + 版本化快照（选定）

新建 `ProductProfile` 和 `ProductProfileVersion` 实体，前端独立路由 `/agents/products`。`AmazonImageTask` 通过 FK 引用 `ProductProfileVersion`。

- **优点**：产品资料可跨任务复用；版本化支持历史追溯和升级；产品事实不混入素材事实，域边界清晰；符合 PRD 架构
- **缺点**：需要新建两张表 + CRUD 服务 + 前端路由；已有任务的 `productFactsJson` 需要迁移到新表（可通过一次性数据迁移脚本处理）

## Decision

采用独立领域方案：新建 `ProductProfile` 表存储自有产品条目，`ProductProfileVersion` 表存储每次人工确认后的目标产品事实快照。

生成任务创建时必须选择一个已确认的 `ProductProfileVersion`，任务通过 `profileVersionId` 引用固定版本。

阶段 1 版本 UI 只显示当前任务版本和"升级到最新版本"按钮，不做完整版本历史视图。

## Consequences

- **正面**：产品资料可跨任务复用，一次确认多次使用；版本快照保证历史生成结果可追溯；产品事实与素材事实天然分离
- **负面**：需要数据迁移脚本将现有 `amazon_image_tasks.productFactsJson` 迁移到新表（约 0 条数据，因为该功能尚未正式上线）
- **风险**：如果未来产品资料的变更需要自动重生成所有关联任务的历史图片，需额外实现重生成检查逻辑——但阶段 1 不做此功能
