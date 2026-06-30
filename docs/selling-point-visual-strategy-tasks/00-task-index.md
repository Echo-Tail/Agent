# 卖点翻译与视觉策略开发任务拆分

来源规格：`docs/selling-point-visual-strategy-spec-2026-06-26.md`

## 任务顺序

1. `01-backend-model-storage.md`：后端模型与存储基础。
2. `02-cognition-generation-service.md`：卖点认知生成服务。
3. `03-cognition-api.md`：卖点认知 API。
4. `04-visual-strategy-service.md`：视觉策略生成服务。
5. `05-visual-strategy-api.md`：视觉策略 API。
6. `06-frontend-cognition-tab.md`：产品详情页卖点认知 Tab。
7. `07-frontend-visual-strategy-tab.md`：产品详情页视觉策略 Tab。
8. `08-end-to-end-validation.md`：真实样本闭环验证与回归测试。

## 依赖关系

```text
01 -> 02 -> 03 -> 04 -> 05 -> 06/07 -> 08
```

## 开发原则

- 两阶段流程：先认知确认，再视觉策略生成。
- 每次生成默认创建新版本，不覆盖已确认版本。
- 第一版不接图像生成 API。
- 第一版只支持 car stereo / vehicle head unit。
- JSON 内容保存为快照，复杂 diff 后续再做。