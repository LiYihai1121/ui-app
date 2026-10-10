# 项目开发规则

本文件是本仓库后续开发的默认执行规则；详细说明以 [CONTRIBUTING.md](CONTRIBUTING.md) 为准。

## 分支

- 禁止直接在 `main` 上提交、推送或强制改写历史。
- 每项变更从最新 `main` 创建独立短生命周期分支，前缀 ∈ `feature` / `fix` / `docs` / `ci` / `test` / `refactor`，形如 `<type>/<id>-<slug>`；发布用 `release/vX.Y.Z`，线上紧急修复用 `hotfix/<id>-<slug>`。
  前缀清单以 `RepoHygieneTest` 强制的为准（少列会让 Agent 建出被摘要误导的分支，多列则会被门禁判红），逐类用途见 [CONTRIBUTING.md](CONTRIBUTING.md)「分支策略」。
- 分支只处理一个需求，关联 Issue；合并后删除源分支。
- 不提交密钥、签名文件、构建产物、运行时数据或个人环境配置；`.vscode/` 只入库 `settings.json` 与 `extensions.json`（其余个人文件由 `ProjectStructureTest` 判红）。

## 提交

- 使用 Conventional Commits：`<type>(<scope>): <description>`，标题不超过 72 个字符。
- 每个提交保持单一意图，正文说明原因、影响和验证方式。
- 破坏性变更必须标记 `!` 或包含 `BREAKING CHANGE:`，并同步更新协议/API 文档。
- 不改写已推送的共享分支历史，不使用 `WIP`、`tmp`、`update` 等无意义标题。

## 验证与合并

- 修改完成后必须运行与变更相关的最小测试；跨模块变更运行完整门禁：
  - `cd client && ./gradlew ktlintCheck`
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

## 多 Agent 协作

细则见 [docs/development/AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md)，本节为执行摘要。

- **一人一工作区**：每个 Agent 用真正的 `git worktree`（落点 `.worktrees/<agent>-<slug>/`，内有 `.git` 元数据）；禁止在仓库内复制整仓当工作区。主检出禁止直接提交，由 `githooks/pre-commit` 机器拦截（每个克隆执行 `git config core.hooksPath githooks` 启用；紧急逃生口 `ADSKIP_ALLOW_MAIN_COMMIT=1`，事后须在 PR 说明）。
- **忽略规则要共享**：Agent 产物目录（`.kilo/`、`.kilocode/`、`.worktrees/`、`.agents/`、`.mimosa/`、`.workbuddy/`、`.cursor/`）必须写进根 `.gitignore`，不得只藏在本机 `.git/info/exclude`——否则会出现「git 干净但文件检索仍命中」的双重真相。
- **唯一写入者**：同一文件同一时间只有一个 Agent 写；开工前在协作规范的认领板登记路径；`CHANGELOG.md`、`ROADMAP*.md`、`ARCHITECTURE.md`、`docs/README.md` 为单写者事实源。
- **一任务一分支**：分支与 Agent 一一绑定，禁止共用分支；禁止对他人分支 rebase/reset/force-push/amend。
- **契约先行**：跨包、跨端边界的改动必须同时给出可执行契约测试（`ArchitectureBoundaryTest` / `ManifestContractTest` / `ProjectStructureTest` / `RepoHygieneTest`），先写失败的测试再写实现。
- **目录结构**：`client/` 与仓库根的目录布局由 `ProjectStructureTest` 强制；新增根级条目需先更新其白名单并在 PR 说明理由，细则见 [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) 第 2.2 节。
- **自跑门禁并交接**：各自跑通相关门禁，交接时给出「改了什么 / 边界变化 / 验证数字 / 遗留风险 / 下一步命令」五项。

## 文档与规划

- 文档入口与事实源见 [docs/README.md](docs/README.md)：版本序列以 `docs/planning/ROADMAP.md` 为准，里程碑与出口条件见 `docs/planning/ROADMAP-ADS.md`，技术步骤见 `docs/planning/DESIGN-PHASE1-SELECTOR.md`，发布链路见 `docs/planning/RELEASE-HISTORY.md`。
- 改规划按「ROADMAP → ROADMAP-ADS / DESIGN → CHANGELOG / RELEASE-HISTORY」顺序更新；新增、改名或删除文档必须登记到文档地图。
- 涉及协议或数据形态变更时，同步更新 `docs/api/API.md` 与 `docs/architecture/ARCHITECTURE.md`。

每次开发开始前先确认分支和工作区状态；每次修改后先验证，再提交或创建 PR。
