# 任务 03：卖点认知 API

## 目标

为卖点认知地图提供 REST API，供产品详情页 Tab 调用。

## API

- `POST /api/product-profiles/{id}/selling-point-cognitions/generate`
- `GET /api/product-profiles/{id}/selling-point-cognitions/current`
- `GET /api/product-profiles/{id}/selling-point-cognitions/versions`
- `PUT /api/product-profiles/{id}/selling-point-cognitions/{versionId}`
- `POST /api/product-profiles/{id}/selling-point-cognitions/{versionId}/confirm`

## 验收标准

- Controller 使用当前用户 ID 权限边界。
- 返回统一 `ApiResponse`。
- 不允许跨产品更新版本。
- 不允许确认空 JSON。
## 实施记录

状态：已完成

实际路由前缀：`/v1/product-profiles`，沿用现有 `ProductProfileController` 约定。

已新增/修改文件：

- `EcomAgents/src/main/java/cafe/snails/ecomagents/controller/ProductProfileController.java`
- `EcomAgents/src/test/java/cafe/snails/ecomagents/controller/ProductProfileControllerSellingPointCognitionTest.java`

已实现 API：

- `POST /v1/product-profiles/{id}/selling-point-cognitions/generate`
- `GET /v1/product-profiles/{id}/selling-point-cognitions/current`
- `GET /v1/product-profiles/{id}/selling-point-cognitions/versions`
- `PUT /v1/product-profiles/{id}/selling-point-cognitions/{versionId}`
- `POST /v1/product-profiles/{id}/selling-point-cognitions/{versionId}/confirm`

验证命令：

```bash
gradle --no-daemon compileJava
gradle --no-daemon test --tests cafe.snails.ecomagents.controller.ProductProfileControllerSellingPointCognitionTest --tests cafe.snails.ecomagents.service.SellingPointCognitionServiceTest --tests cafe.snails.ecomagents.service.ProductProfileServiceParseTest
```

验证结果：通过。