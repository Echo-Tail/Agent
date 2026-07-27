# Car Stereo 评论洞察 MVP 实施计划

关联设计：`docs/superpowers/specs/2026-07-24-car-stereo-review-analysis-mvp.md`

## Phase 1：领域模型与数据库

1. 新增 V8 Flyway migration。
2. 实现项目、商品、采集批次、评论、分析运行、洞察、机会及关联实体。
3. 添加唯一约束、项目归属校验和 Repository。
4. 为幂等去重、权限和状态转换编写测试。

交付结果：可以创建评论分析项目，并安全存储标准化评论。

## Phase 2：Bright Data 评论采集

1. 为 Amazon Reviews 配置独立 dataset ID。
2. 从 `ReviewProjectProduct` 生成 Amazon 商品 URL。
3. 使用 trigger/snapshot/download 异步采集，移除业务流程中的阻塞等待。
4. 实现 Bright Data 字段适配器，保留 raw JSON。
5. 实现评论 ID/content hash 双层去重。
6. 添加采集进度、失败重试和部分成功处理。

交付结果：输入 ASIN 后可以稳定得到标准化、无重复评论。

## Phase 3：LLM 原子问题提取

1. 将 taxonomy v1 固化为服务端枚举。
2. 定义 `review_insight_v1` DTO 和 JSON 校验器。
3. 编写 Car Stereo 分析 system prompt。
4. 每 10～20 条评论批量调用 LLM。
5. 校验 evidence quote 必须来自原文。
6. 保存批次错误并支持仅重试失败评论。
7. 记录 prompt、taxonomy、model 版本。

交付结果：每条评论产生零到多条可追溯原子洞察。

## Phase 4：机会聚合与评分

1. 先按 module/scenario/action type 分桶。
2. 使用标准化问题键和一次 LLM 归并生成机会。
3. 计算频率、低星比例、验证购买比例和证据质量。
4. 实现 customer impact、business impact、effort 和 priority score。
5. 支持人工调整实施成本后实时重算。

交付结果：得到按业务优先级排序的改进机会列表。

## Phase 5：API

1. 项目 CRUD。
2. 启动采集、查询进度和重试。
3. 启动分析、查询进度和重试。
4. 评论、洞察、机会分页筛选。
5. 洞察和机会人工修正。
6. 分析版本确认。
7. Dashboard 聚合接口。

交付结果：完整工作流可由 API 驱动。

## Phase 6：前端

1. 新建分析项目和 ASIN 配置页。
2. 实现采集/分析任务进度展示。
3. 实现概览指标和热力矩阵。
4. 实现机会排序、筛选和本品/竞品对比。
5. 实现评论证据下钻及人工修正。
6. 实现版本确认和只读状态。

交付结果：产品经理可以独立完成一次评论研究。

## Phase 7：端到端验证

使用一个本品和两个竞品 ASIN：

1. 每个 ASIN 采集 100 条评论。
2. 验证重复采集不增加重复数据。
3. 人工抽样 50 条洞察，检查问题拆分、分类和证据准确性。
4. 验证 Top 10 机会全部可以下钻到原文。
5. 验证失败任务可以从检查点重试。
6. 记录单条评论平均采集成本、LLM token 和分析耗时。

MVP 放行门槛：

- 原文证据有效率 ≥ 98%。
- 产品模块分类人工接受率 ≥ 85%。
- 严重程度人工接受率 ≥ 80%。
- 重复评论率 < 1%。
- 300 条评论完整分析在异步任务中可稳定完成。

## 推荐开发顺序

第一迭代只完成 Phase 1～3，并通过 API 查看 JSON 结果；第二迭代完成机会聚合和 Dashboard；第三迭代补人工确认、竞品对比和端到端验收。这样可以最早验证最关键的不确定性：评论能否被稳定拆成有证据的产品问题。
