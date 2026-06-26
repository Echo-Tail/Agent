# 图像生成代码与业务审查记录

日期：2026-06-26

## 审查范围

- 后端图片生成接口、服务层、记录保存与模型调用链路
- 图生图、多图参考、遮罩、历史记录和本地上传能力
- Amazon 图片工作台相关生成流程
- 前端图片生成页面、参数选择、素材选择和结果展示
- 与“精确产品合成 / 安装效果图”相关的业务适配度

## 总体结论

当前功能已经覆盖基础文生图、图生图、多结果生成、历史记录、素材选择和 Amazon 图片工作台，工程可以编译通过，适合做探索式生成。

但它还不适合高精度电商产品合成，尤其是“固定中控台场景、替换指定车机、保留旋钮和物理按键、局部填充面板灯、替换指定圆孔”这类强约束编辑。核心问题是业务上仍然把多张参考图当成松散素材，缺少“主场景图、替换产品图、材质图、局部 mask、保留区域、禁止区域”的结构化建模，导致模型容易自由重绘和跑偏。

## 主要问题

### 高风险：配置的图片模型没有真正生效

位置：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ImageGenerationService.java`

问题：

- 服务里硬编码了 `MODEL_NAME = "gpt-image-2"`。
- 即使前端或业务传入了 `modelId`，后端只用该模型读取 `apiUrl` 和 `apiKey`，请求体里的 `model` 仍然固定为 `gpt-image-2`。
- 这会让模型管理页面的一部分能力变成表面配置，也会导致非 `gpt-image-2` 兼容供应商调用失败或行为不一致。

建议：

- 请求体里的模型名应使用 `AiModel.modelName` 或同等字段。
- 保留默认模型名只作为兜底，而不是覆盖用户选择。
- 为 `generate` 和 `edit` 增加测试，验证传入 `modelId` 后请求体使用对应模型名。

### 高风险：图片分析接口存在 URL 下载与本地 CLI 执行风险

位置：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ImageAnalysisService.java`

问题：

- `analyzeImageExpression` 接收任意 `imageUrl` 并直接 `new URL(imageUrl).openStream()` 下载。
- 下载后调用本地 `codex exec`。
- 命令参数包含 `--dangerously-bypass-approvals-and-sandbox`。

风险：

- SSRF：用户可让服务端请求内网、本机或云元数据地址。
- 资源消耗：没有下载大小、类型、超时、像素尺寸等限制。
- 本地执行风险：图片分析链路不应默认绕过沙箱和审批。

建议：

- 只允许 `http` / `https`。
- 阻止 localhost、内网网段、链路本地地址、云元数据地址。
- 下载前后校验 `Content-Type`、字节大小和实际图片格式。
- 限制最大像素尺寸。
- 移除 `--dangerously-bypass-approvals-and-sandbox`。
- 更长期应改为后端直接调用受控视觉模型 API，而不是 Web 请求里启动本地 Codex CLI。

### 中风险：Amazon 图片工作台模型选择未传入后端

位置：

- `ShadcnAgentUI/src/views/amazon/AmazonImageWorkbenchView.vue`

问题：

- 页面定义了 `genModelId`，也加载了图片模型。
- 但调用 `generateImage` 和 `editImage` 时最后一个参数传的是 `undefined`。
- 结果是用户即使选择或默认加载了模型，也不会影响实际生成请求。

建议：

- 调用 `generateImage` / `editImage` 时传入 `genModelId.value`。
- UI 上补齐模型选择控件，避免只在代码里存在状态。
- 增加前端测试或组件级用例，覆盖模型选择参数传递。

### 中风险：多参考图没有角色语义，难以支持精确合成

位置：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ImageGenerationService.java`
- `ShadcnAgentUI/src/views/image/ImageGenerationView.vue`
- `ShadcnAgentUI/src/views/amazon/AmazonImageWorkbenchView.vue`

问题：

- 图生图接口最多接收 4 张参考图，但后端只是以重复 `image[]` 字段发送。
- 系统不知道哪张是主场景、哪张是替换主机、哪张是面板灯材质、哪张是局部形状参考。
- 当前 prompt 只能用自然语言描述角色，模型很容易误解素材关系。

对当前业务的影响：

- 用户想表达“以图 1 为最终场景，图 2 替换主机，图 3 填充指定区域”时，系统没有结构化约束。
- 模型可能改动中控台、添加/移除旋钮、重绘屏幕 UI、扭曲车机边框或让产品悬浮。

建议：

- 前端上传区改成明确槽位：
  - 主场景图
  - 替换产品图
  - 材质/面板灯参考图
  - 局部细节参考图
  - 可选 mask
- 后端 DTO 增加图片角色字段，不再只接收无语义 `image[]`。
- prompt 生成器根据角色自动拼装：
  - 哪些区域必须保留
  - 哪些区域允许重绘
  - 哪张图提供外观
  - 哪张图提供材质
  - 哪些物理结构禁止变化
- 对强约束商品图，优先要求用户提供 mask 或在 UI 里绘制 mask。

### 中风险：上传文件缺少验证和文件名清理

位置：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ImageGenerationService.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/controller/ImageGenerationController.java`

问题：

- 图生图上传只限制数量，缺少文件大小、真实 MIME、图片解码、像素尺寸验证。
- `upload-local` 使用 `System.currentTimeMillis() + "_" + file.getOriginalFilename()` 拼接文件名。

建议：

- 限制单文件大小和总请求大小。
- 使用 `ImageIO` 或可靠图片库验证实际格式和尺寸。
- 文件名只保留安全扩展名，实际存储名使用 UUID。
- 拒绝 SVG、HTML、可执行内容和空文件。

### 中低风险：多图生成的记录、计费和耗时不够准确

位置：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ImageGenerationService.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/service/AmazonImageTaskService.java`

问题：

- `n > 1` 时会并发调用多次并为每张图保存一条历史记录。
- 每条记录都写入整体耗时，而不是单张图片实际耗时。
- 成功多张图时只记录一次图片使用量。
- Amazon 任务只保存第一个 `generationRecordId`。

建议：

- `SingleGenerateResult` 记录单次耗时。
- 每张图片保存对应耗时。
- 计费/用量按成功图片数量写入或在记录中明确 `imageCount`。
- Amazon 任务结果表与图片生成记录建立一对一关联，或保存所有 record id。

## 测试与验证状态

已验证：

- `ShadcnAgentUI` 执行 `npm run build` 通过。
- `EcomAgents` 执行 `gradle --no-daemon compileJava` 通过。

未完整验证：

- 未运行完整后端测试套件。现有 `ImageGenerationDownloadTest` 包含外部 PackyAPI 图片 URL，结果依赖网络与第三方资源状态。
- 未做真实图片生成 API 调用验证。
- 未做浏览器端交互截图验证。

## 业务改进优先级

### P0

- 修复模型名硬编码，确保模型配置真实生效。
- 加固 `analyzeImageExpression` 的 URL 下载与本地 CLI 调用风险。
- 修复 Amazon 图片工作台不传 `genModelId` 的问题。

### P1

- 为精确图生图建立“图片角色 + mask + 保留区域”的结构化业务模型。
- 上传文件增加安全校验。
- 多图生成记录单张耗时、单张计费和所有 record id。

### P2

- 增加前端组件测试，覆盖模型选择、参考图数量限制、mask 上传、参数提交。
- 增加后端单元测试，覆盖模型名传递、上传校验、路径规范化、失败部分成功场景。
- 把提示词模板沉淀为可配置模板，支持不同 Amazon 图片类型和不同产品类目。

## 针对当前车机安装效果图场景的建议流程

当前自然语言 prompt 很难稳定控制结果。建议把流程改成：

1. 用户上传主场景图，并在 UI 中标记“车机区域”作为可编辑区域。
2. 用户上传替换车机图，系统标记为“产品外观参考”。
3. 用户上传面板灯图，系统标记为“局部材质参考”。
4. 用户上传或框选左侧圆孔区域，系统标记为“局部细节替换参考”。
5. 系统自动生成强约束 prompt：
   - 保留中控台、空调出风口、物理按键、旋钮和透视。
   - 只编辑 mask 区域。
   - 产品必须贴合安装位，不能悬浮。
   - 不得新增返回键、Home 键、电源键、伪文字。
   - CarPlay UI 保持真实、清晰、规整。
6. 后端将图片角色和 mask 一起提交给图像编辑 API。

这比继续优化单段提示词更可靠，因为它把“模型应该理解的关系”变成了系统明确传递的结构。
