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
3. 在本地通过 Android 构建/单测、服务端测试/类型检查；
4. 填写变更范围、兼容性、数据影响、风险、验证结果和回滚方案；
5. 涉及协议、数据、权限、安全或发布配置时，明确请求对应领域负责人审查。
6. 完成项目更迭时，同步更新 `CHANGELOG.md` 和 [Release list](docs/RELEASE-HISTORY.md#release-list)；发布版本还必须核对 annotated tag、合并提交、GitHub Release 和制品校验和（详见 [每次发布完成后的最小核对清单](#每次发布完成后的最小核对清单)）。

合并规则：

- `main` 和 `release/*` 禁止直接 push、强制推送和删除；
- 必须通过 CI：`Android Build & Test`、`Server Tests`、服务端类型检查；
- 至少 1 名维护者批准；安全、协议或数据变更至少 2 名审批者，其中包含领域负责人；
- 所有评论解决，旧审批在新增提交后失效；
- 分支必须基于最新目标分支，合并使用 Squash；合并后自动删除源分支；
- CI 使用固定 Action 主版本，依赖和权限按最小权限配置。

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

## 每次发布完成后的最小核对清单

- [ ] `CHANGELOG.md` 已新增版本条目；
- [ ] [Release list](docs/RELEASE-HISTORY.md#release-list) 已记录版本、tag、合并提交和 Release 状态；
- [ ] tag 为 annotated tag，且指向合并后的 `main` 提交；
- [ ] GitHub Release 已创建并上传制品与 `SHA256SUMS`；
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

详细命令和测试清单见 `.opencode/skills/branch-guard/SKILL.md`。
