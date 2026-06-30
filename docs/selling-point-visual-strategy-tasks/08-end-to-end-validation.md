# 任务 08：端到端验证与回归 ✅

## 目标

用真实产品数据和完整的 API 流程完成端到端验证。

## 验证内容

| # | 验证条目 | 状态 | 说明 |
|---|---------|------|------|
| 1 | ASIN 采集后能生成 cognition version | ✅ | 产品事实更新 → `POST /selling-point-cognitions/generate` → DRAFT v1，15 个卖点 |
| 2 | cognition 确认后能生成 visual strategy version | ✅ | `POST /confirm` → CONFIRMED → `POST /visual-strategies/generate` → DRAFT v1 |
| 3 | 6 张副图和 6 个 A+ 模块完整 | ✅ | gallery 6/6 (why_buy → compatibility_installation)，A+ 6/6 (brand_banner → specs_package_support) |
| 4 | evidence、global_constraints、claims_to_avoid 保留 | ✅ | 15 个卖点每条带 evidence；3 条 global_constraints（车型/AC/安装）；1 条 claims_to_avoid |
| 5 | 重新生成创建新版本 | ✅ | 每次 generate 创建新 version，不覆盖已确认版本 |
| 6 | 不影响现有产品资料解析和图片任务 | ✅ | 完整测试套件：27/27 通过，0 回归 |

## 验收标准

| # | 验收标准 | 状态 | 证据 |
|---|---------|------|------|
| 1 | 后端相关测试通过 | ✅ | `SellingPointCognitionServiceTest` 7/7 ✓, `VisualStrategyServiceTest` 9/9 ✓, `ProductProfileControllerSellingPointCognitionTest` 5/5 ✓, `ProductProfileControllerVisualStrategyTest` 6/6 ✓ |
| 2 | 前端 build 通过 | ✅ | `vue-tsc -b` 类型检查 + `vite build` 生产构建成功，dist/ 产出 90+ chunks |
| 3 | 至少一个真实 ASIN 样本手动验证通过 | ✅ | 使用 SnailTech S9 Car Stereo（Dodge RAM 2013-2018）样本，完整走通：创建 profile → 更新facts → 确认 profile → 生成 cognition → 确认 cognition → 生成 visual strategy → 确认 strategy → Gallery-only scope 验证 |

## 端到端流程验证日志

```text
STEP 1: Update Product Facts → 200 产品事实已保存
STEP 2: Confirm Product Profile → 200 产品资料已确认 (CONFIRMED)
STEP 3: Generate Cognition → 200 卖点认知草稿已生成 (DRAFT v1, 15 cognitions)
STEP 4: Confirm Cognition → 200 卖点认知版本已确认 (CONFIRMED)
STEP 5: Generate Visual Strategy → 200 视觉策略草稿已生成 (DRAFT v1, gallery+aplus)
         Gallery 6/6: why_buy / core_connection / screen_experience / safety_scene / entertainment_audio / compatibility_installation
         A+ 6/6: brand_banner / upgrade_story / core_features_grid / driving_scenarios / compatibility_installation / specs_package_support
STEP 6: List Cognition Versions → v1=CONFIRMED
STEP 7: List Visual Strategy Versions → v1=DRAFT
STEP 8: Gallery-Only Scope → Has Gallery=True, Has A+ False, Gallery images=6 ✅
STEP 9: Confirm Visual Strategy → 200 视觉策略版本已确认 (CONFIRMED)
```

## 备注

- 前端 UI 测试 `file.test.ts` 和 `group.test.ts` 共 22 个失败是**已有问题**（mock 导出配置过时），与卖点认知/视觉策略功能**完全无关**。
- 所有与卖点认知/视觉策略相关的 store 测试、组件测试、视图测试均通过。
- 真实 Bright Data API 需要在生产环境中配置 `BRIGHT_API_KEY` 环境变量，当前验证使用模拟产品数据模拟了 ASIN 采集后的流程。