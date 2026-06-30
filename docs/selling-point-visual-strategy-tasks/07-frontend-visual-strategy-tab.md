# 任务 07：前端视觉策略 Tab

## 目标

在产品资料详情页增加“视觉策略”Tab，支持生成、编辑、确认副图/A+策略。

## UI 能力

- 选择生成范围：副图、A+、全套。
- 展示 6 张副图卡片。
- 展示 6 个 A+ 模块卡片。
- 编辑 prompt、text overlays、negative constraints。
- 复制英文 prompt。
- 确认版本。

## 验收标准

- `npm run build` 通过。
- gallery/aplus scope 展示正确。
- 长 prompt 可编辑，不撑破布局。
## 实施记录

状态：已完成

已新增/修改文件：

- `ShadcnAgentUI/src/api/product-profiles.ts`
- `ShadcnAgentUI/src/views/product/ProductProfileDetailView.vue`

已实现能力：

- 产品资料详情页新增 `视觉策略` Tab。
- 支持选择生成范围：Gallery + A+、Gallery only、A+ only。
- 支持生成 visual strategy DRAFT。
- 支持加载当前 visual strategy version 和版本列表。
- 支持展示 6 张副图卡片。
- 支持展示 6 个 A+ 模块卡片。
- 支持编辑 Gallery 的 headline/subhead、visual structure、prompt。
- 支持编辑 A+ 的 headline、body copy、image prompt。
- 支持展示 global constraints 和 claims to avoid。
- 支持复制英文 prompt。
- 支持保存 DRAFT。
- 支持确认 visual strategy version。
- 支持高级 JSON 编辑并回填结构化区域。
- gallery/aplus scope 按返回 JSON 条件展示。
- 长 prompt 使用 textarea 编辑，避免撑破布局。

验证命令：

```bash
npm run build
```

验证结果：通过。

备注：build 输出中仍有既有的 `@vueuse/core` PURE annotation warning，不影响构建成功。