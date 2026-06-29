# 任务 01：后端模型与存储基础

## 目标

为卖点认知地图和视觉策略建立版本化存储基础，后续服务/API 都基于这两个版本实体工作。

## 范围

新增：

- `ProductSellingPointCognitionVersion`
- `ProductVisualStrategyVersion`
- `ProductSellingPointCognitionVersionRepository`
- `ProductVisualStrategyVersionRepository`

不做：

- 不生成认知 JSON。
- 不生成视觉策略 JSON。
- 不新增 Controller。
- 不新增前端页面。

## 数据模型

### ProductSellingPointCognitionVersion

字段：

- `id`
- `profileId`
- `profileVersionId`
- `versionNumber`
- `status`
- `cognitionJson`
- `sourceFactsHash`
- `createdBy`
- `createdAt`
- `confirmedBy`
- `confirmedAt`

### ProductVisualStrategyVersion

字段：

- `id`
- `profileId`
- `profileVersionId`
- `cognitionVersionId`
- `versionNumber`
- `status`
- `contentScope`
- `strategyJson`
- `createdBy`
- `createdAt`
- `confirmedBy`
- `confirmedAt`

## Repository 方法

两个 repository 都需要：

- 按 `profileId` 倒序查询版本列表。
- 查找某产品当前最新版本。
- 按 `profileId` 统计版本数量。

视觉策略 repository 额外需要：

- 按 `cognitionVersionId` 查询策略。

## 验收标准

- 后端 `compileJava` 通过。
- 新实体 package、表名、字段命名与现有 `ProductProfileVersion` 风格一致。
- 不破坏现有产品资料测试。
## 实施记录

状态：已完成

已新增文件：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/model/ProductSellingPointCognitionVersion.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/model/ProductVisualStrategyVersion.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/repository/ProductSellingPointCognitionVersionRepository.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/repository/ProductVisualStrategyVersionRepository.java`

验证命令：

```bash
gradle --no-daemon compileJava
gradle --no-daemon test --tests cafe.snails.ecomagents.service.ProductProfileServiceParseTest
```

验证结果：通过。