# 开发流程合规审计与流程加固（FOLLOW-UP-PROCESS-AUDIT）

> 状态：审计完成，加固项随本提交落地；最后更新：2026-10-11。
> 审计范围：v2.0（2026-08-24 初始提交）至 v3.4.0 后（2026-10-11）全部 102 个 main 提交、13 个版本标签、开放 PR 与发布记录。审计口径为仓库自订规则（[CONTRIBUTING.md](../../CONTRIBUTING.md)、[AGENTS.md](../../AGENTS.md)、[AGENT-WORKFLOW.md](../development/AGENT-WORKFLOW.md)），全部可机械验证。

## 1. 审计发现

| # | 发现 | 证据 | 严重度 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | **58% 的 main 提交无 PR 关联**（59/102 主题不含 `(#N)`），违反「所有改动经 PR 合并」 | `git log` 主题扫描 | 高 | 流程加固 A2 + 钩子（`ci/agent-guardrails` / #96）+ 本审计后执行 |
| 2 | **主分支历史曾被重写**：2026-08-30 一次「版本历史整理」产生 10 个 merge commit 与「8 个逻辑提交，零 merge noise」的重排 | `git log --merges` 2026-08-30 批次 | 高 | 列入历史瑕疵（见第 2 节豁免线）；加固 A3 禁止再现 |
| 3 | **v3.0.0 与 v3.0.1 指向同一提交**（`b7ebabb`），且 v2.0.0–v3.0.1 五个标签全部为 2026-09-04 同日追溯补打 | `git tag --sort=creatordate` | 高 | 列入历史瑕疵（标签不可移动，不追溯）；加固 A4 固化「一 commit 一标签」 |
| 4 | **提交标题屡次超 72 字符**（≥5 例，含 #82/#83 等近期提交） | 主题长度扫描 | 中 | 加固 A1：commit-msg 钩子机器拦截 |
| 5 | **非 Conventional Commits 格式 ≥13 例**（`v3.2.0：…`、`build+ui: …`、11 个 `merge: …`） | 主题正则扫描 | 中 | 加固 A1 |
| 6 | **main 上存在 15 个 merge commit**（含 3 个发布 back-merge 未走 PR） | `git rev-list --merges` | 中 | 加固 A3：main 仅接受 squash 产生的单线性历史 |
| 7 | versionCode 序列合规 ✅（10→12→13→14 单调递增）；BREAKING 包名迁移落在 minor（3.4.0）符合 Android `versionName` X.Y 约定（已文档化） | 构建配置历史 | — | 无需处置 |
| 8 | CHANGELOG 与 RELEASE-HISTORY 对 13 个标签的覆盖完整 ✅；各发布制品可追溯到 tag + commit ✅（v3.0.x 同提交双标签除外，见 #3） | 逐标签核对 | — | 无需处置 |

## 2. 历史豁免线（不追溯原则）

- 上述 #2、#3 发生在流程文档定稿与机器门禁建立之前，且**标签与制品一经推送不可移动**是既定规则——对历史瑕疵做追溯性修复（改标签、重写历史）的代价与风险高于其收益。
- **豁免线划在 `v3.1.0`（2026-09-29，tag `ad188b0`）**：自该版本起，发布记录完整、制品签名链路生效、本审计的加固项生效。v3.1.0 之前的格式类瑕疵（#1/#4/#5/#6 的历史实例）不再逐条修复。
- 本豁免线是**一次性**的：此后任何新提交不适用。

## 3. 流程加固（本提交落地，机器优先）

| # | 加固项 | 机制 | 状态 |
| --- | --- | --- | --- |
| A1 | 提交标题机器校验：Conventional Commits 格式 + ≤72 字符 | `githooks/commit-msg`（merge 提交豁免；逃生口与 pre-commit 一致） | 本提交 |
| A2 | 主检出提交拦截 | `githooks/pre-commit` | 已落地（#96） |
| A3 | main 历史线性：合并一律 squash（远端 PR 流程天然保证）；本地禁止产生 merge commit 入 main | CONTRIBUTING「版本控制最佳实践」增补 | 本提交 |
| A4 | 标签唯一性：一个 commit 不得承载多个版本标签；发布 bump 提交必须经 PR（禁止在主检出直推 release 提交） | CONTRIBUTING「供应链与可追溯」增补 | 本提交 |

## 4. 重规划后的完整开发流程（现行版）

1. **需求**：Issue 立项（模板）→ 认领板登记（AGENT-WORKFLOW 3.2）；
2. **开发**：独立 worktree（`.worktrees/<agent>-<slug>`）→ `pre-commit` 拦截主检出 → `commit-msg` 校验格式与长度 → 契约先行（跨边界改动先写失败测试）；
3. **验证**：`ktlintCheck` + `assembleDebug` + `testDebugUnitTest` + `bun test` + `bun run typecheck` 自跑全绿；
4. **合并**：PR（多 Agent 协作检查三项）→ CI 全门禁 → Squash Merge（main 历史保持单线性，每个提交天然携带 `(#N)`）→ 认领板状态更新；
5. **发布**：`release/vX.Y.Z` 分支 bump 版本（单独提交、经 PR）→ tag `vX.Y.Z` 唯一指向 → CI 签名 + `SHA256SUMS` → RELEASE-HISTORY / CHANGELOG 同步；
6. **治理**：本审计每季度复跑一次（同口径机械扫描），偏差进 FOLLOW-UP。

## 5. 与其他治理工件的关系

- 钩子基础设施（`core.hooksPath`、主检出拦截）由 [#96](https://github.com/LiYihai1121/ui-app/pull/96) 建立，本提交在其上追加 `commit-msg`；
- 认领板与协作事件记录见 [#95](https://github.com/LiYihai1121/ui-app/pull/95)；架构演进（R1–R3）见 [#101](https://github.com/LiYihai1121/ui-app/pull/101)；
- 本文档为**点时审计报告**，不承载常驻规则——常驻规则一律进 CONTRIBUTING（本提交已增补），避免两份真相。
