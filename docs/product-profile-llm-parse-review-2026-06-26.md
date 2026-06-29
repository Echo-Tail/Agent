# 产品资料 ASIN 解析质量审查与改造计划

日期：2026-06-26

## 背景

产品资料模块支持通过 ASIN 新建产品资料：

1. 用户输入 ASIN。
2. 后端调用 Bright Data 采集 Amazon 商品数据。
3. `ProductProfileService.parseWithLlm` 调用文本模型，把采集结果解析成 `productFactsJson`。
4. 用户在详情页确认产品事实，确认后生成版本快照。

当前体验问题：

- LLM 解析结果过于简单。
- 标题、bullet points 要点列表、产品详情、兼容车型、配件、卖点等信息提取不完整。
- 新建后的资料对后续 Amazon 副图和 A+ 图生成帮助有限。

核心判断：

这不是单纯的 LLM 能力问题，主要是输入预处理、输出 schema、截断策略、结果回填和测试覆盖不足导致的。

## 涉及代码

后端：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ProductProfileService.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/BrightDataService.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/dto/BrightDataScrapeResponse.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/model/ProductProfile.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/controller/ProductProfileController.java`

前端：

- `ShadcnAgentUI/src/views/product/ProductProfileListView.vue`
- `ShadcnAgentUI/src/views/product/ProductProfileDetailView.vue`
- `ShadcnAgentUI/src/api/product-profiles.ts`

文档/需求：

- `docs/prd/2026-06-22-amazon-car-stereo-image-prompt-plan.md`

## 当前问题清单

### P0-1：Bright Data 原始数据没有保存，ASIN 资料无法稳定重解析

当前位置：

- `ProductProfileService.createFromAsin`
- `ProductProfileService.reparse`

当前行为：

- `createFromAsin` 获取 Bright Data 结果后，只把结果传入 `parseWithLlm("bright_data", brightDataJson)`。
- `ProductProfile.markdownContent` 没有保存 Bright Data 原始 JSON。
- `reparse` 只支持 `markdownContent`，ASIN 创建的资料通常没有可重解析内容。

影响：

- 修改 prompt/schema 后，老资料不能直接重解析。
- 解析失败或解析过浅时，用户只能重新输入 ASIN 再采集。
- 采集成本、等待时间和失败概率都增加。

建议修改：

1. `ProductProfile` 增加字段：
   - `sourceType`：`MARKDOWN` / `BRIGHT_DATA_ASIN`
   - `sourceRawJson`：保存 Bright Data 原始响应或规范化后的 records JSON
   - 可选：`sourceAsin`
2. `createFromAsin` 成功采集后保存 raw JSON。
3. `reparse` 根据 `sourceType` 选择：
   - `MARKDOWN`：继续使用 `markdownContent`
   - `BRIGHT_DATA_ASIN`：使用 `sourceRawJson`
4. 保留 `markdownContent` 作为人工资料输入，不混用 Bright Data JSON。

测试闭环：

- 单元测试：ASIN 创建成功后，profile 包含 `sourceType=BRIGHT_DATA_ASIN` 和非空 `sourceRawJson`。
- 单元测试：ASIN 资料调用 `reparse` 时使用 `sourceRawJson`，不再因为 `markdownContent == null` 抛错。
- 集成测试或 service mock 测试：Bright Data 采集失败时不保存误导性的空 raw JSON。

验收标准：

- 用 ASIN 创建的产品资料可在详情页点击“重新解析”。
- 重解析不需要再次调用 Bright Data。
- 修改解析 prompt 后，历史 ASIN 资料可以使用已保存原始数据重新生成 facts。

### P0-2：LLM 输入被粗暴截断到 8000 字符

当前位置：

- `ProductProfileService.parseWithLlm`
- `truncate(content, 8000)`

当前行为：

- 无论是 Markdown 还是 Bright Data，直接截断前 8000 字符传给 LLM。
- Bright Data 返回通常是包装结构，前 8000 字符可能包含大量元信息、图片 URL、变体、无关字段。
- 标题、bullet points 要点列表、产品详情、技术参数可能在截断范围外。

影响：

- LLM 根本没有看到完整输入。
- 即使 Bright Data 已采集到字段，也不会被解析出来。

建议修改：

1. 引入 Bright Data 专用预处理方法：
   - `normalizeBrightDataProduct(Object data)`
   - 输入可以是 `BrightDataScrapeResponse`、`Map`、`List` 或 JSON 字符串。
   - 输出一个紧凑、字段明确的 JSON。
2. 只保留对产品资料有价值的字段：
   - `title`
   - `brand`
   - `asin`
   - `model_number`
   - `manufacturer`
   - `description`
   - `bullet_points`
   - `features`
   - `product_details`
   - `technical_details`
   - `specifications`
   - `included_components`
   - `compatible_devices`
   - `vehicle_fitment`
   - `warranty`
   - `images`
   - `a_plus_content` 或类似字段
3. 对长字段按字段级预算截断，而不是整体截断：
   - title：完整保留
   - bullet_points：完整保留原始列表，不假定固定条数；为防止异常数据，可设置较高上限，例如 20 条
   - product_details：最多 80 个键值
   - description：最多 4000 字符
   - A+ 内容：最多 6000 字符
   - 图片 URL：最多 20 条
4. Markdown 输入也按结构提取：
   - 标题
   - 小标题段落
   - 列表项
   - 表格
   - 原文片段
5. 最终传给 LLM 的内容应是“规范化输入 JSON”，而不是 Bright Data 整包原文。

测试闭环：

- 单元测试：给一个模拟 Bright Data 包装对象，normalize 后能找到 `records[0].title`。
- 单元测试：当 `bullet_points` 在原始 JSON 靠后位置时，normalize 仍完整保留。
- 单元测试：超长 description 被字段级截断，但 title/bullets 不丢。
- 单元测试：normalize 输出长度可控，且包含关键字段。

验收标准：

- 同一份采集数据中，标题、bullet points 要点列表、产品详情字段不会因为整体截断丢失。
- LLM prompt 中的输入区清晰可读，不含大量无关包装字段。

### P0-3：后端 JSON schema 太窄，承接不了 Amazon listing 信息

当前位置：

- `ProductProfileService.JSON_STRUCTURE_TEMPLATE`
- `ProductProfileDetailView.defaultFactsJson`
- `docs/prd/2026-06-22-amazon-car-stereo-image-prompt-plan.md`

当前行为：

后端模板只有：

- `identity`
- `physical_specs`
- `technical_specs`
- `features`
- `compatibility`
- `included_items`
- `warranty`

缺少：

- Amazon 标题
- bullet points 要点列表
- 产品描述
- 产品详情表
- 原始卖点
- 适配说明原文
- 禁止夸大声明
- 人工审核信息
- 图片素材线索

影响：

- LLM 没有位置输出 listing 原文。
- “标题、bullet points 要点列表、产品详情”即使被识别，也只能被压缩到 features 或丢弃。
- 后续图像提示词只能拿到少量规格字段，缺少营销表达和卖点上下文。

建议后端统一 schema：

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
  "amazon_listing": {
    "title": "",
    "bullet_points": [],
    "product_description": "",
    "product_details": {},
    "technical_details": {},
    "included_components_raw": [],
    "important_information": ""
  },
  "physical_specs": {
    "screen_size": "",
    "form_factor": "",
    "product_dimensions": "",
    "item_weight": "",
    "color": "",
    "material": ""
  },
  "technical_specs": {
    "operating_system": "",
    "ram": "",
    "storage": "",
    "resolution": "",
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
    "subwoofer_support": "",
    "split_screen": "",
    "mirror_link": ""
  },
  "compatibility": {
    "vehicle_fitment": [],
    "compatible_devices": [],
    "not_compatible": [],
    "unsupported_or_unknown": [],
    "fitment_notes": ""
  },
  "included_items": [],
  "warranty": "",
  "selling_points": [],
  "image_prompt_facts": {
    "primary_visual_claims": [],
    "installation_scene_facts": [],
    "comparison_points": [],
    "package_content_points": [],
    "compatibility_points": []
  },
  "claims_to_avoid": [],
  "review": {
    "status": "needs_human_review",
    "missing_fields": [],
    "low_confidence_fields": [],
    "notes": ""
  }
}
```

要求：

- 后端模板、前端默认 JSON、PRD 三者同步。
- 解析结果必须严格是 JSON 对象。
- 没有证据的字段填空字符串或空数组，不要编造。
- bullet points 要点列表保留原文和原始条数，同时再抽取 `selling_points`。

测试闭环：

- 单元测试：`parseWithLlm` mock 返回完整 schema，保存后前端默认字段不缺。
- 单元测试：JSON 中 `amazon_listing.bullet_points` 是数组，不是字符串。
- 单元测试：`claims_to_avoid` 和 `review` 始终存在。
- 前端构建测试：详情页能展示/编辑新 schema JSON。

验收标准：

- ASIN 解析结果中能看到完整 listing 标题、bullet points 要点列表、产品详情。
- 后续图片任务从产品资料版本读取时，有足够事实生成副图提示词。

### P0-4：Bright Data 包装结构处理不正确，标题提取可能失效

当前位置：

- `ProductProfileService.extractTitleFromBrightData`

当前行为：

- 方法只处理根节点是数组，或根节点直接含 `title`。
- 实际 `brightDataService.scrape` 返回 `ApiResponse<BrightDataScrapeResponse>`。
- `scrapeResponse.getData()` 通常是：

```json
{
  "records": [
    {
      "title": "...",
      "asin": "...",
      "description": "...",
      "product_details": {}
    }
  ],
  "timeCostMs": 1234,
  "recordId": 1
}
```

影响：

- `extractTitleFromBrightData` 找不到 `records[0].title`。
- 产品名称可能保留为 `ASIN-XXXXXXXXXX`。

建议修改：

1. `extractTitleFromBrightData` 复用 `normalizeBrightDataProduct`。
2. 查找路径按优先级：
   - `records[0].title`
   - `[0].title`
   - `title`
   - `product_title`
   - `name`
3. 标题长度限制从 200 放宽到 300 或按数据库字段截断。
4. 若标题与数据库重复，保持 `ASIN-xxx` 或追加 ASIN 后缀，避免静默失败。

测试闭环：

- 单元测试：包装对象 `records[0].title` 可提取。
- 单元测试：数组根节点 `[0].title` 可提取。
- 单元测试：重复标题时不会抛错，产品仍可创建。

验收标准：

- ASIN 创建成功后列表页显示真实 Amazon 标题，而不是 `ASIN-...`。

### P1-1：LLM 解析结果没有回填实体字段

当前位置：

- `ProductProfileService.createFromAsin`
- `ProductProfileService.createFromMarkdown`
- `ProductProfileService.checkSkuModelDuplicate`

当前行为：

- LLM 输出的 `brand`、`model_number`、`target_asin` 等只保存在 `productFactsJson`。
- `ProductProfile.brand`、`modelNumber`、`targetAsin` 没有同步更新。
- `sku` 仅在 ASIN 创建时预设。

影响：

- 列表页搜索和展示信息不足。
- 型号去重依赖实体字段，无法发挥作用。
- 后续版本判断可能不准确。

建议修改：

新增方法：

```java
private void applyFactsToProfile(ProductProfile profile, String factsJson)
```

逻辑：

- 解析 `factsJson.identity`。
- 回填：
  - `productName`
  - `brand`
  - `sku`
  - `modelNumber`
  - `targetAsin`
  - `category`
- 只在字段非空时覆盖。
- `productName` 需要检查重复，重复时保留原名或追加 ASIN。

测试闭环：

- 单元测试：facts 中 brand/modelNumber 回填到实体。
- 单元测试：ASIN facts 中 target_asin 回填。
- 单元测试：空字段不会覆盖已有有效字段。
- 单元测试：重复 productName 不会导致保存失败。

验收标准：

- 产品列表页显示品牌、SKU、真实标题。
- SKU / modelNumber 去重逻辑可实际命中。

### P1-2：ASIN 重复判断条件过严

当前位置：

- `ProductProfileService.createFromAsin`

当前行为：

```java
if (profileRepository.existsByProductName("ASIN-" + asin) &&
    profileRepository.findBySku(asin).isPresent()) {
    throw new BusinessException(...)
}
```

问题：

- 第一次创建后产品名可能改成真实标题。
- 第二次创建同 ASIN 时 `existsByProductName("ASIN-" + asin)` 可能为 false。
- 即使 SKU 已存在，也不会拦截。

建议修改：

- 改成：

```java
if (profileRepository.findBySku(asin).isPresent()) {
    throw new BusinessException(...)
}
```

或：

- 如果业务希望同 ASIN 作为新版本，则进入版本流程，而不是创建重复草稿。

测试闭环：

- 单元测试：已有 SKU 时再次 createFromAsin 抛 `CONFLICT`。
- 单元测试：已有产品名不是 `ASIN-xxx` 也能通过 SKU 拦截。

验收标准：

- 同一 ASIN 不会创建重复产品资料。

### P1-3：fallback JSON 类型不一致，且提取能力太弱

当前位置：

- `ProductProfileService.fallbackParseMarkdown`

当前行为：

- `included_items` 写成字符串 `"[]"`，不是数组。
- `features`、`compatibility` 为空对象。
- 只支持非常窄的正则提取。

影响：

- 没有 TEXT 模型时，解析结果几乎不可用。
- JSON schema 类型不稳定，后续消费容易踩坑。

建议修改：

- `included_items` 使用 `ArrayNode`。
- fallback 至少提取：
  - Markdown 标题
  - 列表项作为 `amazon_listing.bullet_points`
  - 表格键值作为 `amazon_listing.product_details`
  - 包含 compatible/fit/Ford/F-150 等关键词的行作为 `compatibility.vehicle_fitment`
  - 包含 warranty 的段落作为 `warranty`
- fallback 输出完整 schema。

测试闭环：

- 单元测试：没有模型时 fallback 返回完整 schema。
- 单元测试：`included_items` 是数组。
- 单元测试：Markdown 要点列表进入 `amazon_listing.bullet_points`，不写死 5 条。

验收标准：

- 即使没有 TEXT 模型，也能得到比现在更完整的产品事实草稿。

### P1-4：缺少字段置信度与人工审核提示

当前行为：

- LLM 只返回结果，不说明哪些字段缺失或低置信。

建议修改：

- 在 prompt 中要求：
  - 不能确定的字段放入 `review.low_confidence_fields`
  - 源数据未出现的关键字段放入 `review.missing_fields`
  - 明显冲突信息写入 `review.notes`
- 禁止编造。

测试闭环：

- 单元测试：mock LLM 返回缺字段时，结果中包含 `review.missing_fields`。
- 人工测试：资料详情页能看见需要人工确认的信息。

验收标准：

- 用户知道哪些字段需要人工补充，而不是看到一堆空字符串。

## LLM Prompt 改造建议

### System Prompt

目标：

- 限定角色为 Amazon car stereo 产品资料抽取专家。
- 强调只基于输入内容，不编造。
- 强制返回 JSON。
- 保留 Amazon listing 原文关键字段。

建议内容：

```text
你是 Amazon US 汽车电子产品资料抽取专家，专门处理 car stereo / Android radio / CarPlay head unit 商品数据。

任务：从输入的商品数据中抽取结构化产品事实，用于后续 Amazon 副图和 A+ 图生成。

规则：
1. 只基于输入内容抽取，不要编造。
2. 保留 Amazon listing 的原始标题、bullet points 要点列表、产品描述、产品详情表。
3. 同时抽取适合图片生成的卖点和兼容性事实。
4. 不确定或冲突的信息写入 review.low_confidence_fields 或 review.notes。
5. 缺失但对 car stereo 很重要的字段写入 review.missing_fields。
6. 只返回合法 JSON，不要 Markdown，不要解释。
```

### User Prompt

建议结构：

```text
请按以下 JSON schema 抽取 car stereo 产品事实。

要求：
- 字段不存在时使用空字符串、空数组或空对象。
- bullet_points 必须完整保留输入中的原始要点列表，不要假定只有 5 条，也不要压缩成一句话。
- product_details 必须保留 Amazon 商品详情表中的键值。
- selling_points 是从 title、bullet_points、description 中提炼出的短卖点。
- compatibility.vehicle_fitment 保存明确支持的车型/年份。
- compatibility.not_compatible 保存明确不支持的车型/配置。
- claims_to_avoid 保存没有证据支撑、不能用于广告图的声明。

JSON schema:
...

规范化输入:
...
```

## 代码实施顺序

### Step 1：补测试基线

新增测试类：

- `ProductProfileServiceParseTest`

覆盖：

- Bright Data 包装结构 normalize
- title 提取
- ASIN raw JSON 保存
- reparse ASIN 使用 raw JSON
- schema 完整性
- entity 字段回填
- duplicate ASIN
- fallback 类型一致性

原因：

- 当前问题集中在业务解析链路，先加测试能防止“改 prompt 看似有效、落库仍丢字段”。

### Step 2：引入规范化输入

新增方法：

- `normalizeInputForLlm(String sourceType, String content)`
- `normalizeBrightDataProduct(JsonNode root)`
- `pickFirstRecord(JsonNode root)`
- `copyIfPresent(ObjectNode target, JsonNode source, String... names)`
- `compactArray(JsonNode node, int maxItems, int maxCharsPerItem)`
- `compactObject(JsonNode node, int maxFields, int maxCharsPerValue)`

目标：

- Bright Data 输入只给 LLM 看“商品事实相关字段”。
- 不再对整个原始 JSON 做 8000 字符截断。

### Step 3：统一后端 schema

修改：

- `JSON_STRUCTURE_TEMPLATE`
- `fallbackParseMarkdown`
- 前端 `defaultFactsJson`
- PRD 若需要则同步补充。

目标：

- 后端实际输出和前端默认编辑结构一致。

### Step 4：重写 prompt

修改：

- `systemPrompt`
- `userPrompt`

目标：

- 明确保留 listing 原始字段。
- 明确禁止编造。
- 明确输出 review。

### Step 5：保存 raw source 并支持 ASIN 重解析

修改：

- `ProductProfile` 增加字段。
- 数据库迁移脚本。
- `createFromAsin`
- `reparse`

目标：

- ASIN 资料可以重解析。
- 调试和人工排查时能看到原始输入。

### Step 6：解析结果回填实体字段

新增：

- `applyFactsToProfile`

调用点：

- `createFromMarkdown`
- `createFromAsin`
- `reparse`
- `createNewVersion`
- `updateFacts` 可选：保存人工编辑 facts 后也回填。

目标：

- 列表、搜索、去重、版本判断都使用结构化结果。

### Step 7：验证全链路

后端：

- `gradle --no-daemon test`
- 如果外部网络测试不稳定，应把 Bright Data 外部下载测试隔离或标记。

前端：

- `npm run build`
- 如有测试：`npm test`

人工验证：

1. 输入一个车机 ASIN 创建产品资料。
2. 查看详情页 `productFactsJson`。
3. 确认以下字段存在且内容完整：
   - `identity.product_name`
   - `identity.brand`
   - `identity.sku`
   - `amazon_listing.title`
   - `amazon_listing.bullet_points`
   - `amazon_listing.product_description`
   - `amazon_listing.product_details`
   - `features`
   - `compatibility.vehicle_fitment`
   - `included_items`
   - `selling_points`
   - `review`
4. 点击重新解析，确认不会重新采集 Bright Data，也不会报 `没有可重新解析的 Markdown 内容`。
5. 保存并确认，确认版本快照包含完整 JSON。
6. 进入 Amazon 图片任务，选择该产品资料版本，确认提示词组合能读到新增字段。

## 验收用例样例

### 输入样例

模拟 Bright Data records：

```json
{
  "records": [
    {
      "asin": "B0TEST1234",
      "title": "Android 13 Car Stereo for Ford F150 2009-2014, 10.1 Inch Touchscreen with Wireless CarPlay Android Auto",
      "brand": "ExampleBrand",
      "bullet_points": [
        "Compatible with Ford F-150 2009 2010 2011 2012 2013 2014 standard radio dashboard.",
        "10.1 inch HD touchscreen with wireless Apple CarPlay and Android Auto.",
        "Built-in Bluetooth, WiFi, GPS navigation, FM radio and backup camera input.",
        "Supports steering wheel controls and original vehicle functions.",
        "Package includes radio unit, wiring harness, GPS antenna and user manual."
      ],
      "description": "Upgrade your factory radio with a modern Android car stereo...",
      "product_details": {
        "Brand": "ExampleBrand",
        "Model": "F150-101",
        "Screen Size": "10.1 Inches",
        "Connectivity Technology": "Bluetooth, Wi-Fi, USB",
        "Compatible Devices": "Ford F-150 2009-2014"
      }
    }
  ]
}
```

### 期望输出重点

- `amazon_listing.title` 等于原始标题。
- `amazon_listing.bullet_points` 保留输入中的全部原始要点；如果样例有 5 条则保留 5 条，如果实际商品有 8 条则保留 8 条。
- `amazon_listing.product_details.Brand` 等于 `ExampleBrand`。
- `compatibility.vehicle_fitment` 包含 `Ford F-150 2009-2014`。
- `features.carplay` 包含 wireless Apple CarPlay。
- `included_items` 包含 wiring harness、GPS antenna、user manual。
- `review.status` 为 `needs_human_review` 或 `parsed`，但不能缺失。

## 风险与注意事项

- 不要把竞品 ASIN 数据误认为自有产品的真实参数。当前产品资料模块是“新建产品资料”，如果用户输入的是竞品 ASIN，应在 UI 文案上提醒：采集结果需要人工确认。
- Amazon listing 里可能包含卖家夸张宣传，`claims_to_avoid` 应保留可疑声明，后续图片生成不要直接使用。
- Bright Data 字段名可能随 dataset 变化，normalize 需要支持多别名。
- 不要只靠 prompt 解决问题。必须先做输入规范化和 schema 承接，否则 LLM 仍会丢信息。
- 如果 TEXT 模型是 Anthropic `/messages`，当前 `parseWithLlm` 请求体仍按 OpenAI Chat Completions 格式构造，这可能是潜在兼容问题。改造时应确认项目模型管理对不同 `apiType` 的统一调用封装，避免解析模型调用偶发失败。

## 完成定义

代码修改完成后，必须满足：

- 后端新增/更新单元测试覆盖每个 P0/P1 修改点。
- 后端测试通过。
- 前端构建通过。
- 至少一次人工 ASIN 创建验证通过。
- 文档中的 schema 与后端模板、前端默认 JSON 保持一致。
- 解析失败时错误可读，且可从保存的 raw source 进行排查。


