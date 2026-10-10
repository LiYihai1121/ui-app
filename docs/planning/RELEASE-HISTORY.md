# 发布历史与提交链路（RELEASE-HISTORY）

本文记录版本阶段、Git tag、合并提交和 GitHub Release 的对应关系，避免仅凭短哈希或提交标题判断历史是否断链。每次项目更迭完成后，必须同步更新本页的 Release list，并核对对应的 annotated tag。

标签发布由 [.github/workflows/release.yml](../../.github/workflows/release.yml) 自动执行：先校验 annotated tag、提交对象和 Android/服务端版本一致性，再构建 R8 Release 变体、运行服务端检查、生成 APK SHA-256 校验和并创建 GitHub Release。打包步骤强制 `apksigner verify`：配置 `ADSKIP_KEYSTORE_BASE64` 等仓库 Secrets 时用正式密钥签名，未配置时构建脚本回退 debug 签名（保证制品可安装），未签名产物一律拒绝上传（见「制品勘误」）。目标分支保护和发布前合并要求由仓库规则及 GitHub 分支保护执行。

## 目录

- [Release list](#release-list)
- [链路结论](#链路结论)
- [发布基线说明](#发布基线说明)
- [制品勘误](#制品勘误)
- [关键提交](#关键提交)
- [验证命令](#验证命令)
- [后续规则](#后续规则)

## Release list

| 版本 | Git tag | 对应提交 | GitHub Release | 状态 |
| --- | --- | --- | --- | --- |
| `3.4.0` | [`v3.4.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.4.0) | （tag 创建后补填） | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.4.0) | 待创建（发布流程执行中；`versionCode=14`、`versionName=3.4`、server `3.4.0`；作用域见「v3.4.0」条） |
| `3.3.0` | [`v3.3.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.3.0) | `1c296c0` | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.3.0) | 已发布（`AdSkip-v3.3.0.apk` 2,047,331 bytes，`SHA256=e33bc75b81ae6470aa75ed6bbc51dc125756178f8d6363a7df38006bcff3bf09`，`CN=AdSkip Release`，证书 SHA-256 `040d7afd0e288b7fe8df2aab43fb3f2b9b8eb458e88ab4d104d89921c9993edc`，与 v3.2.0 相同；applicationId 均为 `com.ldp.adskip`；含 `SHA256SUMS`；release workflow #38044504424 成功） |
| `3.2.0` | [`v3.2.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.2.0) | `f5127dd` | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.2.0) | 已发布（`AdSkip-v3.2.0.apk` 1,923,024 bytes，`SHA256=6f958ed5…fcb4`，Release 资产 `SHA256SUMS`；由 release.yml 构建并创建，经 PR #46 Squash 合入 `main`） |
| `3.1.0` | [`v3.1.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.1.0) | `ad188b0` | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.1.0) | 已发布（`AdSkip-v3.1.0.apk` 1,895,460 bytes，`SHA256=30657f9c…fb6`，`CN=AdSkip Release` 正式签名，`apksigner verify` 通过；厂商保活与磁贴未真机验收，见下「发布基线说明」） |
| `3.1.0-rc.1` | [`v3.1.0-rc.1`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.1.0-rc.1) | `696ef1e` | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.1.0-rc.1) | **预发布**（已签名 APK + `SHA256SUMS`；厂商跳转与磁贴未真机验收，**不可作为正式版分发**） |
| `3.0.4` | [`v3.0.4`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.4) | `27c3f5c` | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.4) | 已发布（已签名 Release APK，`CN=AdSkip Release`，可直接安装） |
| `3.0.3` | [`v3.0.3`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.3) | `958ce23` | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.3) | 已发布；APK 为未签名包，无法安装（见「制品勘误」） |
| `3.0.2` | [`v3.0.2`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.2) | `5b85e96` | [GitHub Release](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.2) | 已创建，制品待补传 |
| `3.0.1` | [`v3.0.1`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.1) | `b7ebabb` | 未创建 | 历史误指标签，不得复用 |
| `3.0.0` | [`v3.0.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.0.0) | `b7ebabb` | 未创建 | 历史标签 |
| `2.2.0` | `v2.2.0` | `0a40728` | 历史版本 | 已发布 |
| `2.1.0` | `v2.1.0` | `91140b5` | 历史版本 | 已发布 |
| `2.0.0` | `v2.0.0` | `d395fd0` | 历史版本 | 已发布 |

Release list 的维护要求：版本变更、tag、合并提交和 GitHub Release 必须一一对应；tag 创建后补填实际提交，GitHub Release 创建后补填链接、制品和校验和。未完成任一项时，状态必须明确标记为“待发布”“未创建”或“制品待补传”，不得写成“已发布”。

## 链路结论

提交 `bdf31d5d328c9a146607bf4e3bdaa5e0dd84dcca` 是工作流能力提交，提交 `c5cdac70bfb0394013dea6baee1470860eaac820` 是后续的合并提交。

```text
0fd6fa2 feat(v3.0): 新架构重构
   |
   +-- bdf31d5 feat(workflow): 添加 branch-guard 技能
   |      |
   |      +-- c5cdac7 merge: release/v2（第二父提交为 bdf31d5）
   |
   +-- 6706c28 chore(history): 归档旧远程交付线
          |
          +-- c5cdac7 merge: release/v2（第一父提交为 6706c28）
```

`c5cdac7` 是 `c5cdac70bfb0394013dea6baee1470860eaac820` 的短哈希。它同时包含两个父提交，属于正常的历史合并，不应通过重写历史来“修复”。

## 发布基线说明

- `v3.3.0`（tag 目标 `1c296c0`，发布分支 `release/v3.3.0`）：2026-10-10 已发布，版本包含安全审计修复、OkHttp 迁移/证书锁定及统计备份文件名修复。首次发布运行 [#38043906806](https://github.com/LiYihai1121/ui-app/actions/runs/38043906806) 因备份轮转测试失败；后续修复提交 `1c296c0`，运行 [#38044504424](https://github.com/LiYihai1121/ui-app/actions/runs/38044504424) 成功生成 APK 和 `SHA256SUMS`。下载后复核 APK SHA-256 与资产校验文件相符；CI `apksigner verify` 通过，签名证书为 `CN=AdSkip Release`，证书 SHA-256 `040d7afd0e288b7fe8df2aab43fb3f2b9b8eb458e88ab4d104d89921c9993edc`。APK 为 2,047,331 bytes，SHA-256 `e33bc75b81ae6470aa75ed6bbc51dc125756178f8d6363a7df38006bcff3bf09`。
  **发布治理偏差（保留作审计记录，不得再次移动/删除 tag）：**最初 `v3.3.0` tag 指向版本提交 `65442cf`；首次构建失败后，tag 目标被更新为 `1c296c0` 并再次触发发布。当前目标 `1c296c0` 不在 `origin/main` 历史中，因此不满足“先合入 main 再打 tag”的发布要求。发布时安全/协议 PR #70 也尚未完成独立领域审查。不得将此流程作为后续发布范例。
  发布验收覆盖声明：本次发布未完成厂商 ROM 真机验收，厂商保活与磁贴路径未实测；本地复核 v3.2.0 与 v3.3.0 APK 的签名证书 SHA-256 相同，且 `applicationId` 相同，可按同一签名身份升级。
- `v3.1.0`（提交 `ad188b0`，PR #42 经 Squash 合入）：tag、提交与 GitHub Release 均在 `main` 线，制品由正式密钥签名并经 `apksigner verify` 复核（`CN=AdSkip Release`，v2 方案），可直接安装。
  **本次发布覆盖了 `CONTRIBUTING.md`「发布验收」的强制项**：触及无障碍服务、快捷磁贴、厂商跳转、后台调度、系统权限的变更要求真机验收通过后方可发正式版，而截至发布真机矩阵（Android 8/13/14/15 × MIUI/HarmonyOS/ColorOS/OriginOS）**尚未完成**，已知验证仅覆盖模拟器。覆盖由维护者显式决定，并已同步记入 `CHANGELOG.md` 的发布验收覆盖声明，避免后人误读为「已真机验收」。已知风险：磁贴与厂商保活路径在真实 ROM 上的行为可能与模拟器不同；如出现问题走 `hotfix/*`。
- `v3.0.4`（提交 `27c3f5c`）是上一**发布基线**：tag、提交与 GitHub Release 均在 `main` 线上，制品由正式密钥签名、可直接安装。`v3.0.2`（提交 `5b85e96`，制品待补传）与 `v3.0.3`（提交 `958ce23`，制品不可安装，见「制品勘误」）同样位于 `main` 线，可追溯。
- `v3.0.0` 与 `v3.0.1` 是 annotated tag，两者都指向提交 `b7ebabb`；该提交**不是 `origin/main` 的祖先**（`git merge-base --is-ancestor b7ebabb origin/main` 返回非 0），只存在于远程分支 `docs/enterprise-version-governance`、`docs/repository-development-rules` 与本地 `main`。因此：
  - 这两个 tag 不得作为发布基线，其 GitHub Release 不再补建；
  - tag 保留不动（不删除、不移动），其指向的提交必须保持可达——承载该提交的两个远程分支**不得删除**；
  - 版本号 `3.0.0` / `3.0.1` 不再复用。
- 后续发布硬要求：tag 必须指向合并后的 `main` 提交，并由 [release.yml](../../.github/workflows/release.yml) 校验 Android 与服务端版本一致性。

## 制品勘误

- `v3.0.3`（tag 指向 `958ce23`）的 Release 制品 `AdSkip-v3.0.3.apk` 是**未签名包**：`apksigner verify` 返回 `DOES NOT VERIFY`，手机安装报「解析软件包时出现问题」，属于不可安装制品。成因是该版本发布的 CI 无签名密钥，构建脚本静默产出未签名 APK，工作流又没有签名校验环节。
- `3.0.2` 的状态为「制品待补传」；补传前必须先通过签名校验，不得直接把本地未签名产物传上去。
- 处置原则：tag、历史 Release 与已发布制品一律不删除、不移动；不可安装的历史制品保留原样作为记录，改由新链路保证后续版本可安装——构建侧在缺少正式签名时回退 debug 签名，发布侧打包后强制 `apksigner verify`，签名证书与 `SHA256SUMS` 一并写入工作流 Summary。
- 升级一致性：同一台设备要长期升级，请固定签名来源（本地正式签名构建，或已配置 Secrets 的 Release 制品）；混用 debug 签名与正式签名版本时需先卸载重装。
- 修复验证（`v3.0.4`）：Release 制品 `AdSkip-v3.0.4.apk` 由正式密钥签名（`CN=AdSkip Release`，证书 SHA-256 `040d7afd0e288b7fe8df2aab43fb3f2b9b8eb458e88ab4d104d89921c9993edc`），`apksigner verify` 通过，`versionCode = 9`；SHA-256 `8c8b50148c91b69c25de6be36fad9821fb1c6452ffd931209a336dd64f88ec11` 与 Release 内 `SHA256SUMS` 一致。
- 关联 Issue：[#23](https://github.com/LiYihai1121/ui-app/issues/23)。

## 关键提交

| 阶段 | 提交 | 内容 | 版本标签 |
| --- | --- | --- | --- |
| v2.0 基线 | `d395fd0251280c504f48b085bf8e80ad9a292a43` | Android 客户端和 Node.js 服务端初始版本 | `v2.0.0` |
| v2.1 | `91140b5` | 定时同步、免打扰和日志导出 | `v2.1.0` |
| v2.2 | `0a40728` | 安全、可测试性和协议增强 | `v2.2.0` |
| 工作流 | `bdf31d5d328c9a146607bf4e3bdaa5e0dd84dcca` | branch-guard、权限和 CI 工作流 | 由后续合并提交收录 |
| 历史整理 | `c5cdac70bfb0394013dea6baee1470860eaac820` | release/v2 历史合并 | `v3.0.0` 的祖先 |
| v3.0 | `b7ebabb` | Bun/Compose 架构与文档收尾（**不在 `main` 线上**，见「发布基线说明」） | `v3.0.0`、`v3.0.1`（历史标签） |
| v3.0.2 基线 | `5b85e96` | 规范化发布流程与版本对齐 | `v3.0.2` |
| v3.0.3 | `958ce23` | L1 选择器第三通道内核（纯 JVM 增量） | `v3.0.3` |
| v3.0.4 | `27c3f5c` | 安装可用性修复：release 签名回退 + 发布强制签名校验 + Secrets 正式签名 | `v3.0.4` |
| v3.2.0 | `f5127dd` | 节点快照工具 + UI/权限重设计落版（`ServiceIntentContractTest` 守护广播字面量） | [`v3.2.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.2.0) |
| v3.3.0 | `1c296c0` | OkHttp 网络层迁移 + 服务端请求体限制/限流/结构化日志；安全能力如实声明（无规则 HMAC、未启用证书 pin 与强制 HTTPS） | [`v3.3.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.3.0) |
| v3.4.0 | （tag 创建后补填） | Monorepo 解耦收官：applicationId/namespace 迁移 `com.qingqi.adskip`（BREAKING）+ 服务端解耦 client 包名 + M1 非导出/包级黑名单 + M2 签名禁回退 | [`v3.4.0`](https://github.com/LiYihai1121/ui-app/releases/tag/v3.4.0) |

## 验证命令

在仓库根目录执行：

```bash
git rev-list --parents -n 1 c5cdac70bfb0394013dea6baee1470860eaac820
git merge-base --is-ancestor bdf31d5d328c9a146607bf4e3bdaa5e0dd84dcca c5cdac70bfb0394013dea6baee1470860eaac820
git tag --contains bdf31d5d328c9a146607bf4e3bdaa5e0dd84dcca
git tag --contains c5cdac70bfb0394013dea6baee1470860eaac820
```

预期结果：祖先检查退出码为 `0`，两个提交都被当前发布线和 `v3.0.0` 标签包含。

特定的 `bdf31d5 → c5cdac70` 校验只适用于 `v3.0.0`，不会阻断更早的 v2.x 标签发布。

## 后续规则

- 不删除或移动已推送的标签和共享分支历史。
- 新的历史整理使用合并提交或 `git revert`，不使用 `reset --hard`、强制推送或替换已有提交。
- 发布版本以 annotated tag 为准，提交、标签和构建制品必须可以相互追溯。
- 每次项目更迭完成后更新 `CHANGELOG.md` 和本页 `Release list`；正式版本必须创建新的 annotated tag，不得复用旧版本号。
- 发布核对至少包括：版本号一致、Android `versionCode` 递增、tag 指向合并后的 `main`、GitHub Release 状态和 `SHA256SUMS` 已记录。
- 发布 tag 必须指向合并后的 `main` 提交；指向非 `main` 提交的 tag 视为历史标签——不得补建 Release、不得复用版本号，其承载分支不得删除。
