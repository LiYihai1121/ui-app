# UI 设计评审与重构方案（DESIGN-UI-REVIEW）

> ⚠️ **本文件描述的是 v3（薄荷绿）方案，已被 v4（蓝色）取代。**
> v4 的落地规格见 [DESIGN-UI-V4.md](DESIGN-UI-V4.md)；两者冲突时**以 v4 为准**。
> 本文件保留作为历史依据：其中的**评审方法论与问题清单（三态状态语义、间距是否承载语义、
> 可点性是否由组件类型声明）在 v4 中依然成立并已延续**，但品牌色取值、
> 导航结构（v3 的「应用」一级页在 v4 降为二级）、区块标签体系均已变更。
> 最后更新：2026-10-16（v3）。

> 状态：**已实施** — 评审结论与重构均已落地到 `client/app/src/main/java/com/qingqi/adskip/ui/`。
> 最后更新：2026-10-16。
> 性质：**设计评审记录（Review + Design）**，同时是本次 UI 重构的决策依据与验收判据。
> 范围：仅客户端表现层（`ui/`）。不含业务逻辑、服务端、数据层。
> 门禁状态：`ktlintCheck` / `assembleDebug` / `testDebugUnitTest`（286 项）**全部通过**。

---

## 1. 评审方法

评审按「大厂设计体系」的三条硬标准逐项对照，不使用主观措辞：

| 维度 | 判据来源 | 检查方式 |
| --- | --- | --- |
| **角色完备性** | Material 3 `ColorScheme` 全部 30 个角色 | `ColorSchemeContractTest`（机器强制） |
| **语义正确性** | 状态机是否穷尽、同一状态是否只有一种表达 | 逐屏枚举状态分支 |
| **度量一致性** | 间距/圆角/字阶是否收敛到有限令牌 | `UiContractTest`（机器强制：间距字面量必须来自 `Spacing`/`UiSizes`） |

评审的产出不是「建议清单」，而是**可执行的重构 + 守护测试**：每条结论都对应一处代码改动，且由既有测试契约保证不回归。

---

## 2. 评审结论：从高到低的问题清单

### P0 — 三态被压成两态（正确性缺陷）

**现象**：产品层原本已区分三种无障碍服务状态（`AccessibilityStatus.ON / OFF / UNKNOWN`），但 UI 层只渲染两态——`UNKNOWN` 被静默画成「关闭」。

**危害**：这是本次评审发现的**最高优先级缺陷**，因为它不是「不好看」，而是**误导用户去做错误的动作**。

- `OFF` 的正确用户动作是「打开开关」；
- `UNKNOWN` 的正确用户动作是「去系统设置核对权限」。

把 `UNKNOWN` 画成 `OFF`，等于让用户在权限状态未知时反复点一个不会生效的开关。用户会认为「这个 App 坏了」，而真实原因是权限读取本身失败了。

**修复**：

1. 色板新增第三态 `StatusUnknown = #B26A00` / `StatusUnknownContainer = #FFE7C2`（琥珀色，与 ON 的绿、OFF 的灰在色相上互不混淆）；
2. `StatusPalette` / `StatusColors` 扩展 `unknown` / `unknownContainer`；
3. `StatusOrb` 新增 `unknown: Boolean = false` 参数，未知态渲染为**虚线弧 + 中心短横**（几何上与 ON 的扫光弧、OFF 的实心环都不同——仅靠颜色区分对色觉障碍用户无效，必须同时改变形状）；
4. `HomeViewModel` 暴露 `accessibilityUnknown`；
5. 文案新增 `status_unknown` / `status_unknown_hint`（中英双语）。

### P1 — 状态主视觉用了渐变，读起来像渲染故障

**现象**：首页状态 Hero 区用三停靠点渐变作背景。

**问题**：渐变在 M3 语义里表达「氛围/装饰」，而这一块承载的是**状态结论**。用装饰性手法承载信息性内容，会让用户无法判断「这块颜色是设计意图还是渲染出错」。大厂做法是状态区用**实色容器**（Ant Design 的 `Alert`、M3 的 `*-container` 角色）。

**修复**：渐变改为实色 `StatusColors.*Container`；状态环（108dp）置于圆形浅色底之上，形成「大色块定调 → 图形给形状 → 文字给结论」的三层递进。

### P1 — 每屏强调点不止一个，用户不知道先看哪里

**现象**：三格数值瓦片全部同色，且与页面其他主色元素争抢注意力。

**修复**：引入 `StatTile(emphasis: Boolean = false)`，**每屏只允许一个 `true`**。首页给「累计跳过次数」，我的页给「今日拦截数」。这一约束写进了 KDoc，理由是：强调是一种稀缺资源，三处都强调等于零处强调。

### P2 — 间距没有承载语义

**现象**：全屏统一 32dp 卡片间距，导致「同一分组内」与「跨分组」在视觉上无差别。

**修复**：引入**分组列表卡片模式**（Ant Design / iOS Settings / 微信共用做法）：

- 新增 `GroupedListCard`（整组一张卡，行间用 `RowDivider(startIndent = 20dp)` 分隔，分隔线左侧内缩对齐文字而非图标）；
- 新增 `SectionLabel`（分组标题，字号 `titleSmall`、色 `primary`）；
- 结果：分组内 12dp、分组间 24dp——**间距差本身就是信息**。

### P2 — 不可点的图标长得像按钮

**现象**：关键词输入框尾部的「添加」图标是纯装饰 `Icon`，但长得完全像一个按钮。

**危害**：违反「凡看起来可点的都必须可点」。用户点击无反应会怀疑应用卡死。

**修复**：改为真实 `IconButton(onClick = ..., enabled = input.isNotBlank())`，禁用态由 M3 自动降透明度——**可点性由组件类型声明，而非由视觉猜测**。

### P2 — 破坏性操作比建设性操作更显眼

**现象**：日志页「清空」是 `Button`，「分享」是弱图标。

**修复**：权重对调——分享升为 `Button(weight(1f))` 全宽主操作，清空降为 `IconButton(tint = error)`。破坏性操作降权、且用错误色**仅作提示**而非主色块。

### P2 — `TopAppBar` 缺失被误认为疏漏

**结论：这是刻意决定，不是遗漏。** 四个一级页面均为**标签级目的地**（无返回、无页面级动作），M3 明确建议此类页面省略 `TopAppBar`，改用页面内 `PageHeader`。加了反而会多出一条只有标题的空条，压缩内容高度。评审结论为**维持不加**，并把理由写进 `MainActivity.kt` 注释，防止后来者"顺手补上"。

---

## 3. 设计令牌层重构

令牌层的原则：**所有视觉常量必须有名字，且名字表达语义而非数值**。

### 3.1 色板（`theme/Color.kt`）

- 新增 `Brand05..Brand90` 与 `Neutral00..Neutral95` 两条梯级；
- 新增第三态状态色（见 P0）；
- 新增 `inverseSurface` / `inverseOnSurface`（浅/深各一套）——原先该角色缺失，Snackbar 在深色模式下对比度不足。

**为什么用固定色阶而不是 `copy(alpha)`**：`color.copy(alpha=0.12f)` 叠在不同容器色上会得到**不同结果**——同一枚徽章在卡片上和浮层上是两个颜色。与给定背景做线性插值（`blendOnto`）才能得到确定值。

### 3.2 字阶（`theme/Type.kt`）

这是针对**中文界面**的重校，两处度量被旧版忽略：

| 项 | 旧值 | 新值 | 理由 |
| --- | --- | --- | --- |
| 标题字重 | SemiBold(600) | **Bold(700)** | Noto Sans CJK 在 600 与 700 间**字面宽度差极小、笔画却明显变粗**，导致「标题 SemiBold」在中文下既不够突出又糊。中文标题需要的是更大的字号与更大的字重跨度，而非在 600 档反复微调 |
| bodyLarge 行高 | 24sp | **26sp** | 中文字面是满框方块，行距不足时上下行笔画会「顶」在一起 |
| bodyMedium 行高 | 21sp | **23sp** | 同上 |
| bodySmall 行高 | 17sp | **20sp** | 同上 |
| 正文 `letterSpacing` | 0.15~0.2sp | **0** | 加字距是为拉丁文设计的做法，在中文下会让方块字之间出现可见缝隙，破坏密排节奏 |

全部 15 个 M3 排版角色仍**显式声明**（`ColorSchemeContractTest` 强制要求，不允许依赖默认值）。

### 3.3 间距与尺寸（`theme/Spacing.kt`）

- `Spacing` 新增 `lg2 = 20.dp`（分组标题上方留白）、`huge = 48.dp`；
- `UiSizes` 新增：`iconPlate = 36.dp`、`stateIllustration = 72.dp`、`stateIllustrationIcon = 32.dp`、`iconPlateGlyph = 20.dp`、`hairline = 1.dp`、`statusDot = 10.dp`、`statusDotRing = 1.5.dp`、`topBar = 64.dp`；
- 状态环尺寸：`statusOrbHero` 124→108dp、`orbHalo` 108→96dp（缩小后与文字块的比例更接近黄金比，且给下方操作留出呼吸）。

**关键约束**：`UiContractTest` 规定源码中**不得出现间距字面量**。本次重构产生的 6 处字面量（`1.dp`、`20.dp`、`72.dp`×2、`10.dp`、`1.5.dp`）全部被令牌替换，这是该测试第一次真正拦住新增代码——证明契约有效。

### 3.4 形状（`theme/Theme.kt`）

新增显式形状阶梯（此前用 M3 默认值，导致卡片圆角与按钮圆角不协调）：

```kotlin
private object Shapes { const val XS = 6; const val SM = 10; const val MD = 14
                        const val LG = 20; const val XL = 28 }
```

---

## 4. 组件层重构

| 组件 | 变更 | 解决的问题 |
| --- | --- | --- |
| `GroupedListCard` | **新增** | 分组语义可视化（P2） |
| `RowDivider(startIndent)` | **新增** | 分隔线左缩进对齐文字而非图标 |
| `SectionLabel` | **新增** | 分组标题（原用 `SectionTitle`，与卡片内标题混淆） |
| `IconPlate` | **新增** | 统一图标底衬（36dp），消除各处手写 size |
| `StatusBadge` | **新增** | 徽章成套（内边距/圆角/字号），原日志页与应用页各写一份导致同屏两种内边距 |
| `QuickActionTile` | **新增** | 图标+标签竖向瓦片；原三个高频入口是一排小图标+12sp 小字，密集屏上命中区不足 |
| `SectionCard` | 形状改 `shapes.medium`，内边距 `lg2` | 圆角协调 |
| `StatTile` | 加 `emphasis` | 单一强调点（P1） |
| `EmptyState` / `ErrorState` | 图标进入 72dp 圆形底 | 空态不再是「一行灰字」 |
| `SegmentedFilter<T>` | **新增（泛型）** | 替换单选 `FilterChip`；分段控件是筛选项的标准形态 |
| `StatusOrb` | 加 `unknown`；内置 `StatusDot` + `blendOnto` | P0 |
| `TwoLineRow` | 内边距统一 20/12dp | 度量一致 |

`SectionCard` 与 `GroupedListCard` 的分工写进了代码注释：前者装**一个主题的多项信息**，后者装**同类多行的列表**。两者混用会让分组语义失效。

---

## 5. 页面层重构

### 首页（`HomeScreen.kt`）

信息顺序重排为：`PageHeader → StatusHero → UsageTiles → SectionLabel("快捷操作") + QuickActionsRow → KeywordsCard → SectionHint`。

- 状态结论提到最顶，符合「先告诉用户现在怎么样，再告诉他能做什么」；
- 关键词卡片的 `scrollToKeywords` 索引 4→5（顺序变了，锚点必须跟着变——这是重排的实际成本，容易漏）。

### 应用页（`AppsScreen.kt`）

- 新增 `ToolBar`：搜索框 + `SegmentedFilter` + `apps_hint` 辅助文案三合一；
- 副标题从「操作说明」改为 `plurals.apps_subtitle`（**数量陈述**）。理由：用户在这一屏想知道的是「有几个应用」，不是「怎么用这个页面」；
- 结果列表整组包进一张 `SectionCard`，行间 `RowDivider(startIndent = Spacing.lg)`；
- 应用图标统一裁剪为 `shapes.small`。

### 日志页（`LogsScreen.kt`）

- 分享升为全宽主操作、清空降为 `IconButton` + 错误色（P2）；
- 列表包进单张卡片；
- 新增 `logs_retention_note`（保留条数说明）。原先把这条信息塞在副标题里，导致副标题过长、且与「日志是什么」这一信息混为一谈。

### 我的页（`ProfileScreen.kt`）

重组为四个 `SectionLabel` 区块：`profile_block_status` / `profile_block_usage` / `profile_block_settings` / `profile_block_about`。

`AboutCard` 去掉了卡片内的 `SectionTitle`（卡片标题与分组标题重复），改用 `contentPadding = PaddingValues(vertical = Spacing.xs)` + `RowDivider` / `InfoDivider`。

### 权限卡（`PermissionCard.kt`）

- 新增私有 `statusColor(status)`：`READY → StatusColors.on`、`NEEDS_ACTION → StatusColors.unknown`、`UNKNOWN → outline`；
- `READY` 行加尾部 `StatusBadge`；
- `StatusDot(ready = ...)` 改为 `StatusDot(color = ..., filled = ready)`——从「二值布尔」升级为「显式颜色」，这是 P0 三态修复在组件层的落点。

### 设置（`SettingsScreen.kt`）

- 日程卡片合并为单张 `SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs))`，内含内缩 `SectionTitle` + 两行 `LabeledSwitch` + `RowDivider`；
- 时间选择器包进 `AnimatedVisibility(visible = state.dndEnabled)`——**原先是常显但禁用**，禁用控件常显只会制造噪音，隐藏才是正确表达「与开关联动」；
- 保活按钮 `Button` → `FilledTonalButton`（次要动作降权）；
- 明文传输警告升级为 `errorContainer` `Surface` 横幅（原文是普通文字，安全警告必须视觉可辨）；
- 云同步卡片重排为「说明在字段之上」。

### 外壳（`MainActivity.kt`）

- 底部 `NavigationBar` 改为 `primaryContainer` 指示器 + `primary` 选中文字（原用默认值，与品牌色脱节）；
- 刻意不加 `TopAppBar`（见 P2 结论）。

---

## 6. 无障碍

| 项 | 处理 |
| --- | --- |
| 语义重复 | `StatusOrb` 用 `clearAndSetSemantics {}` 声明为**纯装饰**——它一度另挂 `contentDescription`，导致「服务运行中」被读屏念两遍。同一结论只由一方提供语义 |
| 数值合并 | `StatTile` 用 `semantics(mergeDescendants = true)`，否则读屏把「42」与「次」拆成两个孤立节点 |
| 触控目标 | 全部交互元素 ≥ 48dp（`QuickActionTile` 的引入正是为此） |
| 角色标注 | `Role.Button` / `Role.Switch` / `Role.RadioButton` 显式声明 |
| 标题层级 | 页面标题用 `heading()` |
| 错误提示 | `error()` 语义 + `errorContainer` 视觉 |
| 加载态 | `ProgressBarRangeInfo.Indeterminate` |
| 色觉障碍 | 三态不仅靠颜色区分，`UNKNOWN` 同时改变几何形状（虚线+短横） |

---

## 7. 验收与门禁

### 通过的检查

| 检查 | 结果 |
| --- | --- |
| `./gradlew assembleDebug --offline` | **BUILD SUCCESSFUL** |
| `./gradlew ktlintCheck --offline` | **BUILD SUCCESSFUL** |
| `UiContractTest`（间距字面量 / 字符串引用） | **通过** |
| `ColorSchemeContractTest`（30 个颜色角色 + 15 个排版角色） | **通过** |
| `ScreenHeaderContractTest` / `PluralFormsContractTest` | **通过** |
| `ProjectStructureTest`（含文档地图、仓库结构、导航契约） | **通过** |
| `testDebugUnitTest` 总计 | 286 项，**全部通过** |

### 关于 3 项旁路失败的收尾（已修复）

评审过程中 `ProjectStructureTest` 曾报 3 项失败，根因均为**本次 UI 重构之外的仓库卫生问题**，已一并处理：

| 曾失败的用例 | 根因 | 处理 |
| --- | --- | --- |
| `repository root contains only the allowed entries` | 仓库根出现未登记目录 `.dsh-acl-recovery/`（沙箱 ACL 报告，本机工具产物） | 写入根 `.gitignore`；并登记到 `ProjectStructureTest.AGENT_TOOL_DIRS` / `PRUNED_DIRS` 与 `.vscode/settings.json` 的 `java.import.exclusions`（三处必须同步，缺一即红） |
| `docs are registered in the doc map` | `docs/planning/PLAN-OFFLINE-SYNC.md` 未登记进文档地图 | 在 `docs/README.md` 的「目录结构」与「阅读顺序」中登记 |
| `docs follow module-specific naming conventions` | `PLAN-` 前缀不在 `planning/` 的命名白名单内 | 在 `docs/README.md`「文档命名规范」表与 `ProjectStructureTest.DOC_NAME_PATTERNS` 中**正式新增 `PLAN-<TOPIC>.md` 前缀** |

**为什么选择新增前缀而不是改名**：`PLAN-OFFLINE-SYNC.md` 是另一条工作线正在编写的文档，重命名会产生大量引用同步成本。既然「实施计划」在语义上与「技术设计」确有区别（前者待确认、后者为冻结件），把它固化为一个正式前缀比塞进 `DESIGN-` 更诚实——这也符合本仓库「让结构调整成为一次显式决策」的原则。

**连带教训**：`.agent-tool-dir` 三处登记（`.gitignore` / `ProjectStructureTest` / `.vscode/settings.json`）是**必须同步的耦合**，只改其中一处会让原本通过的用例转红。这次就实测到了

### 未能验证的检查

`assembleRelease` 在本机**无法执行**：`app/build.gradle.kts` 在**配置期**检查签名，`local.properties` 缺少 `adskip.storeFile` 等四项即直接失败（`adskip.unsignedRelease=true` 亦需写入 `local.properties`，作为 Gradle 项目属性无效）。

这对本次重构**不构成风险**：Release 与 Debug 编译同一份 `src/main`，Debug 已通过即证明源码可编译。Release 特有产物差异（R8 混淆、资源压缩）与 UI 重构无关。

---

## 8. 变更规模

```
client/app/src/main/java/com/qingqi/adskip/ui/**
client/app/src/main/res/values*/strings.xml
─────────────────────────────────────────────
+1339 / -479   （合计 1818 行）
```

按文件（变更行数降序）：

| 文件 | 行数 | 角色 |
| --- | --- | --- |
| `components/Common.kt` | 310 | 组件层主体（新增 6 个组件） |
| `home/HomeScreen.kt` | 309 | 信息顺序重排 |
| `components/Feedback.kt` | 156 | 空态/错误态/分段筛选 |
| `profile/ProfileScreen.kt` | 122 | 四区块重组 |
| `settings/SettingsScreen.kt` | 120 | 日程卡合并 + 联动隐藏 |
| `components/StatusOrb.kt` | 119 | 三态几何 |
| `apps/AppsScreen.kt` | 116 | 工具栏 + 计数副标题 |
| `logs/LogsScreen.kt` | 115 | 操作权重对调 |
| `theme/Color.kt` | 109 | 色阶 + 第三态 |
| `theme/Theme.kt` | 83 | 形状阶梯 + 新增状态槽位 |
| `theme/Type.kt` | 74 | 中文字阶重校 |
| `theme/Spacing.kt` | 60 | 新令牌 |
| `profile/PermissionCard.kt` | 44 | 三态落点 |
| `MainActivity.kt` | 27 | 导航配色 |
| `res/values*/strings.xml` | 39 | 新增 8 条文案、删 2 条孤儿项 |

---

## 9. 过程中的事故与修正（供复盘）

| 事故 | 根因 | 修正 |
| --- | --- | --- |
| 5 个 Kotlin 源文件 + 2 个 `strings.xml` 出现 **CRLF** | 批量编辑时换行处理不当；`.gitattributes` 要求 `eol=lf` | 统一转为 LF。**这是本次最隐蔽的问题**：CRLF 让 ktlint 把 `\r` 当作「标识符前的空白」，报出 28 处「opening parenthesis 前不应有空白」——定位完全指向错误方向 |
| `Type.kt` 的 `headlineSmall` 声明被误删 | `Edit` 时 `old_string` 包含了本该保留的声明尾部 | 补回并加注释 |
| `SettingsScreen.kt` 缺 `PaddingValues` import | 改用 `contentPadding` 后未补 import | 补 import |
| `StatusOrb.kt` 缺 `Dp` import | 批量删除 import 时误删仍在使用的项 | 补回 |
| `Type.kt` 两处 KDoc 写在 `value_argument_list` 内 | KDoc 只能用于声明，不能用于实参位置 | 改为 `//` 行注释 |
| 6 处间距字面量被 `UiContractTest` 拦下 | 新写代码未用令牌 | 补 6 个 `UiSizes` 令牌并替换 |

**方法论收获**：批量编辑 Kotlin 源码时，换行符与 import 是最容易静默出错的环节，且**错误信息会把排查方向带偏**（CRLF 报成括号空白）。建议后续改动一律走「小步编辑 + 立即编译」而非批量替换。

---

## 10. 后续建议（未纳入本次范围）

1. **`TopAppBar` 决策应有守护测试**：当前「不加」只写在注释里，后来者可能顺手补上。可加一条契约：一级页面源码中不得出现 `TopAppBar(`。
2. **强调点唯一性应机器化**：`StatTile(emphasis = true)` 目前靠 KDoc 约束「每屏一次」。可扫描单文件内 `emphasis = true` 的出现次数并断言 ≤1。
3. **状态色的对比度应入测试**：`StatusUnknown #B26A00` 在浅色底上的对比度已人工核验，但未机器化。可对全部状态色跑 WCAG AA（4.5:1）断言。
4. **`SegmentedFilter` 的泛型约束足够，但缺无障碍横向导航语义**：多选项分段控件宜标注 `Role.RadioButton` 集合语义。
5. **未做真机视觉验收**：本次仅通过编译与契约测试，未在真机上核对深色模式、超大字号（无障碍字体缩放 200%）、横屏三处的实际渲染。
