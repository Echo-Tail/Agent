# 公共素材库开发进度

## 分支

所有开发在 `feature/new-edit` 分支的 git worktree 中：
```
D:\hermes\github\Agent-feature\
```

## 已完成

### 后端 (Spring Boot)

| 文件 | 说明 |
|---|---|
| `model/AssetSpace.java` | 素材空间实体，字段：id, name(unique), description, createdBy, createdAt, updatedAt |
| `model/PublicAsset.java` | 公共素材实体，字段：id, fileName, filePath, fileSize, mimeType, space(FK), uploadedBy, createdAt |
| `repository/AssetSpaceRepository.java` | `findByName()`, `existsByName()` |
| `repository/PublicAssetRepository.java` | Native query 搜索 `search()` + countQuery，支持 spaceId/keyword/uploadedBy 筛选 |
| `service/AssetService.java` | 素材空间 CRUD、素材上传/删除、从生成记录导入 |
| `controller/AssetController.java` | REST API `/v1/assets/*` |
| `config/SecurityConfig.java` | 新增 `.requestMatchers("/v1/assets/**").authenticated()`，移除 gallery 路由 |

#### API 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/assets/spaces` | 列表所有素材空间 |
| POST | `/v1/assets/spaces` | 创建空间 |
| PUT | `/v1/assets/spaces/{id}` | 修改空间 |
| DELETE | `/v1/assets/spaces/{id}` | 删除空间（素材孤立） |
| GET | `/v1/assets` | 分页查询素材，参数：spaceId, keyword, uploadedBy, page, size |
| POST | `/v1/assets/upload` | 上传素材（multipart），校验 jpeg/png/webp |
| DELETE | `/v1/assets/{id}` | 删除素材（上传者或管理员） |
| POST | `/v1/assets/from-record/{recordId}` | 从生成记录导入素材库 |

### 前端

| 文件 | 说明 |
|---|---|
| `api/assets.ts` | API 服务，全部端点封装 |
| `views/assets/AssetLibraryView.vue` | 素材库主页面 |
| `router/index.ts` | `agents/gallery` → `agents/assets` |
| `layouts/DefaultLayout.vue` | 侧边栏：Gallery → PublicAssets，图标 ImagePlus，nav 名 "素材库" |
| `locales/zh-CN.json` | 新增 `nav.publicAssets: "素材库"` + `assetLibrary.*` 全部中文 |
| `locales/en.json` | 新增 `nav.publicAssets: "Assets"` + `assetLibrary.*` 全部英文 |

## 遗留问题

### ✅ 1. 上传素材 Dialog 交互改造 + 文件夹式导航

交互流程已就绪，上传新增进度反馈 + 缩略图预览 + 轮播放大：

- 基础流程：点击 → Dialog → 选文件 → 选/建空间 → 上传 → 刷新 ✅
- 上传进度：逐文件 Axios `onUploadProgress` 回调，底部显示进度条 + "当前/总数" ✅
- 缩略图预览：选中文件后显示前 3 张缩略图（`URL.createObjectURL`），超出部分显示 "+N" 折叠 ✅
- 放大查看：每张缩略图 hover 显示放大按钮 (ZoomIn)，点击打开 Carousel Dialog 全尺寸轮播浏览 ✅
- Carousel 组件集：新增 `src/components/ui/carousel/` 基于 embla-carousel 8.6 ✅
- 编译错误已修复 ✅
- 文件夹式导航：首页显示空间卡片（名称 + Folder 图标）→ 点击进入空间 → 面包屑路径 + 返回按钮 ✅
- 空间内素材网格支持 hover 放大预览（ZoomIn 图标）+ 点击打开全尺寸预览（左右切换） ✅
- 上传 Dialog 自动选中当前空间（在空间内时） ✅

### ✅ 2. 图生图集成

**入口 1 — 编辑时选素材** ✅
- 参考图区增加"从素材库选择"按钮
- 弹出素材浏览器 Dialog（缩略图网格 + 搜索）
- 选中后 `fetch(url) → blob → File` 追加到参考图列表

**入口 2 — 生成后存素材** ✅
- 结果卡片"发布到画廊" → "上传素材"按钮
- 点击弹出空间选择 Dialog
- 调 `/v1/assets/from-record/{recordId}` 存入素材库

### 3. Gallery 清理（未开始）

- 后端：删除 `GalleryController`、`GalleryService`、`GalleryItemRepository`、`GalleryItem` 实体、相关 DTO
- 前端：删除 `GalleryView.vue`、`api/gallery.ts`、路由 `/agents/gallery`
- 移除 `ImageGenerationView.vue` 历史记录里的"发布到画廊"按钮和 publishDialog
- 删除 `gallery_items` 表

### ✅ 4. 编译错误（已修复）

`AssetLibraryView.vue` 第 145-147 行存在正则表达式转义问题和 SFC 结构错误（`</script>` 标签嵌套在函数体内 + 重复）。已修复：
- `imageUrl()` 正则 `/\/g` → `/\\\\/g`
- 恢复缺失的函数闭合 `}`
- 合并重复的 `</script>` 标签
- 移除死代码 `openCreateSpace` 空函数 + 未定义的空间管理 Dialog
- `npm run build` 通过 ✅
