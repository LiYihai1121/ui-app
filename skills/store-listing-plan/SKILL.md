---
name: store-listing-plan
description: Plan app store listing (软件上架) for this project - compliance gate first, target store selection, materials checklist, signing and release chain, review rejection risks, staged rollout and rollback. Trigger when asked to plan 上架 / 应用商店发布 / Google Play / 华为小米 OPPO vivo 应用市场 / 应用宝 submission or listing.
metadata:
  audience: release-owner
  workflow: release-plan
---

## 规则唯一事实源（此处不复述规则）

| 关注点 | 规范文件（唯一事实源） |
| --- | --- |
| 合规边界与「明确不做」（不上架的现状声明） | [README.md](../../README.md)「合规提示」+ [docs/planning/ROADMAP.md](../../docs/planning/ROADMAP.md)「明确不做的」 |
| 合规评审流程（L2/L3 等待评审的同款门槛） | [docs/planning/ROADMAP-ADS.md](../../docs/planning/ROADMAP-ADS.md) 第 3 节 |
| 版本号、签名、发布链路、制品校验 | [CONTRIBUTING.md](../../CONTRIBUTING.md) + [docs/planning/RELEASE-HISTORY.md](../../docs/planning/RELEASE-HISTORY.md) |
| 发版节奏与版本序列 | [docs/planning/ROADMAP.md](../../docs/planning/ROADMAP.md) |
| 厂商 ROM 适配与真机验收（上架后仍必需的保活引导） | [docs/planning/VENDOR-SUPPORT.md](../../docs/planning/VENDOR-SUPPORT.md) |
| 文档登记（计划文档落盘时必须进文档地图） | [docs/README.md](../../docs/README.md) |
| 分支 / 提交 / 门禁的通用自检 | [skills/branch-guard/SKILL.md](../branch-guard/SKILL.md) |

> 本文件**刻意不复制**签名参数、命令清单与拒审条目明细：复制出去的规则会静默漂移，并成为第二份（往往是错的）真相。
> 上架是一个**决策密集型**任务，本 skill 只提供决策顺序、清单骨架与硬门禁指针。

## Gate 0：合规边界先于一切（不通过则计划中止）

本项目当前**声明不上架应用商店**（README「合规提示」：AccessibilityService 不符合 Google Play 审核口径，分发以 APK 自建渠道为准；ROADMAP「明确不做的」含商业化上架）。因此：

1. 任何上架计划的第一步是**边界修订决策**：由流程/领域负责人修订 README 合规提示与 ROADMAP 不做清单（与计划同一 PR，否则视为未通过本 Gate）；
2. 若目标是国内厂商商店（华为/小米/OPPO/vivo/应用宝），先逐条对照其**无障碍权限与后台策略审核口径**，把结论写进计划的「审核风险对照」；
3. 未过 Gate 0 时，唯一合法分发路径是**自建渠道**：GitHub Releases（`SHA256SUMS` 校验）与服务端 `/download`（见 README「分发与下载」），本 skill 仍可用于规划自建渠道的发布物料。

## 上架规划清单（输出 = 一份计划文档，落盘须按 docs/README.md 登记）

按顺序执行，每节产出表格或结论，不跳步：

1. **目标商店与制品形态**
   - 商店清单 × 是否上架 × 形态（APK / AAB）× 包名（`com.ldp.adskip` 不可变，换包名即换应用）；
   - 同一签名是硬前提：商店包与 Releases 包必须同源同签名，否则用户装机会遇到签名冲突（README「手机安装报错排查」已有该场景）。
2. **制品与版本链**（指针 CONTRIBUTING / RELEASE-HISTORY，不在此复述命令）
   - `versionCode` 全局单调递增、`versionName` 与 `server/package.json` 同步；
   - 正式签名来源（仓库 Secrets 或本机 `local.properties`）→ 发布流水线已强制 `apksigner verify`，未签名制品拒绝上传；
   - tag `vX.Y.Z` → CI 制品 → `SHA256SUMS` 可追溯。
3. **上架材料清单**
   - 隐私政策 URL（内容必须与 README 隐私声明一致：数据默认不出本机、上报仅含包名/时间戳/匹配通道、无账号体系）；
   - 权限用途说明：`INTERNET`（可选同步与上报）、无障碍服务（仅点击界面已有「跳过」按钮）、电池白名单（防后台被清）；
   - 图标与截图（中英）、应用描述、关键词、年龄分级；国内商店额外要求 ICP 备案信息与可访问的隐私政策链接。
4. **审核风险对照表**
   - 逐条列出高危审核点（无障碍服务用途、后台启动、自启动引导、更新机制、广告相关表述）与**证据链接**（README 合规提示、技术原理章节可直接引用）；
   - 明确「不拦截/不破解广告内容」的产品边界表述，避免审核时被归类为广告拦截工具。
5. **发布与灰度节奏**
   - 挂到 ROADMAP 的版本序列：先 `release/vX.Y.Z` 冻结 → 商店提审与 GitHub Releases 同版本同制品；
   - 灰度顺序建议：内部验收 → 小范围 RC（沿用 `rc.N` 预发布纪律）→ 全量。
6. **回滚与下架**
   - 优先回滚已验证制品或 `git revert`（禁止对受保护分支 reset / force-push，见 CONTRIBUTING）；
   - 商店侧「撤回审核 / 下架 / 回退版本」的操作与责任人写入计划。
7. **上架后运营边界**
   - 规则更新走**云端下发**（服务端规则包），不靠商店发版；
   - 统计只回自建服务端；差评中「跳过失效」类问题回链 [VENDOR-SUPPORT.md](../../docs/planning/VENDOR-SUPPORT.md) 的保活引导与验收矩阵。

## 开工前自检

1. 读本 skill 的 Gate 0——默认结论是「不上架」，先拿到边界修订决策再继续
2. 走 [branch-guard](../branch-guard/SKILL.md) 的开工自检（干净分支、worktree、从最新 `main` 拉 `docs/<slug>` 或 `release/vX.Y.Z`）
3. 计划文档若落盘 `docs/planning/`，同 PR 登记 [docs/README.md](../../docs/README.md) 文档地图（`ProjectStructureTest` 会拦未登记文档）

## 提交前自检

- 计划中每个引用的规则（签名、版本、回滚）都给出**指针**而非复制条款
- 涉及合规边界（README 合规提示 / ROADMAP 不做清单）的改动与计划**同一 PR**
- 门禁跑通：`cd client && ./gradlew testDebugUnitTest`（文档登记、仓库卫生、分支命名均由其覆盖）