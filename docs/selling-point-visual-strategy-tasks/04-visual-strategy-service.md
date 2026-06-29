# 任务 04：视觉策略生成服务

## 目标

新增 `VisualStrategyService`，基于已确认的卖点认知版本生成副图/A+策略草稿。

## 主要能力

- 只允许基于 CONFIRMED cognition version 生成。
- 支持 `content_scope`: `gallery`、`aplus`、全套。
- 固定 6 张副图结构。
- 固定 6 个 A+ 模块结构。
- 生成中英 prompt 和 text overlays。
- 保存 DRAFT visual strategy version。
- 查询、更新、确认版本。

## 验收标准

- 未确认 cognition 不允许生成。
- scope 为 gallery 时不生成 A+。
- scope 为 aplus 时不生成副图。
- 默认全套。
- 重新生成创建新版本。
## 实施记录

状态：已完成

已新增文件：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/VisualStrategyService.java`
- `EcomAgents/src/test/java/cafe/snails/ecomagents/service/VisualStrategyServiceTest.java`

已实现能力：

- 基于 CONFIRMED cognition version 生成 DRAFT visual strategy version。
- cognitionVersionId 为空时自动使用当前产品最新 CONFIRMED cognition version。
- 支持 `content_scope`: `gallery`、`aplus`、默认全套。
- 固定生成 6 张副图结构。
- 固定生成 6 个标准 A+ 模块结构。
- 输出中英 prompt 和 `text_overlays`。
- 继承 `global_constraints` 和 `claims_to_avoid`。
- 支持查询当前版本、版本列表。
- 支持更新 DRAFT strategy JSON。
- 支持确认版本。
- 重新生成按 profile 递增版本号，不覆盖旧版本。

验证命令：

```bash
gradle --no-daemon compileJava
gradle --no-daemon test --tests cafe.snails.ecomagents.service.VisualStrategyServiceTest --tests cafe.snails.ecomagents.service.SellingPointCognitionServiceTest --tests cafe.snails.ecomagents.service.ProductProfileServiceParseTest
```

验证结果：通过。