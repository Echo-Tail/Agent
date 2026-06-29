# Amazon US Car Stereo 商品图自动生成器计划

## 背景

当前目标是为 Amazon 美国站 car stereo 类目建设一个商品图自动生成器。它的最终产出不是提示词文本，而是可人工挑选的 Amazon 副图 / A+ 商品图候选；提示词、产品资料、素材事实、图片表达结构和生成结果是过程中沉淀的资产。

阶段 1 做半自动版本：系统自动完成素材采集、图片表达分析、prompt 草稿生成和图片生成调用；人工负责确认产品资料、选择素材、修改 prompt、挑选候选图。

## 核心定位

- **商品图自动生成器**：基于产品资料、素材来源和图像模型，生成可人工挑选的商品图候选。
- **提示词不是最终产物**：prompt 是中间资产，只有人工认为稳定好用时才保存到提示词库。
- **ASIN 和图片 URL 都是素材来源**：它们不默认代表目标产品。
- **产品资料代表目标产品**：目标产品事实来自用户上传的 Markdown 产品参数文档。
- **自有产品图可选**：有自有产品图时走图生图；没有时允许文生图，适合部分副图/A+ 图。

## 阶段 1 总流程

```text
维护产品资料：上传产品参数 Markdown
 -> LLM 从 Markdown 提取目标产品事实
 -> 人工确认并保存产品资料版本
 -> 新建商品图生成任务，选择已确认产品资料
 -> 添加素材来源：单个 ASIN 或一个/多个图片 URL
 -> ASIN 通过 Bright Data 同步 API 采集商品信息、listing 图片、A+ 图片
 -> 图片 URL 下载为图片素材
 -> 展示素材图片，用户选择要分析的图片
 -> LLM 多模态分析图片表达结构
 -> 用户选择一个图片表达结构
 -> 系统生成结构化 brief 和最终 prompt 草稿
 -> 用户修改最终 prompt
 -> 可选选择/上传自有产品图作为参考图
 -> 有参考图时调用图生图，没有参考图时调用文生图
 -> 输出 1-10 张候选商品图，默认 4 张
 -> 用户标记候选图：未处理 / 可用 / 弃用
```

## 菜单划分

阶段 1 拆成两个菜单：

### 产品资料

维护自有产品长期资产。

能力：

- 产品列表
- 上传 Markdown 产品参数文档
- LLM 解析目标产品事实 JSON
- 人工编辑 JSON 并确认
- 管理产品资料版本
- 维护多张默认自有产品图

### Amazon 商品图生成

围绕某个产品资料执行一次商品图生成工作流。

能力：

- 选择产品资料
- 添加素材来源
- 分析图片表达结构
- 生成 / 修改 prompt
- 调用文生图或图生图
- 查看并标记候选图

产品资料是长期资产，生成任务是一次工作流，二者不混在同一个菜单中。

## 产品资料

### 来源

产品资料来自用户上传的 Markdown 产品参数文档。当前约定：每个产品一个 Markdown 文件。

Markdown 允许自由格式，不强制模板。系统使用 LLM 将自由格式 Markdown 抽取为固定 car stereo 产品参数 JSON，再由人工确认。

### 唯一性

- 产品名称不允许重复。
- SKU / 型号唯一。
- 目标产品 ASIN 可选，阶段 1 最多一个。
- 目标产品 ASIN 与素材 ASIN 是不同概念。

### 上传同一 SKU / 型号

如果上传 Markdown 后解析出的 SKU / 型号已存在：

```text
不新建重复产品
 -> 创建该产品资料的新版本
 -> 新版本进入待确认
 -> 确认前不影响当前已确认版本
```

### 状态

```text
待解析 -> 待确认 -> 已确认
      -> 解析失败
```

只有已确认的产品资料可以用于商品图生成。

### 版本

产品资料需要版本化：

- 产品资料保存当前产品条目。
- 产品资料版本保存每次人工确认后的目标产品事实快照。
- 生成任务创建时固定引用某个产品资料版本。
- 用户可手动将任务升级到最新产品资料版本，并记录升级动作。
- 阶段 1 只显示当前任务版本和“升级到最新版本”按钮，不做复杂版本历史 UI。

### 默认自有产品图

产品资料可以维护多张默认自有产品图，生成任务可以直接选择，也可以临时上传。

默认图需要分类标签：

- `front`：正面图
- `back`：背面图
- `ports`：接口图
- `wiring`：线束图
- `package`：包装 / 配件图
- `installation`：安装效果图
- `other`：其他

## 目标产品事实结构

第一版固定 car stereo 参数结构，后续需要字段再扩展。字段参考 Amazon Product information / Bright Data `product_details`，但以自有产品参数文档为准。

```json
{
  "identity": {
    "product_name": "",
    "brand": "",
    "manufacturer": "",
    "model_number": "",
    "sku": "",
    "target_asin": "",
    "category": "car stereo"
  },
  "physical_specs": {
    "screen_size": "",
    "form_factor": "",
    "product_dimensions": "",
    "color": "",
    "material": ""
  },
  "technical_specs": {
    "controller_type": "",
    "connectivity": [],
    "connector_types": [],
    "control_methods": [],
    "audio_output_mode": "",
    "supported_media": []
  },
  "features": {
    "carplay": "",
    "android_auto": "",
    "bluetooth": "",
    "wifi": "",
    "backup_camera": "",
    "gps_navigation": "",
    "steering_wheel_control": "",
    "fm_am_radio": "",
    "subwoofer_support": ""
  },
  "compatibility": {
    "compatible_devices": [],
    "vehicle_fitment": [],
    "unsupported_or_unknown": []
  },
  "included_items": [],
  "warranty": "",
  "claims_to_avoid": [],
  "review": {
    "status": "needs_human_review",
    "notes": ""
  }
}
```

## 输入类型边界

### 产品资料

目标产品来源。商品图生成任务必须选择一个已确认产品资料。

### ASIN 商品来源

素材来源之一。通过 Bright Data 获取商品信息、listing 图片和 A+ 图片。它不默认代表目标产品。

阶段 1：

- 支持单个 ASIN。
- 使用 Bright Data 同步接口。
- 支持粘贴 Bright Data snapshot JSON 作为调试和兜底。
- 多个 ASIN 和异步采集后置。

### 图片 URL

素材来源之一。用于下载图片并交给 LLM 多模态模型分析，提取构图、场景、风格、卖点排版和提示词结构。

阶段 1：

- 支持一个或多个图片 URL。
- 每个 URL 独立下载和分析。
- 单个 URL 失败不阻断整个任务。

### 自有产品图 / 图生图参考图

可选输入。用于图生图阶段约束目标产品外观。

- 可以从产品资料默认自有产品图中选择。
- 可以在生成任务中临时上传。
- 不上传时允许文生图。

## 素材事实与目标产品事实

- **素材事实**：从 ASIN 或图片 URL 中提取的可参考商品信息、卖点、兼容声明、配件和功能描述。它属于素材，不默认代表自有产品。
- **目标产品事实**：从产品资料版本中读取的自有产品真实参数、功能、配件和限制。最终生成图不能违背它。

素材事实不做正式确认状态，但允许用户编辑或剔除。

最终 prompt 生成时：

- 目标产品事实默认参与，但系统按图片类型自动选择相关字段，用户可补充或删除。
- 图片表达结构由用户选择一个参与。
- 素材事实只有用户勾选后才进入 prompt。

## 图片表达结构

图片 URL 或用户选择的 ASIN 图片分析结果不直接叫 prompt，而叫图片表达结构。它服务 gpt-image-2 的 prompt 组织方式：先明确用途和画面目标，再描述场景、主体、关键细节、构图、文字、保留/禁止项和多图关系。

分析范围不做硬过滤。即使来源图中包含竞品品牌、车型元素、竞品产品外观或竞品文案，也允许被识别和记录。阶段 1 的 prompt 草稿也不自动规避这些风险，控制点放在人工修改 prompt 后再生成。

多个图片 URL 各自独立分析，每张图生成一个图片表达结构。阶段 1 不把多个 URL 合并成综合结构。

ASIN 采集到的 listing 图片和 A+ 图片先展示，用户选择某一张后再触发图片表达结构分析。

阶段 1 生成 prompt 草稿时一次只选择一个图片表达结构。后续再支持多个表达结构，并指定角色。

图片表达结构第一版展示 JSON，但不强制确认、不允许编辑内部 JSON。用户可以查看、选择、重新分析或删除分析结果，主要在 prompt 草稿阶段修改最终 prompt。

建议结构：

```json
{
  "intended_use": "Amazon US car stereo A+ image",
  "image_type": "feature_infographic",
  "scene": {
    "background": "",
    "environment": "",
    "lighting": "",
    "mood": ""
  },
  "subject": {
    "main_subject_role": "",
    "subject_placement": "",
    "supporting_objects": []
  },
  "composition": {
    "framing": "",
    "viewpoint": "",
    "layout_grid": "",
    "negative_space": "",
    "callout_positions": []
  },
  "visual_style": {
    "medium": "photorealistic commercial product image",
    "color_palette": "",
    "texture_detail": "",
    "polish_level": ""
  },
  "copy_structure": {
    "headline": "",
    "feature_labels": [],
    "body_copy_pattern": "",
    "typography": "",
    "text_placement": "",
    "legibility_requirements": []
  },
  "risk_notes": [],
  "reference_image_roles": []
}
```

## 图片类型

目标图片类型由图片表达结构推断，用户可以修改。阶段 1 支持：

- `feature_infographic`：功能卖点图
- `installation_scene`：安装场景图
- `dimension`：尺寸图
- `ports_wiring`：接口 / 线束图
- `package_contents`：包装清单图
- `compatibility`：兼容车型图
- `a_plus_banner`：A+ 横幅图
- `a_plus_module`：A+ 模块图

阶段 1 暂不做：

- 主图
- 对比图
- 评论 / 背书图

图片类型到目标产品事实字段的映射先写死默认规则，后续规则中心再配置化。

## Prompt

Prompt 草稿页同时展示结构化 brief 和最终自然语言 prompt。

- 结构化 brief 用于说明 prompt 来源和依据。
- 用户主要编辑最终自然语言 prompt。
- 每次生成都保存当时使用的 prompt 版本。
- 每次生成都保存当时使用的模型和参数。
- 每次 prompt 生成保存当时使用的图片表达结构快照。

生成任务中的 prompt 草稿和最终 prompt 不自动进入提示词库。生成后提供“保存到提示词库”操作，由人工判断该 prompt 是否足够稳定、可复用，再沉淀到现有提示词库。

## 文案处理

英文文案由系统自动生成，用户可修改。

文案生成规则：

- 图片表达结构决定文案布局和数量。
- 目标产品事实决定文案内容。
- 保留素材图的文案结构，但用目标产品事实重写文案内容。
- 阶段 1 不限制文案长度，主要面向 PC 端查看。

## 生成任务

生成任务使用步骤向导：

```text
1. 选择产品资料
2. 添加素材来源
3. 分析图片表达
4. 生成 / 修改 prompt
5. 生成候选商品图
```

生成任务规则：

- 必须选择一个已确认的产品资料。
- 必须至少提供一个素材来源：单个 ASIN 或一个/多个图片 URL。
- 阶段 1 支持单个 ASIN，多个 ASIN 后置。
- 图片 URL 可以多个，逐个下载和分析；单个 URL 失败不阻断整个任务。
- ASIN 采集失败时，如果还有图片 URL 成功，任务可以继续。
- 自有产品图可选：有图时走图生图，没有图时走文生图。

任务状态保持简单：

```text
草稿
待生成
已生成
已完成
失败
```

中间步骤通过数据是否存在判断，不做复杂状态流。

## 生成结果

默认一次生成 4 张候选图，用户可调整为 1-10 张。

候选图状态：

```text
未处理
可用
弃用
```

任务内保存全量生成记录，便于回看和复盘。只有用户标记为可用、收藏或确认采用的结果，才进入后续资产沉淀流程。

阶段 1 的生成结果暂不自动进入公共素材库，也不新建商品图结果库。所有结果先保存在任务历史中；后续根据使用情况再决定沉淀到公共素材库或独立结果库。

## 阶段 1 不做

- 多个 ASIN 采集和素材归并
- 自动风险检查
- 自动评分
- Prompt Lab
- 规则配置中心
- OCR 文案检查
- 复杂权限
- Amazon 官方 API
- 多个图片表达结构组合
- 自动进入公共素材库

## 后续扩展优先级

P1 自动风险检查：优先补充功能、车型、配件、品牌、文案误导风险。

P2 自动评分：在风险检查之后增加质量筛选。

P3 规则配置中心：等风险和评分规则跑出经验后再配置化。

P4 Prompt Lab：有足够历史 prompt 和生成结果后，再做受控创意变体。

P5 批量 ASIN / 异步采集：单任务流程稳定后再规模化。

P6 OCR 文案检查：当生成图中文字错误、乱码或误导问题变多时再补。

P7 多表达结构组合：单表达结构流程稳定后，再支持构图参考、文案布局参考、场景参考等多角色组合。

P8 结果资产库 / 公共素材库沉淀：等确认哪些结果真有复用价值后，再决定沉淀位置。

## 第一版验收标准

阶段 1 做完后，应能完成：

```text
上传产品 Markdown
 -> 解析目标产品事实 JSON
 -> 人工确认产品资料
 -> 创建商品图生成任务并选择产品资料
 -> 添加单个 ASIN 或多个图片 URL 作为素材来源
 -> 选择并分析一张素材图，得到图片表达结构
 -> 生成结构化 brief 和最终 prompt 草稿
 -> 用户修改最终 prompt
 -> 可选上传自有产品图
 -> 调用文生图或图生图
 -> 输出 1-10 张候选商品图
 -> 用户标记候选图为未处理 / 可用 / 弃用
```
