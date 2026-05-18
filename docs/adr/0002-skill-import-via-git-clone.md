# 技能导入方式从 npx skills 切换为 git clone

`npx skills add` 对外部工具和 GitHub API 有隐式依赖，在大陆网络环境下稳定性差，且不可控因素多（npx 缓存、npm 源、skills CLI 版本）。决定放弃 npx，改用 git clone + gh-proxy 加速的方式直接下载技能仓库。

Status: accepted

## Considered Options

- **npx skills（原有方案）**：通过 `npx skills add <org/repo> --skill <name>` 下载。**拒绝原因**：依赖 npx 和 npm 生态，gh-proxy 无法加速，网络不稳定，且 npx skills CLI 本身有版本兼容风险。
- **git clone + gh-proxy（选定）**：解析 GitHub URL 后通过 `git clone https://gh-proxy.org/https://github.com/{owner}/{repo}.git` 下载，扫描 SKILL.md 导入。**选定原因**：git 协议成熟稳定，gh-proxy 可配置，不依赖第三方 CLI 工具，全量扫描和单技能导入均支持。
- **GitHub API ZIP 下载**：用 GitHub Archive API 下载 ZIP。**拒绝原因**：API 有频率限制，无法利用 gh-proxy 加速，且不支持大仓库。

## Consequences

- 原有 `npx skills add` 导入的技能不受影响，仍然可用
- 用户在 `application.properties` 中配置 `skill.gh-proxy-url`，默认 `https://gh-proxy.org`
- 前端输入框从 skills.sh URL 改为 GitHub URL，不再枚举 7 个来源
- ZIP 上传功能保持不动，与 git clone 并行，提供完整的 ZIP 目录格式说明
- 用户需安装 Git，未安装时引导到 `https://git-scm.com/downloads`
- `skills-lock.json` schema 升级到 version 2，添加 `commitHash`、`updatedAt`、`skillPath` 字段
