# 广告能力边界「不做限制」验证记录（ROADMAP-ADS-VERIFY）

> 本页登记 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节能力边界由「明确不做」改为「不做限制」
> （提交 `02e4c8b`）后的**外部依据、风险评估、仓库能力面排查与门禁验证结果**；
> 不复述版本号与里程碑（以 [ROADMAP.md](ROADMAP.md) / [ROADMAP-ADS.md](ROADMAP-ADS.md) 为准）。
> 文档入口见 [../README.md](../README.md)（文档地图）。
> 状态：已完成本轮核查；Phase 0 合规评审仍是 L2/L3 的前置门（未启动）。
> 最后更新：2026-10-08。

## 1. 变更确认

- 提交 `02e4c8b`（`docs: ROADMAP-ADS 能力边界改为不做限制`）把 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节
  四条「❌ 明确不做 / ⚠️ 部分覆盖」改为「**不做限制**」的保留表述；**未改任何代码**。
- 「不做限制」指的是**能力清单不再预先排除**（激励视频跳过、信息流关闭控件点击、
  短信/商店/搜索广告按层处理、贴片 L2 拦素材），**不等于**放开既有约束：
  - L2 DNS / L3 Hook 仍需 Phase 0 合规评审通过才进发布分支（[ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节）；
  - 仍只点击界面已展示的关闭/跳过控件，SafetyGuard 护栏、去抖与节流全部保留；
  - 分发仍为 APK 自建渠道，不上架应用商店（[../../README.md](../../README.md) 合规提示）。

## 2. 外部依据（Web 证据）

### 2.1 意外/自动点击风险（对应 ad_ignore_click 语义）

- **Google AdMob 官方博客《Preventing accidental clicks for a better mobile ads experience》**
  （2016-05-06，https://blog.google/products-and-platforms/products/ads/preventing-accidental-clicks-for-better-mobile-ads/）
  - 平台会**忽略**被判为意外的点击：超快点击（人类对 90mph 棒球仅有约 680ms 反应时间，
    点击快于此阈值「几乎不可能是有效意图」）与广告**边缘点击**（指腹约 50px，误触边缘转化率远低于中心）。
  - 这类保护每日拦截数千万次意外点击；启用后广告转化率平均提升 10%+。
  - **对本项目的含义**：本工具代用户执行的自动点击若被广告平台判为 fast/edge click，会被**直接忽略**
    ——即「点了也无效」，同时可能被计入无效流量统计。
- **Google AdMob 官方博客《Tips on how you can prevent invalid activity on your apps》**
  （2016-02-04，https://blog.google/products/admob/tips-prevent-invalid-activity-on-apps/）
  - 无效流量（invalid activity）**同时涵盖蓄意欺诈与意外点击**；发布商不得以「自动或手动方式」
    人为抬高点击/展示；点击必须源于真实用户兴趣。
  - 若检测到无效流量：**账号可能被暂停/停用，已产生收入可能被退还广告主**。
  - 应用内广告实现不得诱导意外点击（插屏突然出现、banner 贴邻可点元素是两大主因）。
  - **对本项目的含义**：本工具的自动点击只作用于**用户自己设备上第三方 App 的 UI 关闭控件**，
    不触碰本应用广告计费；但「通过无障碍批量代点」这一行为模式与上述「自动化手段」红线相邻，
    属于必须在免责声明中向用户讲清的边界。
- 仓库内**不存在** `ad_ignore_click` 标识符（全仓检索 488 文件无命中）；
  该语义按平台「忽略意外点击（ignore click）」政策理解，如后续要落地为配置键，需单独设计。

### 2.2 无障碍 API 的合规边界

- **Android 官方文档《Create an accessibility service》**
  （https://developer.android.google.cn/guide/topics/ui/accessibility/service）
  - 无障碍服务的设计意图是**辅助用户操作**（读屏、替代手势、焦点引导）；
    以无障碍服务做「对第三方 App 的通用自动化」属于平台审视的用法。
  - [../../README.md](../../README.md) 合规提示已如实声明：本工具**不符合 Google Play 对
    AccessibilityService 的审核口径**，故仅 APK 自建渠道分发、不上架应用商店。
- 局限说明：`support.google.com`（Play 政策页 / AdMob 帮助页）本轮多次抓取超时，
  未能取得一手政策原文；上述两条为可复现抓取的一手来源，Play 政策原文留待 Phase 0 评审补证。

### 2.3 国内判例

- [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节已列「李跳跳」不正当竞争诉讼先例作为合规前提：
  仅点击 UI 已展示按钮、不上架商店、免责声明醒目。本轮未取得新判例原文，维持既有口径。

## 3. 边界与合规/体验风险评估（对应「不做限制」）

| 维度 | 评估 | 既有控制 |
| --- | --- | --- |
| 能力范围 | 四类广告从「排除」转为「按层保留」；L1 仅点击已展示控件，L2 拦已知广告域名，L3 需 Root | 分层矩阵见 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 4 节 |
| 合规/法律 | 激励视频「帮你跳过」若越过 UI 自动点击将直接损害用户奖励与广告主利益，是判例高危点 | 条款限定「仅在 UI 提供跳过按钮时生效」；Phase 0 评审未过 L2/L3 不发版 |
| 误触/触控风险 | 信息流「删除」实为点关闭控件，坐标手势一旦偏移即变成**点进广告**（与 2.1 意外点击同构） | SafetyGuard 黑名单、1.2s 去抖、150ms 全局节流、≤500 节点遍历、按应用禁用 |
| 无障碍权限 | 权限面大（可读可点全屏节点），属用户高感知风险 | 首次引导显式授权、磁贴一键启停、README 隐私声明（数据默认不出本机） |
| 平台分发 | 不符合 Play 审核口径，无法上架 | 已选 APK 自建渠道 + GitHub Releases 校验和 |
| 回滚 | 边界声明是纯文档改动，可独立回滚 | `git revert 02e4c8b`（或反向提交），无数据/协议影响 |

**结论**：「不做限制」在**声明层**成立且风险可控；**能力层**仍受 Phase 0 与既有护栏约束，
两者不矛盾。后续若把任一「保留」项真正落地（尤其激励视频与信息流），必须先过第 3 节合规前提。

## 4. 仓库广告能力面排查

- 产品本体是**广告跳过器**（`com.ldp.adskip`），非广告投放/计费方：
  `service/SkipAdService`（AccessibilityService + GestureDescription 手势）、
  `engine/SkipRuleEngine`（选择器/关键词/ViewID 匹配）、`data/RulesRepository`（规则下发）、
  `data/Prefs` 与 `core/SecureStore`（加密偏好）。
- 无 `ad_ignore_click`、无 `advert/advertis` 命名的代码面；广告相关仅出现在包名、规则与文档语境。
- 结论：**不存在需要同步修改的「广告屏蔽模块」实现**——边界变更只涉及规划文档。

## 5. 构建验证（门禁数字）

环境：Windows + PowerShell，JDK 21（`D:\Java\jdk-21.0.12.1+1`），Gradle 9.7.0 wrapper。
PowerShell 不支持 `&&`，实际命令形态为
`cmd /c 'cd /d F:\LocaRepository\ui-app\client && F:\LocaRepository\ui-app\client\gradlew.bat <task>'`。

| 门禁 | 结果 | 数字 |
| --- | --- | --- |
| `assembleDebug` | BUILD SUCCESSFUL（6s） | 40 actionable tasks：13 executed / 5 from cache / 22 up-to-date |
| `ktlintCheck` | BUILD SUCCESSFUL（1s） | 11 actionable tasks：7 from cache / 4 up-to-date |
| `testDebugUnitTest`（`--rerun` 强制重跑） | BUILD SUCCESSFUL（7s） | 28 actionable tasks；**234 tests，0 failures，0 errors，0 skipped** |

> 234 项含 `ProjectStructureTest` / `RepoHygieneTest` / `ArchitectureBoundaryTest` /
> `ManifestContractTest` 等契约测试——即文档地图登记规则本身已随本次验证一并复验。

## 6. 正式文档补齐状态

| 文档 | 状态 |
| --- | --- |
| [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节 | ✅ 已改（`02e4c8b`），本页第 1 节确认其语义边界 |
| [../../README.md](../../README.md) 合规提示 | ✅ 现状即准确（仅点已展示按钮 / 不上架商店 / 隐私声明），代码未变故不需改 |
| 本页（证据 + 评估 + 验证记录） | ✅ 已登记文档地图 |
| Play 政策一手原文 | ⏳ Phase 0 评审时补证（本轮 `support.google.com` 抓取超时） |

## 7. 总结与待办

**已完成**：边界变更确认（`02e4c8b`）、外部证据固化（§2）、风险评估（§3）、
能力面排查（§4，确认无代码面）、三件套门禁验证（§5，234/234 绿）。

**待办（按优先级）**：

1. Phase 0 合规评审启动前，补抓 Play AccessibilityService 政策与 AdMob 无效流量帮助页原文（§2.2 局限）；
2. 若要落地任一「保留」能力（激励视频 / 信息流关闭 / L2 DNS），先修订 [ROADMAP.md](ROADMAP.md)
   「明确不做的」声明并走 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节合规前提；
3. 真机回归（步骤 F，归属 `3.3.0`）继续按 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 6 节出口条件执行。
