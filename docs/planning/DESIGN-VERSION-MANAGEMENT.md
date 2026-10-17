# 版本控制与发布管理体系重设计（DESIGN-VERSION-MANAGEMENT）

> 状态：待评审；最后更新：2026-10-11。
> 本文档覆盖五个维度：分支模型、存量分支重构、版本号规范、发布工作流、实施路线图。它不另立规则——所有规则的事实源仍是 [CONTRIBUTING.md](../../CONTRIBUTING.md)；本文档是把这些规则组织成一套**可执行体系**的设计说明书，冲突时以 CONTRIBUTING 为准。前置事实：[FOLLOW-UP-PROCESS-AUDIT.md](FOLLOW-UP-PROCESS-AUDIT.md)（历史合规审计）、[#104](https://github.com/LiYihai1121/ui-app/pull/104)（CI 镜像修复）。
>
> **版本序列基线（2026-10-16 补注，必读）**：2026-10-13 仓库历史重置（`推倒重来`）后 SemVer **自 `1.0.0` 起重新计数**，当前已发布 `v1.0.1`（见 [RELEASE-HISTORY.md](RELEASE-HISTORY.md)）。本页第 3、5 节出现的 `3.x` / `v3.4.0` / `v3.5.0` / `v4.0.0` 等均为**重置前的历史示例与规划**，**规则本身**（版本号形态、递增语义、禁止事项、发布工作流）继续适用，但示例版本号不构成当前序列。另：§3.3「禁止跳号」的连续性自重置起有一次**显式豁免**——`v3.4.2` → `v1.0.0` 属「重置重新计数」，已记于 [RELEASE-HISTORY.md](RELEASE-HISTORY.md) 的「版本起点声明」，此后按新序列推导。「重置后各里程碑用什么版本号」尚未决策。

## 1. 分支管理策略：GitHub Flow + 发布分支（不用 GitFlow）

**选型结论**：保留现行「GitHub Flow + `release/vX.Y.Z`」并把它规则化，**不引入 GitFlow 的 `develop` 分支**。理由：单仓库双端、1–2 人 + 多 Agent 并行的规模下，`develop` 只会把「集成延迟」从 main 挪到 develop；现有 `ArchitectureBoundaryTest` + 三检查 CI 已把集成风险前置到 PR 内，长周期集成分支没有收益。

| 分支类型 | 命名 | 职责 | 生命周期 | 合并规则 |
| --- | --- | --- | --- | --- |
| `main` | — | 唯一真相分支：始终可发布、线性历史、每提交携带 `(#N)` | 永久 | **只接受 Squash Merge**；禁止 merge commit 直入；禁止直推（`pre-commit` 拦截 + 保护规则） |
| 特性分支 | `<type>/<id>-<slug>` | 一个需求一个分支，绑定 Issue | 合并后即删 | PR → 3 必需检查（Structure Contract / Android Build & Test / Server Tests）→ strict up-to-date → Squash |
| 发布分支 | `release/vX.Y.Z` | 从 main 切出做版本冻结：bump 版本、回归、rc | tag 打出、Release 发布后保留归档 | bump 单独提交经 PR 回 main；tag 打在发布分支的最终提交上 |
| 热修复分支 | `hotfix/<id>-<slug>` | 从**上一个发布 tag**切出修复线上问题 | 修复合回 main 并打新 patch tag | 同特性分支；事故记录进 RELEASE-HISTORY |
| Agent 工作区 | `.worktrees/<agent>-<slug>/` | 物理隔离（多会话并行） | 任务结束删除 | 主检出禁止直接开发（`pre-commit` 机器拦截） |

流转逻辑（一条特性的完整路径）：

```text
Issue 立项 → 认领板登记 → worktree 建分支 → 开发（commit-msg 校验 + 契约先行）
→ PR（协作检查三项）→ 3 必需检查 + strict up-to-date → Squash 进 main → 删分支 → 认领板更新
```

发布路径：

```text
main 冻结点 → cut release/vX.Y.Z → bump PR（chore(release) 单独提交）→ rc（可选，X.Y.Z-rc.N）
→ 全部门禁 + 真机验收 → annotated tag vX.Y.Z → release.yml 签名发布 → RELEASE-HISTORY/CHANGELOG 同步
```

## 2. 存量分支重构与清理（已完成动作 + 剩余步骤）

### 2.1 已执行（2026-10-11，本设计的前置工作）

历史与存量的结构性问题已在流程审计（FOLLOW-UP-PROCESS-AUDIT）中定案并处置：

1. **历史豁免线**：v3.1.0 之前的追溯贴标签（v2.0.0–v3.0.1 同日补打）、同提交双标签（v3.0.0/v3.0.1 → `b7ebabb`）、历史重写（2026-08-30 merge 批次）列为**已知历史瑕疵，不追溯修复**——标签与制品不可移动是更高优先级规则。
2. **分支清理**：远端分支 63 → 23。已删 43 个：9 个方向不符的实验分支、30 个 squash 前残留/被取代栈/已关 PR 头、4 个陈旧本地分支；9 个开放 PR 头与在建工作保留。
3. **保护规则**：strict up-to-date + 3 必需检查 + 无评审要求（契约测试即评审）——已实测拦截行为正确。

### 2.2 剩余清理步骤（按序执行）

1. **存量开放 PR 逐个裁决**（9 个）：按「内容是否已随版本发布 / 是否符合方向」关闭或合并——与 10-11 已完成的 6 个过时 PR 同口径；
2. **在建分支登记认领板**：`chore/89`、`fix/security-audit-remediation`、`fix/p0-release-sdk-alignment` 等由作者补登记，两周无动静按过时处理；
3. **worktree 惯例固化**：`git worktree prune` + 每季度清理无对应分支的目录；
4. **main 提交抽查**：豁免线（v3.1.0）之后的所有提交必须携带 `(#N)`——CI 侧可加一条只读校验，发现直推立即告警。

> 历史记录完整性原则：清理只删「分支指针」，**不重写任何已推送历史**；被删分支的提交在 GitHub 上 90 天内可经 support 恢复，重要内容以「先拆 Issue/PR 再删」的方式保留。

## 3. 版本号命名规范（SemVer 2.0.0 + Android 适配）

### 3.1 版本号形态

| 载体 | 形态 | 示例 | 规则 |
| --- | --- | --- | --- |
| Git tag / Release | `vMAJOR.MINOR.PATCH` | `v3.4.0` | annotated tag；**一个 commit 只承载一个 tag**；一经推送不可删除/移动 |
| 预发布 | `vX.Y.Z-rc.N` | `v3.1.0-rc.1` | 仅在 release 分支冻结后、正式 tag 前使用；N 递增 |
| Android `versionName` | `X.Y` | `3.4` | 展示值用两段（用户可见约定，已有先例）；与 tag 的 X.Y 段一致 |
| Android `versionCode` | 全局单调整数 | `15` | 禁止复用已发布编号；rc 也占用（v3.1.0-rc.1 = 11 的先例延续） |
| 服务端 `package.json` | `X.Y.Z` | `3.4.0` | 与 tag 完全一致；release.yml 校验三处一致 |
| BREAKING | **MAJOR 进位** | `4.0.0` | 见 3.2 的历史豁免说明 |

### 3.2 递增规则与历史豁免

- **PATCH**：纯修复（崩溃、安全补丁、发版链路问题），无新能力、无协议字段变化 → `v3.4.1`；
- **MINOR**：向后兼容的新能力或新协议字段（旧客户端行为不变，见 [API.md](../api/API.md)「协议演进」）→ `v3.5.0`；
- **MAJOR**：出现以下任一情况——`applicationId`/包名变更、协议不兼容（schemaVersion 淘汰旧字段）、数据存储格式不迁移、权限面变化 → `v4.0.0`。
- **历史豁免**：v3.4.0 的 `applicationId` BREAKING 落在 MINOR（`versionName` X.Y 约定的时期产物），已列入流程审计豁免线；**自本设计生效起，BREAKING 一律 MAJOR**，豁免不再延续。

### 3.3 禁止事项（承接既定规则）

- 禁止复用/移动/删除已推送 tag；禁止在非 main 提交上补建 Release；
- 禁止「重跑已存在的 tag」类操作（历史分支 `release-list-governance` 因此裁决删除）；
- 禁止跳号（下一个版本号必须与上一个发布版本按上述规则可推导）。

## 4. 标准化发布工作流

### 4.1 发布前置质量门（全绿才允许打 tag）

1. 代码门禁：`ktlintCheck`、`assembleDebug`、`testDebugUnitTest`（当前 248）、`bun test`（87）、`bun run typecheck`；
2. 结构契约：`ProjectStructureTest` / `RepoHygieneTest` / `ArchitectureBoundaryTest`（含于单测）；
3. 版本一致性：`versionCode`/`versionName`/`server package.json`/CHANGELOG 顶部条目四处一致；
4. 制品门禁：release.yml 强制 `apksigner verify`，未签名制品拒绝上传；`SHA256SUMS` 与制品核对；
5. 记录门禁：RELEASE-HISTORY 新行（tag/commit/Release/制品校验和）+ CHANGELOG 条目就位。

### 4.2 Tag 与 Release 规范

```bash
# 在 release/vX.Y.Z 的最终提交（经 PR 合入 main 后）打 tag
git tag -a vX.Y.Z -m "release: vX.Y.Z" && git push origin vX.Y.Z
```

- tag 由 release 分支的最终提交产生；CI（release.yml）按 tag 构建、签名、上传 GitHub Release + `SHA256SUMS`；
- Release 描述启用 GitHub 自动生成说明（「Generate release notes」按 PR 标题聚合）作为草稿，人工按 Keep a Changelog 分节（Added/Changed/Fixed/BREAKING）定稿。**双轨已落地**：`generate_release_notes: true`（轨一）+ release.yml 自动运行 `scripts/generate-changelog.mjs` 生成 `notes-draft.md` 草稿资产（轨二输入物），核对项见 [CONTRIBUTING.md](../../CONTRIBUTING.md) 发布核对清单。

### 4.3 Changelog 自动生成机制

**方案**：轻量脚本（Node 零依赖，`node scripts/generate-changelog.mjs <prevTag> <newTag>`）从 git log 抓取两 tag 间的 Squash 提交主题，按 Conventional Commits type 自动分节（feat→Added、fix→Fixed、`!`/BREAKING CHANGE→Breaking 尾注、docs/ci→并入 Changed 或忽略），**产出草稿供人工编辑**——不直接写入 CHANGELOG.md。

**理由**：全自动写入会打破「CHANGELOG 是用户可读文档而非日志转储」的定位；脚本只做机械归纳，措辞与影响范围仍由人定稿。脚本落点 `scripts/`（根级新目录，需 `ProjectStructureTest` 白名单 + ARCHITECTURE 2.2 登记一次，与 `githooks/` 同先例）。CI 侧可选：release.yml 在打 tag 时自动跑脚本并把草稿贴进 Release 页。

### 4.4 发布后动作（顺序固定）

1. RELEASE-HISTORY.md 增行（tag → commit → Release → 制品字节/校验和 → workflow run 链接）；
2. CHANGELOG.md 增节（人工定稿后的分节条目）；
3. 版本号四处一致性复核（3.1-4 的第三条）；
4. `main` 上以 `chore(release): back-merge vX.Y.Z` 收尾（如发布分支含 main 之外的收尾提交，经 PR 回灌）；
5. 事故路径：线上问题 → `hotfix/<id>-<slug>`（从上一个 tag 切出）→ 修复 PR → 新 PATCH tag → RELEASE-HISTORY 记录事故与回滚点。

## 5. 实施路线图

| 阶段 | 时间 | 动作 | 状态 |
| --- | --- | --- | --- |
| P0 已完成 | 2026-10-11 | 存量清理（43 分支）、保护规则（strict + 3 检查）、主检出/提交钩子、历史审计与豁免线、PR 治理（6 过时 PR 关闭、6 新 PR 合并） | ✅ |
| P1 本周 | v3.4.1 发布前 | ① 按 4.1 门禁走一次**新流程演练**：v3.4.1 从 `release/v3.4.1`（已就绪，versionCode 15）经 bump PR → tag → 签名发布；② 启用 GitHub Release 自动说明 + 人工定稿双轨；③ ~~`scripts/generate-changelog.mjs` 落地并登记白名单~~ **✅ 已落地（实测 v3.2.0→v3.4.0 两区间通过）** | ① ✅ 已演练（v3.4.1/v3.4.2 已按新流程发布）；② ✅ 已落地（release.yml 自动生成 `notes-draft.md` 草稿资产 + CONTRIBUTING 双轨核对清单）；③ ✅；P1 全部完成 |
| P2 下一 minor（v3.5.0 冻结前） | 随 L2 | ① 开放 PR 逐个裁决完毕；② signed tags（SLSA 演进第一步）；③ 季度分支清理首次例行执行 | 待执行 |
| P3 随 R3（架构演进） | M4 前 | SBOM（CycloneDX）+ provenance attestation；`/metrics` 与监控告警接入发布门禁 | 待执行 |
| 例行 | 每季度 | 流程审计复跑（同 FOLLOW-UP-PROCESS-AUDIT 口径）+ 分支/worktree 清理 + 本设计符合性抽查 | 周期任务 |

**平滑迁移保证**：P1 的 v3.4.1 是新体系的首次实战；若门禁发现流程缺陷，修复以 patch 节奏滚动，不阻塞既定 L2/L3 能力里程碑。本设计与 ROADMAP-ADS 的 L1–L5 无版本竞争——版本管理是工程承载侧，见 DESIGN-ARCH-EVOLUTION 第 7 节。
