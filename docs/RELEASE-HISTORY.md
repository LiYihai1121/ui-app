# 发布历史与提交链路

本文记录版本阶段、Git tag、合并提交和 GitHub Release 的对应关系，避免仅凭短哈希或提交标题判断历史是否断链。每次项目更迭完成后，必须同步更新本页的 Release list，并核对对应的 annotated tag。

标签发布由 [.github/workflows/release.yml](../.github/workflows/release.yml) 自动执行：先校验 annotated tag、提交对象和 Android/服务端版本一致性，再构建 R8 Release 变体、运行服务端检查、生成 APK SHA-256 校验和并创建 GitHub Release。CI 不持有签名密钥，因此上传的是未签名 APK；正式分发前必须在受信任环境签名。目标分支保护和发布前合并要求由仓库规则及 GitHub 分支保护执行。

## Release list

| 版本 | Git tag | 对应提交 | GitHub Release | 状态 |
| --- | --- | --- | --- | --- |
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

## 关键提交

| 阶段 | 提交 | 内容 | 版本标签 |
| --- | --- | --- | --- |
| v2.0 基线 | `d395fd0251280c504f48b085bf8e80ad9a292a43` | Android 客户端和 Node.js 服务端初始版本 | `v2.0.0` |
| v2.1 | `91140b5` | 定时同步、免打扰和日志导出 | `v2.1.0` |
| v2.2 | `0a40728` | 安全、可测试性和协议增强 | `v2.2.0` |
| 工作流 | `bdf31d5d328c9a146607bf4e3bdaa5e0dd84dcca` | branch-guard、权限和 CI 工作流 | 由后续合并提交收录 |
| 历史整理 | `c5cdac70bfb0394013dea6baee1470860eaac820` | release/v2 历史合并 | `v3.0.0` 的祖先 |
| v3.0 | `b7ebabb` | 当前 Bun/Compose 架构和文档收尾 | `v3.0.0` |

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
