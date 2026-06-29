# 任务 05：视觉策略 API

## 目标

为视觉策略提供 REST API，供产品详情页视觉策略 Tab 调用。

## API

- `POST /api/product-profiles/{id}/visual-strategies/generate`
- `GET /api/product-profiles/{id}/visual-strategies/current`
- `GET /api/product-profiles/{id}/visual-strategies/versions`
- `PUT /api/product-profiles/{id}/visual-strategies/{versionId}`
- `POST /api/product-profiles/{id}/visual-strategies/{versionId}/confirm`

## 验收标准

- Controller 使用当前用户 ID 权限边界。
- 支持生成范围选择。
- 不允许跨产品操作版本。
- 返回统一 `ApiResponse`。
## 实施记录

状态：已完成

实际路由前缀：`/v1/product-profiles`，沿用现有 `ProductProfileController` 约定。

已新增/修改文件：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/dto/VisualStrategyGenerateRequest.java`
- `EcomAgents/src/main/java/cafe/snails/ecomagents/controller/ProductProfileController.java`
- `EcomAgents/src/test/java/cafe/snails/ecomagents/controller/ProductProfileControllerVisualStrategyTest.java`
- `EcomAgents/src/test/java/cafe/snails/ecomagents/controller/ProductProfileControllerSellingPointCognitionTest.java`

已实现 API：

- `POST /v1/product-profiles/{id}/visual-strategies/generate`
- `GET /v1/product-profiles/{id}/visual-strategies/current`
- `GET /v1/product-profiles/{id}/visual-strategies/versions`
- `PUT /v1/product-profiles/{id}/visual-strategies/{versionId}`
- `POST /v1/product-profiles/{id}/visual-strategies/{versionId}/confirm`

生成请求体：

```json
{
  "cognition_version_id": 789,
  "content_scope": ["gallery", "aplus"]
}
```

说明：请求体可为空；为空时使用最新已确认 cognition version，并默认生成全套 gallery + aplus。

验证命令：

```bash
gradle --no-daemon compileJava
gradle --no-daemon test --tests cafe.snails.ecomagents.controller.ProductProfileControllerVisualStrategyTest --tests cafe.snails.ecomagents.controller.ProductProfileControllerSellingPointCognitionTest --tests cafe.snails.ecomagents.service.VisualStrategyServiceTest --tests cafe.snails.ecomagents.service.SellingPointCognitionServiceTest --tests cafe.snails.ecomagents.service.ProductProfileServiceParseTest
```

验证结果：通过。