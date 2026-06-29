# 卖点认知生成 LLM 接入计划

日期：2026-06-29
状态：计划确认稿
基于：`docs/selling-point-visual-strategy-spec-2026-06-26.md` Section 15

## 背景

当前 `SellingPointCognitionService` 使用纯规则引擎（keyword 匹配 + 硬编码模板）生成买家认知。
规格文档 Section 15 已明确要求 LLM 参与，Phase 2 原本就包含"增加 LLM prompt 和 fallback 最小逻辑"。

现状评价（详见经验评估）：
- `classify()` keyword 匹配只能做粗分类，无法理解语义
- `cognitionFor()`/`painPointFor()`/`beliefFor()` 全是通用模板，不基于产品实际数据
- `scene_cn`/`scene_en` 硬编码为 `"Real in-car usage scenario."`，无信息量
- 三条重要数据源（`title`、`features`、`selling_points`）完全未使用

## 接入策略（三阶段）

### Phase 1：文案生成引擎替换（低风险，高回报）

**范围**：只替换 `cognitionFor()`、`painPointFor()`、`beliefFor()` 三个文案模板方法，
保留 `classify()` 关键字分类逻辑不变。

**做法**：
- 注入 `LlmService` 到 `SellingPointCognitionService`
- 新增 `generateCognitionTexts()` 方法，调用 LLM 生成三条文案（buyer_cognition / pain_point / belief）
- LLM 输入：type / visualModel / feature / sourceText / sourcePath
- LLM 输出：结构化 JSON（中英双语）
- 失败时 fallback 到现有模板

**改动量**：仅修改 `SellingPointCognitionService.java` 一个文件

### Phase 2：分类引擎升级（中等投入）

**范围**：用 LLM 替换 `classify()` 的 keyword 匹配，改为语义分类。

**改进点**：
- 理解"1024×600"是分辨率，"Android 14 OS"是系统
- 识别一条文本中包含多个卖点需要拆分
- 区分"核心卖点"和"常规参数"
- 利用 `title`、`features`、`selling_points` 数据源

### Phase 3：端到端 LLM 生成（完整方案）

**范围**：完整构建 prompt → LLM → 解析 → 校验管线，直接输出完整 cognition JSON。

**做法**：
- 构建器模式：将 `ProductProfile` 完整 JSON + 品类策略传入 LLM
- 当前三阶段管线（compatibility → bullet → detail）转为 LLM prompt 中的结构化指令
- 支持模型选择（从 AiModel 管理中选择）

## API 设计（Phase 1）

### LLM Prompt

```
System: 你是 Amazon US car stereo 产品的视觉营销策略师。
        根据产品事实和卖点分类信息，生成买家认知文案。
        只使用输入事实，不发明功能。
        中英双语输出，JSON 格式。

User: {
  "type": "connection",
  "visual_model": "connection",
  "feature": "Wireless CarPlay",
  "source_text": "Supports wireless Apple CarPlay and Android Auto",
  "source_path": "amazon_listing.bullet_points[2]"
}
```

```
LLM Response:
{
  "buyer_cognition_cn": "上车自动连接手机，导航、通话、音乐无需插线",
  "buyer_cognition_en": "Auto-connects your phone for navigation, calls, and music without cables",
  "pain_point_cn": "每次上车插线连接手机很麻烦",
  "pain_point_en": "Plugging in a cable every time you get in the car is inconvenient",
  "belief_cn": "买家相信这台车机能给老车带来便捷的无线连接体验",
  "belief_en": "Buyers believe this head unit brings convenient wireless connectivity to their older vehicle"
}
```

### Fallback

LLM 调用失败（网络错误、JSON 解析失败、超时）时，回退到当前模板方法。

## 验证清单

- [ ] LLM 成功时，buyer_cognition 有具体产品信息，不是通用模板
- [ ] LLM 失败时，回退到现有模板，不阻断生成流程
- [ ] 原有 `addCompatibilityCognitions()` 的硬编码文案不受影响
- [ ] 原有 `classify()` 的分类结果不受影响
- [ ] 生成时间在可接受范围内（单条 < 3s，20 条 < 60s）
