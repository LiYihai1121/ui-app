# 项目开发规则

本文件是本仓库后续开发的默认执行规则；详细说明以 [CONTRIBUTING.md](CONTRIBUTING.md) 为准。

## 分支

- 禁止直接在 `main` 上提交、推送或强制改写历史。
- 每项变更从最新 `main` 创建独立短生命周期分支：`feature/<id>-<slug>`、`fix/<id>-<slug>`、`docs/<id>-<slug>`、`ci/<id>-<slug>`、`release/vX.Y.Z` 或 `hotfix/<id>-<slug>`。
- 分支只处理一个需求，关联 Issue；合并后删除源分支。
- 不提交密钥、签名文件、构建产物、运行时数据或个人环境配置。

## 提交

- 使用 Conventional Commits：`<type>(<scope>): <description>`，标题不超过 72 个字符。
- 每个提交保持单一意图，正文说明原因、影响和验证方式。
- 破坏性变更必须标记 `!` 或包含 `BREAKING CHANGE:`，并同步更新协议/API 文档。
- 不改写已推送的共享分支历史，不使用 `WIP`、`tmp`、`update` 等无意义标题。

## 验证与合并

- 修改完成后必须运行与变更相关的最小测试；跨模块变更运行完整门禁：
  - `cd client && ./gradlew assembleDebug`
  - `cd client && ./gradlew testDebugUnitTest`
  - `cd server && bun test`
  - `cd server && bun run typecheck`
- 提交 PR 前完成自审，说明变更范围、兼容性、风险、验证结果和回滚方案。
- `main` 与 `release/*` 必须通过 CI 和必要审查后合并；默认使用 Squash Merge。
- 安全、协议、数据或发布配置变更需要领域负责人审查；紧急修复也必须保留事故记录和回滚点。

## 版本发布

- 使用 Semantic Versioning：`MAJOR.MINOR.PATCH`；预发布版本使用 `X.Y.Z-rc.N`。
- 发布从 `main` 创建 `release/vX.Y.Z`，冻结功能并同步更新 Android `versionName`、全局单调递增的 `versionCode` 和 `server/package.json` 版本。
- 通过完整 CI 和发布验收后，创建不可移动的带注释标签 `vX.Y.Z`，制品必须可追溯到 commit/tag 并记录校验和。
- 禁止删除或移动已推送的版本标签；故障优先回滚已验证制品或使用 `git revert`，不得对受保护分支执行 reset 或 force-push。

每次开发开始前先确认分支和工作区状态；每次修改后先验证，再提交或创建 PR。
