# 贡献与版本管理规范

本项目采用企业级 GitHub Flow：`main` 是唯一受保护的集成主干，所有变更必须通过 Pull Request（PR）进入；发布使用短生命周期的 `release/*` 分支和不可变版本标签。规范的目标是让每次变更可审计、可复现、可回滚。

## 分支模型

从最新 `main` 创建分支，分支名使用以下格式：

| 分支 | 用途 | 生命周期 | 规则 |
| --- | --- | --- | --- |
| `main` | 可发布集成主干 | 长期 | 禁止直接 push、强制推送和本地提交 |
| `feature/<id>-<简述>` | 新功能 | 短期 | 从最新 `main` 创建，一个分支只对应一个需求 |
| `fix/<id>-<简述>` | 缺陷修复 | 短期 | 必须关联 Issue 或事故记录 |
| `release/vX.Y.Z` | 发布冻结与验收 | 发布期间 | 只允许版本、文档、阻断性缺陷修复 |
| `hotfix/<id>-<简述>` | 生产紧急修复 | 尽快合并 | 发布后必须回合并 `main` 和维护中的 release 分支 |

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/123-rule-simulator
```

分支名使用小写、短横线和可追踪的 Issue ID；合并后删除本地及远端分支。严禁提交密钥、签名文件、构建产物、运行时数据和个人环境配置。

> **门禁只覆盖格式，不覆盖可追踪性**：`RepoHygieneTest` 仅校验分支名符合 `type/<slug>` 形状，
> **不校验 `<id>` 是否对应真实 Issue**。因此「是否关联了可追踪的 Issue」仍是评审的人工责任，
> 不能因为 CI 变绿就认为分支名已合规。

## 提交规范

提交信息采用 Conventional Commits，标题不超过 72 个字符：

```text
<type>(<scope>): <简短描述>
```

允许的 `type`：`feat`、`fix`、`refactor`、`perf`、`test`、`docs`、`build`、`ci`、`chore`、`revert`。破坏性变更必须在标题追加 `!` 或正文使用 `BREAKING CHANGE:`，并同步更新 API/迁移文档。

要求：

- 每个提交保持单一意图，确保可以独立回滚；
- 提交正文说明变更原因、影响范围和验证方式；
- 不使用 `WIP`、`tmp`、`update` 等无法表达意图的提交标题；
- PR 合并采用 Squash Merge，生成一个规范提交；紧急修复允许 Rebase Merge；禁止无意义的 merge commit；
- 不改写已推送的共享分支历史。

示例：

```text
feat(server): 增加规则发布审计日志
fix(client): 避免重复触发跳过点击
```

## Pull Request 门禁

创建 PR 前，提交者必须：

1. 从最新 `main` 创建分支并关联 Issue；
2. 完成自审，确认无敏感信息、无无关改动和无新增诊断；
3. 在本地**自行执行** [AGENTS.md](AGENTS.md)「验证与合并」列出的全部门禁命令（Android 构建、单测、服务端测试、类型检查）——不把验证推给 CI；本机环境见 [docs/development/DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md)；
4. 填写变更范围、兼容性、数据影响、风险、验证结果和回滚方案；
5. 涉及协议、数据、权限、安全或发布配置时，明确请求对应领域负责人审查。
6. 完成项目更迭时，同步更新 `CHANGELOG.md` 和 [Release list](docs/planning/RELEASE-HISTORY.md#release-list)；发布版本还必须核对 annotated tag、合并提交、GitHub Release 和制品校验和（详见 [每次发布完成后的最小核对清单](#每次发布完成后的最小核对清单)）；涉及规划或文档改动时，按 [docs/README.md](docs/README.md) 声明的事实源顺序同步。

合并规则：

- `main` 和 `release/*` 禁止直接 push、强制推送和删除；
- 必须通过 CI：`Structure Contract`、`Android Build & Test`、`Server Tests`、服务端类型检查；
- 至少 1 名维护者批准；安全、协议或数据变更至少 2 名审批者，其中包含领域负责人；
- 所有评论解决，旧审批在新增提交后失效；
- 分支必须基于最新目标分支，合并使用 Squash；合并后自动删除源分支；
- CI 使用固定 Action 主版本，依赖和权限按最小权限配置。

## 多 Agent 并行开发

单人开发时按上面的分支模型即可。当**多个 Agent（或多名协作者）同时在一个仓库工作**时，额外遵守 [docs/development/AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md)，核心是四点：

1. **隔离**：每个 Agent 一个真正的 `git worktree`（`.worktrees/<agent>-<slug>/`），禁止在仓库内复制整仓；Agent 产物目录必须写进共享 `.gitignore`，不得只写本机 `.git/info/exclude`。
2. **所有权**：同一文件同一时间只有一个写入者（开工前认领），`CHANGELOG.md`、`docs/planning/ROADMAP*.md`、`docs/architecture/ARCHITECTURE.md`、`docs/README.md` 为单写者事实源。
3. **契约**：跨包/跨端边界的改动必须附带可执行契约测试（客户端由 `ArchitectureBoundaryTest`、`ManifestContractTest`、`ProjectStructureTest`、`RepoHygieneTest` 守护），先写失败测试再写实现；目录布局本身也是契约，新增根级文件/目录需先更新 `ProjectStructureTest` 白名单并在 PR 说明理由（细则见 [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) 第 2.2 节）。
4. **交接与合并**：分支与 Agent 一一绑定，禁止对他人分支 rebase/force-push；交接给出「改了什么 / 边界变化 / 验证结果 / 遗留风险 / 下一步」五项；合并仍走 PR + Squash，顺序上先合依赖方。

违反上述约定时，仓库卫生类问题会由 `RepoHygieneTest` 直接判失败（如仓库内出现无 `.git` 元数据的整仓副本、忽略规则缺失、协作文档未登记、分支名不合规）。

## 版本策略与发布

版本号遵循 Semantic Versioning：`MAJOR.MINOR.PATCH`。

- `MAJOR`：不兼容的 API、协议、数据格式或行为变更；
- `MINOR`：向后兼容的新功能；
- `PATCH`：向后兼容的缺陷、安全或性能修复；
- 预发布版本使用 `X.Y.Z-rc.N`，不得覆盖正式版本号。

版本发布必须遵循以下顺序：

1. 从 `main` 创建 `release/vX.Y.Z`，冻结功能并更新 [CHANGELOG.md](CHANGELOG.md)；
2. 以发布标签 `vX.Y.Z` 为规范版本；Android `versionName` 使用对应的 `X.Y` 展示值，单调递增 `versionCode`，服务端 `package.json` 使用完整 `X.Y.Z`；
3. 通过完整 CI、发布验收和安全检查；
4. 合并到 `main` 后创建带注释的、不可移动的 `vX.Y.Z` 标签；
5. 由 CI 根据标签生成制品和 Release，记录制品校验和；
6. 发布后观察关键指标，出现问题优先回滚制品；修复代码再通过 hotfix 发布。

版本标签一经推送不得删除或移动。版本号变更不能与无关功能混在同一个 PR 中。Android `versionCode` 必须全局单调递增，禁止复用已发布编号。发布使用带注释的 Git tag：

```bash
git tag -a vX.Y.Z -m "release: vX.Y.Z"
git push origin vX.Y.Z
```

### 发布验收

CI 只能证明「编译通过、逻辑符合契约」，**不能证明「在真实厂商 ROM 上可用」**。触及下列范围的变更，必须在真机完成验收后才能发正式版；否则只允许发 `X.Y.Z-rc.N` 预发布版本：

- 触及范围：无障碍服务、快捷磁贴、厂商保活跳转、`Intent` 跳转、后台任务调度、系统权限；
- 最低机型覆盖：Android 8（minSdk 26 下界）与当前最高 targetSdk 机型；
- 厂商覆盖：MIUI、HarmonyOS/EMUI、ColorOS、OriginOS（保活入口表覆盖的厂商逐一验证跳转与降级）；
- 验收项：磁贴添加与移除、冷进程下启用/禁用状态显示、磁贴启停服务、跳转系统设置、各厂商入口可用性与通用降级路径。

## 纯工程变更不需要版本号

只改动构建配置、测试、文档、CI 或内部重构（不产生用户可见能力）时，**不升版本号、不占用 `X.Y.Z`**，在 [docs/planning/ROADMAP.md](docs/planning/ROADMAP.md)「候选池」登记即可。这与「按是否产生用户可见能力切分小版本」的版本原则一致，避免工程支出稀释版本序列。

## 每次发布完成后的最小核对清单

- [ ] `CHANGELOG.md` 已新增版本条目；
- [ ] [Release list](docs/planning/RELEASE-HISTORY.md#release-list) 已记录版本、tag、合并提交和 Release 状态；
- [ ] tag 为 annotated tag，且指向合并后的 `main` 提交；
- [ ] GitHub Release 已创建并上传制品与 `SHA256SUMS`；
- [ ] **制品已用 `apksigner verify` 校验签名并确认可安装**——未签名包在手机上必然报「解析软件包时出现问题」（Issue #23）；命令见 [DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md#apk-安装排障)；
- [ ] 触及系统集成范围（无障碍/磁贴/跳转/调度/权限）时，真机验收已完成并记录机型与 ROM 版本；
- [ ] 已确认没有复用或移动历史 tag。

## 紧急变更与回滚

生产故障可从最新生产标签创建 `hotfix/*`，PR 描述必须包含事故编号、影响范围、缓解措施和回滚点。紧急变更仍需至少一名维护者批准并通过最小 CI；发布后必须补齐完整测试、变更记录和复盘，并回合并所有维护分支。

回滚优先选择已验证的上一版本制品或 `git revert`，禁止在受保护分支上 reset、force-push 或删除历史标签。涉及数据库或协议的回滚必须提供向后兼容方案。

## 仓库管理员配置

远端仓库应对 `main` 启用以下保护：

- 配置 `main`、`release/*` 分支保护和 CODEOWNERS；
- 配置必需状态检查、审批人数、旧审批失效和合并后删分支；
- 开启 Dependabot/Renovate，依赖升级走 PR 并保留 lockfile；
- 发布凭据使用 CI Secret/OIDC，禁止写入仓库和日志；
- Release、APK、日志和测试报告保留周期明确，生产制品可追溯到 commit/tag。

## 命令与清单的唯一事实源

本文件是**流程规范**（该做什么、什么条件下允许合并与发版），不复述命令清单——命令会随工具链版本漂移，复制出去的清单会静默变成第二份真相。各命令的唯一事实源：

| 内容 | 唯一事实源 |
| --- | --- |
| 门禁命令（提交前必须自跑的四条） | [AGENTS.md](AGENTS.md)「验证与合并」 |
| 本机工具链、SDK、JDK 与排障步骤 | [docs/development/DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md) |
| 目录/架构契约的判定细则 | [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) 第 2.1/2.2 节 |
| 并行协作的隔离与交接细则 | [docs/development/AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md) |

> 新增命令一律加到上表对应的事实源，本文件只保留指针。
