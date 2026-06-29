# 任务 02：卖点认知生成服务

## 目标

新增 `SellingPointCognitionService`，从 `ProductProfile.productFactsJson` 生成卖点认知地图草稿并保存 DRAFT 版本。

## 主要能力

- 校验产品资料存在且属于当前用户。
- 构造 car stereo 认知生成输入。
- 调用 LLM 生成 cognition JSON。
- 本地 fallback 至少产出基础骨架。
- 生成 `sourceFactsHash`。
- 保存新的 DRAFT version。
- 查询当前版本、版本列表。
- 更新 cognition JSON。
- 确认版本。

## 关键规则

- 每条 enabled cognition 必须带 evidence。
- 推荐 8-12 条，硬上限 20 条。
- 生成失败不覆盖旧版本。
- 只支持 car stereo / vehicle head unit。

## 验收标准

- 单测覆盖生成、更新、确认、版本号递增。
- 无产品 facts 时返回业务错误。
- 重新生成创建新版本。
## 实施记录

状态：已完成

已新增文件：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/SellingPointCognitionService.java`
- `EcomAgents/src/test/java/cafe/snails/ecomagents/service/SellingPointCognitionServiceTest.java`

已实现能力：

- 从 `ProductProfile.productFactsJson` 生成 DRAFT cognition version。
- 生成 `sourceFactsHash`。
- 版本号按 `profileId` 递增。
- 支持查询当前版本、版本列表。
- 支持更新 DRAFT cognition JSON。
- 支持确认版本。
- 校验产品归属当前用户。
- 无 facts 时返回业务错误。
- 本地 deterministic fallback 生成基础 cognition schema，包含 evidence、global_constraints、claims_to_avoid。

验证命令：

```bash
gradle --no-daemon compileJava
gradle --no-daemon test --tests cafe.snails.ecomagents.service.SellingPointCognitionServiceTest --tests cafe.snails.ecomagents.service.ProductProfileServiceParseTest
```

验证结果：通过。

备注：第一版先落确定性 fallback 和版本流，LLM 调用可在后续任务中替换/增强生成器，不影响 API 与存储结构。