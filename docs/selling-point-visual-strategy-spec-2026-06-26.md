# 卖点翻译与 Amazon 视觉内容策略模块规格

日期：2026-06-26
状态：设计确认稿
适用范围：car stereo / vehicle head unit 品类第一版

## 1. 背景

当前系统已经具备通过 ASIN 自动采集或解析产品资料的能力，能够把 Amazon 商品页面转换为结构化 `productFactsJson`。现有缺口不是继续提取更多字段，而是缺少一套稳定的中间转换系统：

```text
产品事实 -> 功能/参数 -> 买家认知 -> 视觉模型 -> 副图/A+策略 -> 可编辑 prompt
```

因此本模块目标不是“翻译文案”，而是把产品事实编译成可审核、可版本化、可复用的 Amazon 视觉内容策略。

## 2. 业务目标

第一版建设一个“卖点翻译 + 视觉策略生成”模块，支持：

- 从已确认或待确认的产品资料中生成买家认知地图。
- 用户先审核、启用/禁用、调整卖点优先级。
- 基于已确认的认知地图生成整套 Amazon 视觉内容策略。
- 策略包含 6 张副图和 6 个标准 A+ 模块。
- 输出中英双语策略说明、图片文字字段、图像生成 prompt。
- 暂不直接调用图像生成接口。

## 3. 非目标

第一版不做：

- 不接入 gpt-image-2 或其他图像生成 API。
- 不管理生成图片结果、失败重试、图片存储。
- 不做通用品类策略，只支持 car stereo / vehicle head unit。
- 不让 LLM 自由决定 A+ 或副图整体结构。
- 不覆盖用户已确认版本，重新生成默认创建新版本。

## 4. 已确认产品决策

1. 模块目标是“整套 Amazon 视觉内容策略”，不是单纯卖点文案。
2. 覆盖 6 张副图和标准 A+ 模块方案。
3. A+ 第一版采用固定 6 模块骨架。
4. 每个卖点认知必须带 evidence。
5. 流程采用两阶段：先生成认知地图，人工确认后再生成视觉策略。
6. 认知层中英双语，中文用于运营审核，英文用于 Amazon 图文和 prompt。
7. 允许 AI 直接生成图片内文字，但文字字段必须单独可编辑。
8. 卖点优先级采用车机品类规则优先，LLM 辅助补充排序。
9. 风险限制进入 `global_constraints`，并参与所有相关图文生成。
10. 第一版只支持 car stereo / vehicle head unit 品类。
11. 认知地图和视觉策略都要版本化，并关联产品资料版本。
12. UI 嵌入产品资料详情页，使用 Tab 承载。
13. 第一版只生成策略、视觉脚本和 prompt，不接图像生成。
14. 后端使用两个显式接口：认知生成接口、视觉策略生成接口。
15. 视觉策略支持生成范围选择：`gallery`、`aplus` 或全套。
16. 卖点数量动态提取，推荐 8-12 个，硬上限 20 个。
17. 卖点认知同时具有业务类型和视觉模型类型。
18. 做 `claims_to_avoid`，防止夸大或错误声明。
19. prompt 输出中英双版。
20. 先落规格文档，再进入正式实现。

## 5. 总体流程

```text
Step 1: 产品资料采集/解析
ASIN / markdown / Bright Data JSON -> ProductProfile.productFactsJson

Step 2: 生成卖点认知草稿
ProductProfile.productFactsJson -> SellingPointCognitionVersion(status=DRAFT)

Step 3: 人工确认认知地图
用户编辑认知、证据、启用状态、优先级、风险项 -> status=CONFIRMED

Step 4: 生成视觉策略
Confirmed cognition version + product facts + assets -> VisualStrategyVersion(status=DRAFT)

Step 5: 人工编辑视觉策略
用户调整副图/A+模块、文字、prompt、约束 -> status=CONFIRMED
```

## 6. 卖点认知层

### 6.1 输入

主要输入：

- `ProductProfile.productFactsJson`
- `ProductProfile.brand`
- `ProductProfile.productName`
- `ProductProfile.sku`
- `ProductProfile.targetAsin`
- 已上传的产品图片素材标签，可选

重点读取字段：

```text
identity
amazon_listing.title
amazon_listing.bullet_points
amazon_listing.product_description
amazon_listing.product_details
amazon_listing.technical_details
amazon_listing.included_components_raw
physical_specs
technical_specs
features
compatibility
included_items
warranty
claims_to_avoid
review
```

### 6.2 输出

卖点认知地图不是自由文本，而是结构化 JSON。

```json
{
  "category": "car_stereo",
  "category_strategy_version": "car_stereo_v1",
  "profile_id": 123,
  "profile_version_id": 456,
  "status": "draft",
  "buyer_cognitions": [],
  "global_constraints": [],
  "claims_to_avoid": [],
  "review": {
    "status": "needs_human_review",
    "missing_fields": [],
    "low_confidence_items": [],
    "notes": ""
  }
}
```

### 6.3 单条 cognition schema

```json
{
  "id": "wireless_carplay",
  "enabled": true,
  "priority": 2,
  "type": "connection",
  "visual_model": "connection",
  "feature": "Wireless CarPlay",
  "feature_cn": "无线 CarPlay",
  "buyer_cognition_cn": "上车自动连接，不用插线",
  "buyer_cognition_en": "Auto-connects when you start the car, no cable needed.",
  "scene_cn": "上车启动后自动同步手机导航和音乐",
  "scene_en": "Sync phone navigation and music after starting the car.",
  "pain_point_cn": "插线麻烦，开车前操作繁琐",
  "pain_point_en": "Cable connection is inconvenient before driving.",
  "belief_cn": "买家相信这台车机能让老车拥有现代智能连接体验",
  "belief_en": "The buyer believes this head unit gives an older vehicle a modern smart connection experience.",
  "confidence": "high",
  "evidence": [
    {
      "source_path": "amazon_listing.bullet_points[3]",
      "source_text": "Supports wireless Apple CarPlay..."
    }
  ],
  "risk_notes": []
}
```

### 6.4 cognition 类型

业务类型：

- `compatibility`：车型兼容、年份、空调类型、安装限制。
- `connection`：CarPlay、Android Auto、Bluetooth、MirrorLink。
- `display`：屏幕尺寸、QLED、分辨率、触控体验。
- `safety`：倒车影像、通话、驾驶安全。
- `navigation`：GPS、在线/离线地图。
- `audio`：DSP、EQ、FM/AM、音乐体验。
- `performance`：Android 版本、RAM/ROM、处理器。
- `installation`：即插即用、线束、包装、售后。
- `risk_constraint`：不适配项、不能夸大的限制。

视觉模型：

- `connection`
- `scenario`
- `comparison`
- `infographic`

### 6.5 卖点数量

- 最少建议：6 个。
- 推荐范围：8-12 个。
- 硬上限：20 个。
- 不固定 5 条。
- `bullet_points` 或 Bright Data `features` 原始条数不应被硬截断为 5。

## 7. 车机品类优先级规则

第一版使用固定品类规则优先，LLM 只做补充排序。

默认优先级：

1. 车型兼容性 / 安装限制，例如 `Only fit Manual AC`。
2. Wireless CarPlay / Android Auto。
3. 屏幕尺寸与清晰度。
4. 倒车影像 / 安全功能。
5. GPS / WiFi / Bluetooth。
6. RAM / ROM / 系统版本 / 处理器。
7. 音效 DSP / EQ。
8. 包装清单 / 售后。

注意：兼容性不一定占据副图第 2 张，但必须进入高优先级和全局约束。

## 8. 风险与声明控制

### 8.1 global_constraints

表示生成策略必须遵守的事实边界。

示例：

```json
[
  "Only show compatibility with Dodge RAM 1500/2500/3500 2013-2018 Manual AC.",
  "Use the listed screen size and resolution only.",
  "Do not show automatic climate control panels as compatible."
]
```

### 8.2 claims_to_avoid

表示生成图片、文案和 prompt 时不能说、不能暗示的声明。

示例：

```json
[
  "Do not claim automatic AC compatibility.",
  "Do not claim 4K display unless evidence says 4K.",
  "Do not claim backup camera included unless evidence confirms it is included.",
  "Do not claim wireless Android Auto if evidence only says wired Android Auto."
]
```

### 8.3 处理原则

- 风险信息不仅放在最后兼容性模块，也作为全局约束参与所有相关图文生成。
- 所有 claims 必须能回溯 evidence。
- 无 evidence 的高风险卖点应标记 `confidence=low` 或禁用。

## 9. 副图策略

第一版固定 6 图购买决策链。

### 9.1 结构

1. `why_buy`：价值总览，老车升级智能车机。
2. `core_connection`：核心连接卖点，例如 Wireless CarPlay / Android Auto。
3. `screen_experience`：屏幕体验，清晰度、触控、UI。
4. `safety_scene`：安全功能，倒车、通话、驾驶场景。
5. `entertainment_audio`：音乐、蓝牙、DSP/EQ、娱乐体验。
6. `compatibility_installation`：兼容性、安装、包装、售后，降低购买风险。

### 9.2 单张图 schema

```json
{
  "slot": 1,
  "role": "why_buy",
  "visual_model": "comparison",
  "goal_cn": "让买家相信老车可以升级成智能车机",
  "goal_en": "Make buyers believe their older vehicle can be upgraded with a smart head unit.",
  "selected_cognition_ids": ["upgrade_feeling", "large_screen", "modern_ui"],
  "buyer_cognition_cn": "老车也能拥有现代智能车机体验",
  "buyer_cognition_en": "Give an older vehicle a modern smart head unit experience.",
  "visual_structure_cn": "左侧原车旧中控，右侧安装后大屏车机，中间用升级箭头连接",
  "visual_structure_en": "Before-and-after dashboard comparison with an upgrade arrow between the old factory radio and the new touchscreen unit.",
  "required_visual_elements": ["installed dashboard", "before/after comparison", "upgrade arrow", "large touchscreen UI"],
  "text_overlays_cn": {
    "headline": "老车升级智能大屏",
    "subhead": "CarPlay / 导航 / 蓝牙一体体验",
    "badges": []
  },
  "text_overlays_en": {
    "headline": "Smart Upgrade",
    "subhead": "CarPlay, navigation and Bluetooth in one screen",
    "badges": ["Before", "After"]
  },
  "prompt_cn": "...",
  "prompt_en": "...",
  "negative_constraints": [],
  "text_rendering_risk": "medium",
  "evidence": []
}
```

## 10. 标准 A+ 模块策略

第一版固定 6 个标准 A+ 模块，LLM 只负责填内容和局部排序，不自由发明结构。

### 10.1 A+ 6 模块

1. `brand_banner`：品牌/产品主视觉。
2. `upgrade_story`：原车升级成智能车机。
3. `core_features_grid`：4 个核心功能。
4. `driving_scenarios`：导航/音乐/通话/倒车等真实场景。
5. `compatibility_installation`：车型兼容、空调类型、安装风险。
6. `specs_package_support`：参数、包装、售后。

### 10.2 A+ 模块 schema

```json
{
  "module_index": 1,
  "module_type": "brand_banner",
  "goal_cn": "建立品牌和产品第一印象",
  "goal_en": "Build the first impression for the brand and product.",
  "selected_cognition_ids": [],
  "visual_model": "scenario",
  "headline_cn": "为你的爱车升级智能中控体验",
  "headline_en": "Upgrade Your Drive with a Smarter Dashboard",
  "body_copy_cn": "...",
  "body_copy_en": "...",
  "image_prompt_cn": "...",
  "image_prompt_en": "...",
  "required_assets": ["product_image", "installed_dashboard_image"],
  "text_overlays_cn": {},
  "text_overlays_en": {},
  "negative_constraints": [],
  "evidence": []
}
```

## 11. 视觉策略总 schema

```json
{
  "category": "car_stereo",
  "category_strategy_version": "car_stereo_v1",
  "profile_id": 123,
  "profile_version_id": 456,
  "cognition_version_id": 789,
  "status": "draft",
  "content_scope": ["gallery", "aplus"],
  "global_constraints": [],
  "claims_to_avoid": [],
  "gallery_strategy": {
    "images": []
  },
  "aplus_strategy": {
    "layout_type": "standard_modules",
    "modules": []
  },
  "review": {
    "status": "needs_human_review",
    "missing_assets": [],
    "low_confidence_prompts": [],
    "notes": ""
  }
}
```

## 12. 数据模型建议

### 12.1 ProductSellingPointCognitionVersion

建议新增实体：

```text
id
profileId
profileVersionId
versionNumber
status: DRAFT | CONFIRMED | ARCHIVED
cognitionJson: TEXT/LONGTEXT
createdBy
createdAt
confirmedBy
confirmedAt
sourceFactsHash
```

说明：

- 保存完整认知地图快照。
- `sourceFactsHash` 用于判断产品资料变更后认知是否过期。
- 第一版不做复杂 diff。

### 12.2 ProductVisualStrategyVersion

建议新增实体：

```text
id
profileId
profileVersionId
cognitionVersionId
versionNumber
status: DRAFT | CONFIRMED | ARCHIVED
contentScope: gallery,aplus,gallery+aplus
strategyJson: TEXT/LONGTEXT
createdBy
createdAt
confirmedBy
confirmedAt
```

说明：

- 保存副图和 A+ 策略快照。
- 默认每次生成创建新版本。
- 不覆盖已确认版本。

## 13. 后端 API 建议

### 13.1 认知地图

```http
POST /api/product-profiles/{id}/selling-point-cognitions/generate
GET  /api/product-profiles/{id}/selling-point-cognitions/current
GET  /api/product-profiles/{id}/selling-point-cognitions/versions
PUT  /api/product-profiles/{id}/selling-point-cognitions/{versionId}
POST /api/product-profiles/{id}/selling-point-cognitions/{versionId}/confirm
```

生成请求体：

```json
{
  "overwrite_mode": "create_new_version",
  "language": "bilingual"
}
```

### 13.2 视觉策略

```http
POST /api/product-profiles/{id}/visual-strategies/generate
GET  /api/product-profiles/{id}/visual-strategies/current
GET  /api/product-profiles/{id}/visual-strategies/versions
PUT  /api/product-profiles/{id}/visual-strategies/{versionId}
POST /api/product-profiles/{id}/visual-strategies/{versionId}/confirm
```

生成请求体：

```json
{
  "cognition_version_id": 789,
  "content_scope": ["gallery", "aplus"],
  "overwrite_mode": "create_new_version"
}
```

## 14. UI 建议

位置：产品资料详情页新增 Tab。

建议 Tab：

- `基础资料`
- `图片素材`
- `卖点认知`
- `视觉策略`
- `版本历史`

### 14.1 卖点认知 Tab

功能：

- 生成认知地图草稿。
- 表格展示 cognition。
- 可编辑中英认知、场景、痛点、优先级、启用状态。
- 展示 evidence，可展开原始来源。
- 展示 `confidence`、`risk_notes`。
- 编辑 `global_constraints` 和 `claims_to_avoid`。
- 确认当前版本。

### 14.2 视觉策略 Tab

功能：

- 选择生成范围：副图、A+、全套。
- 基于已确认 cognition 版本生成策略。
- 副图以 6 张卡片展示。
- A+ 以 6 个模块展示。
- 每张图/模块可编辑：目标、画面结构、文字字段、prompt、负面约束。
- 支持复制英文 prompt。
- 支持确认策略版本。

## 15. LLM 生成设计

### 15.1 认知生成 Prompt 原则

系统提示词应强调：

- 你是 Amazon US car stereo 视觉营销策略师。
- 只能使用输入事实，不允许发明功能。
- 每个 cognition 必须有 evidence。
- 识别风险限制和不能夸大的 claims。
- 中英双语输出。
- 输出合法 JSON。
- 卖点数量推荐 8-12，硬上限 20。

### 15.2 视觉策略生成 Prompt 原则

系统提示词应强调：

- 基于已确认 cognition，不重新发明卖点。
- 使用固定 6 张副图结构和固定 6 个 A+ 模块结构。
- `global_constraints` 和 `claims_to_avoid` 必须进入所有相关 prompt。
- 允许 AI 生成图中文字，但文字必须单独输出到 `text_overlays`。
- prompt 中英双版。
- 输出合法 JSON。

## 16. 验证用例

### 16.1 认知生成

- 输入包含 8 条 bullet 时，不能只保留 5 条。
- 输入包含 `features` 而非 `bullet_points` 时，仍能生成卖点。
- 输入包含 `product_details` 数组 `{type,value}` 时，能提取到详情。
- `Only fit Manual AC` 必须进入 `global_constraints`。
- 摄像头如果只是支持输入，不能生成“包含摄像头”的卖点。
- 每条 enabled cognition 必须至少有一条 evidence。
- 输出 cognition 数量不超过 20。

### 16.2 视觉策略生成

- 未确认 cognition 版本时，不允许生成视觉策略。
- `content_scope=["gallery"]` 只生成副图。
- `content_scope=["aplus"]` 只生成 A+。
- 默认生成 gallery + aplus。
- 6 张副图结构固定，不缺 slot。
- A+ 6 模块结构固定，不缺 module。
- 所有 prompt 必须包含相关 global constraints。
- `text_overlays` 必须单独存在。
- 重新生成必须创建新版本，不覆盖已确认版本。

## 17. 实施计划

### Phase 1：后端模型与存储

- 新增 cognition version 实体、repository。
- 新增 visual strategy version 实体、repository。
- 建表或依赖 Hibernate ddl-auto 更新。
- 增加版本号生成逻辑。

### Phase 2：认知生成服务

- 新增 `SellingPointCognitionService`。
- 从 `ProductProfile.productFactsJson` 构造归一化输入。
- 增加 LLM prompt 和 fallback 最小逻辑。
- 保存 DRAFT 版本。
- 支持更新、确认、查询当前版本。

### Phase 3：视觉策略服务

- 新增 `VisualStrategyService`。
- 只允许基于 CONFIRMED cognition 生成。
- 支持 `content_scope`。
- 固定副图/A+骨架。
- 保存 DRAFT 策略版本。
- 支持更新、确认、查询当前版本。

### Phase 4：前端 UI

- 产品资料详情页新增 `卖点认知` Tab。
- 产品资料详情页新增 `视觉策略` Tab。
- 支持生成、编辑、确认、复制 prompt。
- 展示 evidence、风险、版本状态。

### Phase 5：测试闭环

- 后端单测覆盖 schema、状态流、版本化、scope。
- 前端基础 build 验证。
- 用真实 Bright Data 样本做端到端手动验证。

## 18. 开放问题

暂不阻塞第一版，但后续需要继续确认：

- 是否要对不同车机细分类目建立多个 strategy pack。
- 是否要接入图片生成结果管理。
- 是否要支持批量 ASIN 一键生成策略。
- 是否要支持导出为 Excel / Markdown / 飞书文档。
- 是否要支持多 marketplace 语言，例如 DE/FR/ES。