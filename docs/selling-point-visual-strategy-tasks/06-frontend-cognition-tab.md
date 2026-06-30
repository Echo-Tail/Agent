# 任务 06：前端卖点认知 Tab

## 目标

在产品资料详情页增加“卖点认知”Tab，支持生成、编辑、确认 cognition version。

## UI 能力

- 生成认知地图草稿。
- 展示 cognition 列表。
- 编辑 enabled、priority、type、visual_model、中英认知、证据、风险备注。
- 展示 global_constraints 和 claims_to_avoid。
- 保存草稿。
- 确认版本。

## 验收标准

- `npm run build` 通过。
- 空状态、加载态、错误态完整。
- 不把 JSON 编辑作为唯一交互，至少提供结构化区域。
## 实施记录

状态：已完成

已新增/修改文件：

- `ShadcnAgentUI/src/api/product-profiles.ts`
- `ShadcnAgentUI/src/views/product/ProductProfileDetailView.vue`

已实现能力：

- 产品资料详情页新增 `卖点认知` Tab。
- 支持生成 cognition draft。
- 支持加载当前 cognition version 和版本列表。
- 支持结构化编辑 buyer cognitions：enabled、priority、type、visual_model、feature、中英 buyer cognition。
- 支持展示 evidence 和 risk notes。
- 支持编辑 `global_constraints` 和 `claims_to_avoid`。
- 支持保存 DRAFT。
- 支持确认 cognition version。
- 支持高级 JSON 编辑并回填结构化表单。
- 空状态、加载态、错误态已覆盖。

验证命令：

```bash
npm run build
```

验证结果：通过。

备注：build 输出中仍有既有的 `@vueuse/core` PURE annotation warning，不影响构建成功。