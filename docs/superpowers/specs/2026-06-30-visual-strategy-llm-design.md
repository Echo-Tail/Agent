# 视觉策略 LLM 编排设计

## 目标

将 VisualStrategyService 从固定 6 slot 模板改为卖点数据驱动：一次 LLM 调用分析已确认的卖点分布，动态决定每张图/A+ 模块主打什么卖点。

> **关于与卖点认知 batch 合并**：卖点认知生成（DRAFT）和视觉策略生成（使用 CONFIRMED 版本）是两个独立 API 调用，发生在不同时间点，无法合并。视觉策略的 LLM 调用是**单独的一次请求**，不是与卖点认知共享。

## 改动范围

- **VisualStrategyService.java** — 新增 LLM 策略编排方法、补充新 role 模板
- **VisualStrategyServiceTest.java** — 更新测试数据

## LLM 调用策略

视觉策略生成时发 **1 次** LLM 请求，分析已确认的 `cognitionJson` 并返回策略编排。

输入：所有已确认的 `buyer_cognitions` 列表（type、buyer_cognition_cn/en 等）
输出：gallery 和 A+ 的 slot 分配方案（见下方格式）

## LLM 输出格式

```json
{
  "gallery": [
    {
      "slot": 1,
      "role": "why_buy",
      "focus_on": "连接方式",
      "goal_cn": "突出无线互联是最强差异点",
      "goal_en": "Highlight wireless connectivity as the strongest differentiator"
    },
    {
      "slot": 2,
      "role": "feature_spotlight",
      "focus_on": "性能配置",
      "goal_cn": "展示 Android 14 系统流畅度和硬件性能",
      "goal_en": "Showcase Android 14 system speed and hardware power"
    },
    {
      "slot": 3,
      "role": "screen_experience",
      "focus_on": "屏幕显示",
      "goal_cn": "展示大屏和清晰 UI 带来的使用体验",
      "goal_en": "Show how the large clear screen improves daily use"
    }
  ],
  "aplus": [
    {
      "module": 1,
      "type": "brand_banner",
      "focus_on": "连接方式",
      "goal_cn": "建立品牌和产品第一印象",
      "goal_en": "Build the first impression for the brand and product"
    }
  ]
}
```

### 规则

- `focus_on` 为中文卖点类型（"兼容性""连接方式""屏幕显示"等），对应 cognition 中 `type` 字段值
- `focus_on: null` 表示该类型卖点不足，跳过这个 slot
- `role` 从已知模板库中选择（见下方）
- gallery 最多 6 slot，A+ 最多 6 module，可以少于上限
- 同一 `focus_on` 可以出现在多个 slot 中（如果某个卖点特别强）

## 已知 role 模板

### Gallery

| role | visual_structure_cn | 适用 focus_on |
|---|---|---|
| `why_buy` | 左右对比：原车旧中控 vs 升级后智能大屏，中间用升级箭头连接 | 兼容性、显示、连接 |
| `core_connection` | 手机、无线信号、车机屏幕三层连接结构，突出自动同步 | 连接 |
| `screen_experience` | 车内驾驶视角，屏幕显示导航和应用 UI，突出清晰触控 | 显示、导航 |
| `safety_scene` | 真实倒车或通话场景，屏幕反馈清晰可见 | 安全 |
| `entertainment_audio` | 车内音乐娱乐场景，屏幕和音频元素形成氛围 | 音频、连接 |
| `compatibility_installation` | 产品与车型兼容信息图，使用标注线和简洁说明降低购买风险 | 兼容性、安装 |
| **`feature_spotlight`** | 产品核心部件特写，突出做工和设计质感 | 性能、连接 |
| **`usage_scene`** | 真实驾驶场景，展示产品使用状态的综合图 | 通用 |

### A+ Module

| module_type | 适用 focus_on |
|---|---|
| `brand_banner` | 连接、显示 |
| `upgrade_story` | 兼容性、显示、连接 |
| `core_features_grid` | 连接、显示、安全、导航 |
| `driving_scenarios` | 导航、音频、安全 |
| `compatibility_installation` | 兼容性、安装 |
| `specs_package_support` | 性能、安装、音频 |
| **`feature_showcase`** | 性能、显示（新） |

### 新 role 补充的模板内容

#### feature_spotlight

```java
case "feature_spotlight" ->
    "产品核心部件特写，突出做工和设计质感，使用标注说明关键技术和参数。";
```

#### usage_scene

```java
case "usage_scene" ->
    "真实驾驶场景，展示产品在车内的完整使用状态，驾驶视角。";
```

prompt、text_overlays、visual_elements 等按照同样的 pattern 补充。

## 代码改动

### 1. VisualStrategyService 新增 designVisualStrategy

新增方法 `designVisualStrategy(JsonNode cognitionRoot)`：
- 从 `cognitionRoot` 提取所有 `buyer_cognitions` 列表
- 调用 `llmService.syncChat()` 一次，传入 cognitions 信息
- 解析返回的 JSON 中的 `gallery` 和 `aplus` 数组
- LLM 不可用时返回 null，由调用方回退到固定模板

### 2. VisualStrategyService.generateLocalStrategyJson

- 接收 LLM 编排结果替代硬编码 `buildGalleryImages`
- 按 `focus_on` 类型调用 `selectCognitions` 选 cognition
- 对 `focus_on: null` 跳过该 slot
- 补充新的 role 模板 switch 分支

### 3. selectCognitions 修复 + focus_on 匹配

目前 `selectCognitions` 用英文 type（"compatibility"）匹配中文 JSON 字段（"兼容性"），**已引入 bug**。改为：

```java
private List<JsonNode> selectCognitionsByFocus(JsonNode cognitionRoot, String focusOn, int max) {
    // 按 focus_on 中文 type 精确匹配
}
```

原有 `selectCognitions` 保留作为 LLM 不可用时的回退（仍按英文 preferredTypes 匹配，但需参数支持中文）。

## LLM Prompt 设计

### 输入（发送给 LLM 的数据）

```
你是一名 Amazon US car stereo 视觉营销策略师。
以下是该产品已确认的卖点认知列表，请为 6 张 Amazon 副图（gallery）和 6 个 A+ 模块设计视觉策略。

已知卖点类型：兼容性、连接方式、屏幕显示、安全保障、导航功能、音频娱乐、性能配置、安装服务
已知画面角色：why_buy, core_connection, screen_experience, safety_scene, entertainment_audio, compatibility_installation, feature_spotlight, usage_scene
已知 A+ 模块类型：brand_banner, upgrade_story, core_features_grid, driving_scenarios, compatibility_installation, specs_package_support, feature_showcase

产品卖点列表：
<cognitions JSON 数组，每项包含 type, buyer_cognition_cn, buyer_cognition_en, pain_point_cn>

请分析卖点分布强度，为 gallery 和 aplus 分别输出策略编排 JSON。
```

### 输出约束

```
- gallery 最多 6 项，按 priority 排序
- aplus 最多 6 项，按 priority 排序
- role/type 必须从已知列表中选择
- focus_on 必须是已知卖点类型之一，或 null（跳过）
- 同一 focus_on 可以在多个 slot 中使用
- 输出合法 JSON，不要 markdown 包裹
```

## 测试策略

- 单元测试验证：给定 mock 编排 JSON，正确分配 slot 角色
- 集成测试验证 LLM 返回编排 JSON 时正常解析
- LLM 不可用时回退到原有固定模板行为

## 回退策略

如果 LLM 调用失败或 `visual_strategy` 字段丢失，`VisualStrategyService` 回退到当前的固定 6 slot 逻辑（保持向后兼容）。
