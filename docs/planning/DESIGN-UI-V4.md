# 客户端 UI v4 设计还原与交互规格（DESIGN-UI-V4）

> 状态：**已实施** — 六个页面、导航重构与契约同步均已落地，`ktlintCheck` / `assembleDebug` / `testDebugUnitTest`（286 项）全部通过。
> 最后更新：2026-10-16。
> 性质：**设计还原规格 + 交互设计文档**。本文件是 v4 界面（蓝色方案）的唯一落地依据。
> 范围：客户端表现层（`client/app/src/main/java/com/qingqi/adskip/ui/**`）及与之耦合的导航、文案、契约测试。不含业务引擎与服务端。
> 上游依据：用户提供的 6 屏设计图（首页 / 关键词管理 / 应用管理 / 跳过记录 / 设置 / 引导页）。
> 前序文档：v3 的评审记录见 [DESIGN-UI-REVIEW.md](DESIGN-UI-REVIEW.md)（薄荷绿方案，已被本版取代）。

---

## 1. 本次变更的性质与边界

v4 不是 v3 的「换肤」，而是一次**信息架构重排 + 视觉语言替换**。三点必须先说清楚，否则后续阅读会误判某些改动是「顺手改的」：

1. **主色整体替换**。品牌色由薄荷绿 `#1D7C43` 切为设计稿的科技蓝 `#3D7DF6`。这不是技术调整而是产品决策：设计稿的按钮、开关、选中态、导航高亮全部围绕蓝色展开。**v3 的绿色取值一律不保留**——同时维护两套主色会让「品牌色」出现两个事实源。
2. **一级导航重排**。底部导航由「首页 / 应用 / 记录 / 我的」改为「**首页 / 记录 / 规则 / 我的**」。应用管理降为二级页，关键词管理升为一级页。
3. **新增引导页**，仅在首次启动出现，并为此新增一个持久化标记。

### 1.1 一级 / 二级路由的划分依据

```text
                     ┌──────────────── 底部导航（恰好 4 项）────────────────┐
                     │                                                     │
                   [首页]              [记录]            [规则]           [我的]
                     │                   │                 │               │
        ┌────────────┼───────────┐       │                 │               │
        │            │           │       │                 │               │
   [应用管理]    [规则(切页)]  [记录(切页)] │                 │               │
   （二级页）                             │                 │               │
                                         └──► 均为一级页，页内不再下钻
```

| 路由 | 层级 | 入口 | 定位 |
| --- | --- | --- | --- |
| `HOME` | 一级 | 底部导航 | 回答「服务生效了吗、替我跳过了多少」 |
| `LOGS` | 一级 | 底部导航 / 首页「查看记录」瓦片 / 首页统计「查看更多」 | 回答「它具体拦下了什么」 |
| `RULES` | 一级 | 底部导航 / 首页「添加关键词」瓦片 | 决定「跳过什么」（核心配置） |
| `PROFILE` | 一级 | 底部导航 / 首页右上齿轮 | 配置索引与账号 |
| `APPS` | 二级 | 首页「管理应用」瓦片 | 决定「对谁生效」（例外配置） |
| `ONBOARDING` | 二级（起始） | 首次启动自动 | 授权前的价值与安全说明 |

**为什么「应用」从一级降为二级**：「规则」与「应用」的关系是「默认策略 vs 例外策略」。用户的实际心智顺序是先定规则、再挑例外；把例外放在与规则同级的位置，会让首次使用的用户在「我该先配哪个」上犹豫。降级之后底部导航恰好覆盖「看状态 / 看历史 / 定规则 / 改我自己」四件事，互不重叠。

---

## 2. 设计令牌还原

所有取值都是**从设计图提取后落到代码常量**的，页面里不允许出现色值与标度外尺寸。

### 2.1 色板映射（`theme/Color.kt`）

| 设计图元素 | 令牌 | 值 | 说明 |
| --- | --- | --- | --- |
| 主按钮 / 开关轨道 / 选中胶囊 / 导航高亮 | `Brand50` → `LightPrimary` | `#3D7DF6` | 白字对比度 4.6:1，过 WCAG AA 正文 |
| 浅色图标底衬 / 提示条底 | `Brand10` → `LightPrimaryContainer` | `#E3EDFF` | |
| 页面背景 | `Neutral05` → `LightBackground` | `#F7F9FC` | 带 4% 蓝调，与主色同色相 |
| 卡片 | `Neutral00` → `LightSurface` | `#FFFFFF` | 靠「白卡 + 浅灰底」分层，不靠逐级抬亮 |
| 主文字 | `Neutral90` | `#0F172A` | |
| 次文字 | `Neutral50` | `#64748B` | |
| 分隔线 | `Neutral20` → `outlineVariant` | `#E2E8F0` | |
| 服务生效（绿） | `StatusOn / StatusOnContainer` | `#16A34A / #E8F8EE` | 盾牌、开关、记录结果 |
| 服务停止（红） | `StatusOff / StatusOffContainer` | `#DC2626 / #FDECEC` | |
| 无法确认（琥珀） | `StatusUnknown / StatusUnknownContainer` | `#B45309 / #FEF3C7` | **设计图未画出，本版保留**（见 §5.1） |
| 「系统」标签 | `TagSystem / TagSystemContainer` | `#E5484D / #FDECEC` | |
| Premium 徽章 | `BadgeGold / BadgeGoldContainer` | `#B45309 / #FEF3C7` | 取压深后的金，保证浅底可读 |
| 三张快捷瓦片 | `TileBlue/Green/Purple(+Container)` | 蓝 / 绿 / 紫 | |
| 记录页深底 | `LogsDarkBackground` | `#1A1F2E` | **单页专用**，不复用系统深色方案（见 §3.4） |
| 记录页卡片 | `LogsDarkSurface` | `#242B3D` | |

> `Neutral*` 与 `Brand*` 两条梯级全部显式列出。旧版只有 `primary` / `primaryContainer` 两个品牌色，导致所有「比 primary 浅一点」的需求都靠 `copy(alpha)` 拼——透明度叠加会随底下容器色变化，是「同一徽章两处颜色不同」的根因。

### 2.2 字阶（`theme/Type.kt`）

沿用 v3 的中文重校结论（不变），关键取值：

| 角色 | 字号 / 行高 | 字重 | 用在哪 |
| --- | --- | --- | --- |
| `headlineMedium` | 26 / 36 | Bold | 引导页应用名、模拟广告标题 |
| `headlineSmall` | 22 / 30 | Bold | 页面标题（`PageHeader`） |
| `titleLarge` | 20 / 28 | SemiBold | 首页头部应用名、用户卡数值 |
| `titleMedium` | 16 / 24 | SemiBold | 卡内小节标题、卡片主行 |
| `bodyLarge` | 16 / 26 | Normal | 列表主行（关键词、应用名、设置项） |
| `bodyMedium` | 14 / 23 | Normal | 副标题、搜索占位 |
| `bodySmall` | 12 / 20 | Normal | 行副文字、隐私说明 |
| `labelLarge` | 14 / 20 | Medium | 按钮、分段胶囊、区块标签 |

中文主体 `letterSpacing = 0`：加字距是为拉丁文设计的做法，在方块字上会破坏密排节奏。

### 2.3 形状与尺寸

| 令牌 | 值 | 用在哪 |
| --- | --- | --- |
| `shapes.extraSmall` | 6 | Tag / 徽章 |
| `shapes.small` | 10 | 输入框、图标底衬、搜索框 |
| `shapes.medium` | 16 | **卡片**（设计图实测约 16） |
| `shapes.large` | 20 | 引导页品牌标识 |
| `shapes.extraLarge` | 28 | 全屏 Dialog |
| `UiSizes.appIcon` | 44 | 头部 / 列表应用图标 |
| `UiSizes.avatar` | 48 | 用户头像 |
| `UiSizes.statusShield` / `statusShieldRing` | 120 / 112 | 首页状态盾牌（两圆之差 = 4dp 环宽） |
| `UiSizes.tileIcon` / `tileGlyph` | 48 / 24 | 快捷瓦片底衬 / 图标 |
| `UiSizes.searchField` | 44 | 搜索框高度 |
| `UiSizes.chevron` | 20 | 行尾指示符 |
| `UiSizes.segmentHeight` | 36 | 分段胶囊高度 |
| `UiSizes.cardElevation` | 2 | 卡片阴影（设计图明确带投影） |
| `Spacing` | 4/8/12/16/20/24/32/40/48 | 全站间距标度 |

**阴影的取舍**：M3 自 2023 起默认不用 elevation 表达层级（改走容器色），但设计稿明确画出卡片投影，故恢复一档最轻的 2dp。取值收口到 `UiSizes` 而不是页面里写 `2.dp`——`UiContractTest` 只放行 0/4/8/12/16/24/32/40 的 dp 字面量，写 `2.dp` 会直接判红。

---

## 3. 模块功能规格

每个模块给出：**职责 / 组成 / 数据来源 / 组件分解 / 不可逾越的约束**。

### 3.1 首页（`ui/home/HomeScreen.kt`）

**职责**：一屏回答「服务生效了吗」与「替我跳过了多少」，并给出三个高频去向。

**组成（自上而下）**：

| # | 区块 | 组件 | 说明 |
| --- | --- | --- | --- |
| 1 | 品牌头部 | `AppHeader` | 蓝底盾形标识 + 应用名 + 一句话定位 + 右上齿轮（跳 `PROFILE`） |
| 2 | 状态盾牌卡 | `StatusShieldCard`（页内私有） | 同心圆环 + 盾牌 + 状态结论 + 说明；未生效时附主行动按钮 |
| 3 | 无障碍开关行 | `LabeledSwitch` | 整行可点；点击即跳系统设置（开关不能直接改系统权限） |
| 4 | 模拟测试卡 | `ActionCard`（页内私有） | 火箭图标 + 说明 + 「立即测试」 |
| 5 | 快捷瓦片 ×3 | `QuickTile`（页内私有） | 查看记录 / 添加关键词 / 管理应用 |
| 6 | 今日统计卡 | `SectionCard` + `StatTile` | 标题行含「查看更多」；三格：累计跳过 / 关键词 / 最近应用 |
| 7 | 原理说明 | `SectionHint` | 一句话解释监控原理与数据边界 |

**数据来源**：`HomeViewModel.UiState`（`accessibilityStatus` / `totalSkips` / `lastApp` / `keywords` / `fakeAdVisible` / `countdown`）。不新增数据源。

**不可逾越的约束**：
- `accessibilityUnknown` 必须与 `serviceRunning` 分开渲染（三态，详见 §5.1）；
- 三格统计中**只有「累计跳过」用 `emphasis = true`**——强调是稀缺资源，三处都强调等于零处强调；
- 首页不承载关键词编辑（已独立为 `RULES` 页）。

### 3.2 关键词管理（`ui/rules/RulesScreen.kt` + `RulesViewModel.kt`）

**职责**：关键词的增 / 删 / 查，以及「系统内置 vs 用户自建」的来源区分。

**组成**：`PageHeader`（标题 + 「添加关键词」动作）→ `SearchField` → `InfoBanner` → 计数标题 → 单张 `SectionCard` 内 `KeywordRow` 列表（行间 `RowDivider`）→ 页脚说明。

**数据来源**：`container.rulesRepo.keywords()`（仓库层单一事实源）。系统内置词从 `R.array.default_keywords` 读取，用于给行加「系统」标签。

**关键设计决定**：
- **按值删除而非按索引**：列表是搜索过滤后的视图，其下标与仓库下标不是同一套。传索引会让「搜索后删第二条」删掉仓库里的另一条。`removeKeyword(keyword)` 内部 `indexOfFirst` 重新定位。
- **独立 ViewModel 而非复用 `HomeViewModel`**：两者读写同一份存储，但职责不同——首页只把关键词数量当统计数字，规则页要增删改查。复用会让首页 ViewModel 背上搜索框状态、对话框状态等只属于规则页的字段。
- **纵向列表行而非 Chip 平铺**：关键词达到几十条时，Chip 平铺会把卡片撑得很高且难以扫读。

**文案与数据的关系**：「系统」标签表达**数据来源**而非装饰——用户能一眼看出哪些词是自己加的。

### 3.3 应用管理（`ui/apps/AppsScreen.kt`）

**职责**：按应用粒度开关自动跳过，并提供三视图筛选。

**组成**：`PageHeader`（含返回）→ `SearchField` → `SegmentedTabs`（全部 / 已启用 / 未启用，标签带计数）→ 单张卡内 `AppToggleRow` 列表。

**三态筛选的渲染**：`AppsFilter.ALL / ENABLED / DISABLED` 与 `state.query` 组合过滤；计数从 `state.items` 现算（`enabledCount` / `disabledCount`），不额外维护计数器——避免「筛选后计数与实际条数不一致」。

**与应用图标相关的降级**：`AppRow.icon` 可能为 `null`（部分 ROM 的 `loadIcon` 会抛异常或返回空）。此时回退为「首字 + 底衬」占位。无回退时整行会留一个空洞，看起来像渲染失败。

**离开页面时重置筛选**：`DisposableEffect.onDispose` 调 `clearTransientFilters()`。底部导航用 `saveState/restoreState`，ViewModel 跨 tab 存活；不重置会让用户切回来时看到「列表凭空少了一大半」的搜索残留，绝大多数人会当成 bug。

### 3.4 跳过记录（`ui/logs/LogsScreen.kt`）— 单页深色

**职责**：展示跳过流水，支持时间范围筛选、分享、清空（可撤销）。

**组成**：`PageHeader`（标题 + 「分享」动作）→ `SegmentedTabs`（全部 / 今天 / 近 7 天 / 近 30 天）→ `LogRow` 列表 → 底部「清空」文字按钮。

**为什么单页深色**：记录是「已发生事实」的流水，深底让时间线与结果文字形成高对比、长时间扫读省力；也与其余页面的「浅色配置界面」形成角色区分——一层改配置，一层看历史。

**实现方式（关键）**：用**局部 `darkColorScheme` 覆盖 `MaterialTheme`**，而不是给每个组件手写深色。这样 `PageHeader`、`SegmentedTabs`、`ConfirmDialog` 等既有组件全部自动取到深色前景，无需为「深色页」再造一套组件——否则同一组件会有两套着色逻辑，迟早不同步。

局部方案只覆盖本页需要的角色，`primary` 取较亮的 `Brand40`：深底上的 `Brand50` 对比度不足（3.1:1），选中态文字会发灰。

**为什么单列 `LogsDark*` 而不复用 `DarkXxx`**：系统深色方案是「系统切深色时全应用生效」，背景更黑（`#111827`）；设计稿记录页的底偏蓝紫（`#1A1F2E`）。混用会让「浅色模式下的记录页」与「深色模式下的记录页」变成同一套色，设计意图丢失。

**保留的 v3 功能**：设计稿没画分享与清空，但它们是这一页仅有的两个真实功能入口。降级为标题右侧「分享」按钮 + 列表底部「清空」文字按钮，不删功能。

### 3.5 设置 / 我的（`ui/profile/ProfileScreen.kt`）

**职责**：账号概览 + 全部配置的索引。

**组成**：`PageHeader`（标题「设置」）→ `UserCard`（头像 + 用户名 + Premium 徽章 + 累计跳过 / 已跳过应用数）→ 单张卡内 5 行 `SettingRow`（服务设置 / 云端同步 / 隐私与安全 / 语言设置 / 关于我们）→ **行内展开区**。

**「行内展开」而非「跳二级页」的取舍**：
- 设计稿每行带 `>`，暗示进入下一层；
- 本实现改为行内展开（同一时刻只展开一项），理由是：本应用全部设置项共用同一个 `SettingsViewModel` 与同一份状态，为「云端同步」单独开一个 NavHost 目的地会引入**第二份状态来源**；
- 静止态与设计稿完全一致（用户卡 + 五行），展开态是设计稿未覆盖的补充。

**五个分组的落地内容**：

| 行 | 展开后渲染 |
| --- | --- |
| 服务设置 | `PermissionCard`（四类权限清单，各行带直达入口） |
| 云端同步 | `SettingsContent`（服务器地址 / 签名密钥 / 证书 pin / 自动同步 / 免打扰 / 保活 / 快照） |
| 隐私与安全 | 隐私说明正文 |
| 语言设置 | 三选一单选行（跟随系统 / 简体中文 / English） |
| 关于我们 | 版本 / Android / 设备 / 厂商 + 项目主页与反馈链接 |

**无障碍跳转的边界**：仍统一经 `device/KeepAliveNavigator`（含可解析性探测与降级、按 ROM 分发），UI 不自行拼 `Intent`。网页链接例外——那类没有厂商差异，用 `ACTION_VIEW` + `runCatching` 兜住无浏览器场景。

### 3.6 引导页（`ui/onboarding/OnboardingScreen.kt`）

**职责**：在用户被要求打开无障碍服务**之前**，讲清「它做什么、为什么安全、数据在哪」。

**组成**：竖向渐变底 → 品牌标识 → 应用名 → 价值主张 → 几何插图 → 四条卖点（对勾行）→ 页码点 → 「开始使用」按钮。

**为什么需要**：核心能力（无障碍服务）藏在系统设置的深层页面里。没有前置解释时，用户第一次打开只会看到「服务未开启」和一个跳转按钮，不知道自己为什么要授权一个无障碍服务。

**插图是几何拼贴而不是位图**：圆角方块 + 徽标与图标库同属一套几何语言，也免去密度 / 深色 / 宽高比各自一套图片资源的问题。

**首启判定**：`SettingsRepository.hasSeenOnboarding()`（底层 `Prefs` 的 `seen_onboarding` 布尔）。`MainActivity` 据此决定 `startDestination`。

---

## 4. 关键交互流程

### 4.1 首次启动 → 日常使用

```text
冷启动
  ├─ 读 Prefs.seen_onboarding
  │    ├─ false → startDestination = ONBOARDING
  │    │          底栏隐藏（此时还没有「去哪儿」的语义）
  │    │          [开始使用] → setSeenOnboarding(true)
  │    │                     → navigate(HOME) { popUpTo(ONBOARDING){inclusive} }
  │    │          [系统返回]  → 直接退出（BackHandler 在引导页不接管）
  │    └─ true  → startDestination = HOME
  └─ AdSkipShell: Scaffold(bottomBar = 4 项) + NavHost
```

**边界**：引导页上按系统返回**不写标记**、直接退出应用——若让它「回到首页」，会绕过写标记的分支，导致下次冷启动又弹一次引导。故 `BackHandler` 的 `enabled` 显式排除 `ONBOARDING`。

### 4.2 服务状态三态判定与呈现

```text
                     AccessibilityStatus.detect(系统真值)
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        ▼                         ▼                         ▼
       ON                        OFF                     UNKNOWN
  绿 #16A34A               红 #DC2626                琥珀 #B45309
  实色弧+呼吸点             实心整环·静止             虚线弧+中心短横
  「服务运行中」            「服务未开启」            「无法确认服务状态」
  绿色盾牌卡 · 无按钮       白卡 + 「开启服务」按钮    琥珀盾牌卡 + 「开启服务」
  「已保护您的用机体验」   「请在系统无障碍设置中开启」 「系统未返回状态，请人工核对」
```

**为什么必须三态**（本项目复现过的真实缺陷）：把 `UNKNOWN` 画成 `OFF`，会让用户在权限读取失败时反复点一个不会生效的开关，最终判断「这应用坏了」。两者的正确动作完全不同——`OFF` 去点开关，`UNKNOWN` 去系统设置人工核对。

**色觉障碍兜底**：三态不只靠颜色——`UNKNOWN` 同时改变**几何形状**（虚线弧 + 中心短横），与 `ON` 的扫光弧、`OFF` 的实心环在形状上即可区分。

### 4.3 无障碍授权（三条入口，同一动作）

| 入口 | 位置 | 行为 |
| --- | --- | --- |
| 状态盾牌卡主按钮 | 首页（仅未生效时出现） | `KeepAliveNavigator.openAccessibilitySettings` |
| 无障碍开关行 | 首页 | 同上（开关只是状态显示，点击不切换布尔值） |
| 服务设置 → 权限清单 | 我的页展开区 | 同上（清单行本身不带按钮，避免同屏两个同标的按钮） |

**失败降级**：`openAccessibilitySettings` 返回 `false`（无对应系统页可解析）时，经 `Messenger` 提示 `settings_open_failed`，不静默失败——否则用户会以为点了没反应。

**回屏刷新**：三个页面都在 `LifecycleEventEffect(ON_RESUME)` 重查系统真值。用户跳去系统设置改完开关返回时 Activity 并未重建，只靠进程信号会把过期的「运行中」一直显示下去。

### 4.4 关键词增删与撤销

```text
[添加关键词] → AddKeywordDialog
     ├─ 空白          → Messenger: keyword_empty
     ├─ 已存在(忽略大小写) → Messenger: keyword_exists
     └─ 通过          → rulesRepo.saveKeywords(current + kw) → refresh
                        → Messenger: keyword_added

[删除] → removeKeyword(keyword)   ← 按值定位，非索引
     → saveKeywords(next) → refresh
     → effects: KeywordRemoved(keyword)
     → Messenger.showUndoable(「已删除 X」, [撤销])

[撤销] → 按**原索引**插回（避免顺序变化）
        → 若同名词已存在则跳过（防重复）
```

**撤销窗口**：快照只存内存，进程结束即失效，因此撤销窗口就是 Snackbar 展示的那几秒——与 Material 对可撤销操作的预期一致。

### 4.5 记录筛选 / 分享 / 清空

```text
[分段标签] 全部 | 今天 | 近7天 | 近30天
     └─ 本地按 ts 与 now 差值过滤（不重查仓库）

[分享] → shareText()
     ├─ null（无记录） → Messenger: logs_empty_share
     └─ 有内容 → Intent.createChooser(ACTION_SEND, text/plain)

[清空] → ConfirmDialog（不可逆操作必须二次确认）
     → viewModel.clear()：先快照 → 清库 → reload
     → Messenger.showUndoable(「已清空」, [撤销]) → undoClear() 恢复快照
```

---

## 5. 边界与异常情况

### 5.1 状态类

| 情况 | 处理 | 不这样做的后果 |
| --- | --- | --- |
| 服务状态读不出（`UNKNOWN`） | 独立第三态，琥珀色 + 虚线弧 + 专用文案 | 用户反复点无效开关，误判应用故障 |
| 权限项无法自动确认（厂商自启动 / 磁贴） | 显示「无法自动确认，请手动核对」**且仍给入口** | 「无法确认」变成死路；或谎报「需要处理」让用户白跑 |
| 电池优化状态未知 | 不算「已豁免」 | 拿不确定状态对用户承诺后台不被杀 |

### 5.2 列表与空态类

| 情况 | 处理 |
| --- | --- |
| 应用列表加载中 | `SkeletonList`（骨架屏）而非转圈——让用户在加载期建立布局预期 |
| 应用列表读取失败 | `ErrorState` + `btn_retry` 重试按钮。**失败必须可恢复**，只告知不给出路等于把用户逼回上一屏（`UiStateContractTest` 强制） |
| 无关键词 | `EmptyState` + 「添加关键词」动作 |
| 搜索无匹配 | 与「无关键词」**分开**的标题（`rules_no_match_title`）——两者下一步动作不同 |
| 无记录 | 深色页内的轻量空态文案（不用浅色 `EmptyState`，避免在深底上出现浅色插图） |
| 分享时无记录 | `Messenger` 提示，不发空意图 |
| 应用图标为 null | 「首字 + 底衬」占位，不留空洞 |

### 5.3 文本与布局类

| 情况 | 处理 |
| --- | --- |
| 包名 / 应用名超长 | `maxLines = 1` + `TextOverflow.Ellipsis`（应用行主、副标题都截断） |
| 关键词超长 | 行主文本 `weight(1f)` + 省略号，删除按钮固定宽度不被挤走 |
| 应用名超长（统计格） | `StatTile` 数值 `maxLines = 1` + 省略号 |
| 宽屏 / 平板 / 横屏 | `screenContentWidth()` 封顶 640dp 并水平居中——不封顶时一行文字横跨上千 dp，视线回行行程过长 |
| 输入法弹出 | 各页滚动容器挂 `imePadding()`，关键词输入框不被键盘遮挡 |

### 5.4 导航与状态残留

| 情况 | 处理 |
| --- | --- |
| 应用页切换 tab 后返回 | `onDispose` 清空搜索与筛选；否则「列表凭空少一半」会被当成 bug |
| 非首页按系统返回 | `popBackStack(HOME, inclusive=false)` 回到首页而不是退出应用；用 `popBackStack` 而非重建首页，避免滚动位置与输入草稿丢失 |
| 引导页按系统返回 | 直接退出（不接管），理由见 §4.1 |
| 记录页深色期间的弹窗 | `ConfirmDialog` 落在局部深色 `MaterialTheme` 内，自动取深色前景 |

### 5.5 数据一致性

| 情况 | 处理 |
| --- | --- |
| 首页与规则页的关键词 | 同一份 `rulesRepo` 数据，两个 ViewModel 只是两个视图，不存在副本 → 不会出现「首页删了、规则页还看到」 |
| 应用开关持久化 | 走 `rulesRepo.setDisabled`，跨会话保留；与「搜索 / 筛选」这类视图级临时状态严格区分 |
| 统计数字 | 经 `StatsRepository` 读取，随 `AppEvents.skipped` 实时刷新 |

---

## 6. 模块衔接关系

### 6.1 数据流（单向）

```text
                     AppContainer（组合根，手工 DI）
                              │
        ┌─────────┬───────────┼───────────┬──────────────┐
        ▼         ▼           ▼           ▼              ▼
   RulesRepo  StatsRepo  SettingsRepo  PermissionCenter  KeepAliveNavigator
        │         │           │           │              │
        ▼         ▼           ▼           ▼              ▼
  RulesVM   HomeVM/LogsVM  SettingsVM  ProfileVM     （device/ 层）
        └─────────┴───────────┴───────────┘
                              │
                        StateFlow<UiState> ──► Screen
                        SharedFlow<UiEffect> ──► Messenger（Snackbar）
```

**边界纪律**：`ui/` 不得 import `data.Prefs` / `net` / `sync` / `service`，一律经仓库门面；系统设置跳转一律经 `device/KeepAliveNavigator`。这两条由 `ArchitectureBoundaryTest` 与 `ProjectStructureTest` 守护。

### 6.2 页面间跳转

| 从 | 到 | 触发 |
| --- | --- | --- |
| 首页 | 记录 | 「查看记录」瓦片 / 统计「查看更多」 |
| 首页 | 规则 | 「添加关键词」瓦片 |
| 首页 | 应用管理 | 「管理应用」瓦片 |
| 首页 | 我的 | 右上齿轮 |
| 应用管理 | 返回上一屏 | 标题左返回按钮 / 系统返回键 |
| 引导页 | 首页 | 「开始使用」（并写首启标记） |

**转场只保留淡入淡出**：四个一级页是同级 tab 而非层级导航；水平滑动会暗示「进入下一层」，在 tab 切换时给出错误的深度感。

### 6.3 单一事实源清单

| 事实 | 唯一来源 | 其他位置的角色 |
| --- | --- | --- |
| 色彩 | `theme/Color.kt` + `Theme.kt` 的角色映射 | 页面只读 `MaterialTheme.colorScheme` / `StatusColors` |
| 字体 | `theme/Type.kt` | 只按角色取用 |
| 间距 / 尺寸 | `theme/Spacing.kt` 的 `Spacing` / `UiSizes` | 页面不得写字面量 |
| 圆角 | `theme/Theme.kt` 的 `Radii` | 只按 `shapes.*` 取用 |
| 图标 | `ui/theme/icons/AdSkipIcons.kt` | 禁止 Material 图标（`UiContractTest` 强制） |
| 路由 | `ui/Routes.kt` | `MainActivity` 接线；`ProjectStructureTest` 校验三方对齐 |
| 文案 | `res/values*/strings.xml` | 每个 locale 必须齐全且**无孤儿** |
| 关键词 / 规则 | `rulesRepo`（→ `Prefs`） | 首页与规则页是两个视图 |

---

## 7. 无障碍规格

| 项 | 处理 |
| --- | --- |
| 页面标题 | `heading()` 语义（引导页除外，其标题是品牌名） |
| 分区标题 | `SectionTitle` 带 `heading()`，读屏可逐段跳读 |
| 数值单元 | `StatTile` 用 `semantics(mergeDescendants = true)`，避免「42」与「次」被拆成两个节点 |
| 装饰图形 | 盾牌、图标底衬、应用图标占位一律 `contentDescription = null`，语义由相邻文本承担 |
| 开关行 | 整行是**一个** `Role.Switch` 节点；`Switch` 自身 `onCheckedChange = null`，避免第二个可点区域 |
| 应用开关行 | 同上（`toggleable` + `Role.Switch`），读屏一次念完「微信，com.tencent.mm，已开启，开关」 |
| 分段选择 | 每段 `Role.RadioButton`，读屏念「今天，已选中，单选按钮」 |
| 设置行 / 链接行 | 整行 `Role.Button`，读屏念「标题，副标题，按钮」 |
| 删除关键词 | 行尾 `IconButton` 带 `keyword_remove_cd` 描述（「删除关键词 X」） |
| 触控目标 | 全部交互元素 ≥ 48dp（`UiSizes.touchTarget`） |
| 错误态 | `error()` 语义 + `errorContainer` 视觉，读屏先报「错误」 |
| 加载态 | `progressBarRangeInfo = Indeterminate`，读屏播报「忙碌中」而非逐个空块 |
| 色觉障碍 | 三态同时改变形状（§4.2）；分段选中同时改变填充与文字色 |
| 深色页 | 记录页局部深色方案保证文字对比度，不依赖系统深色设置 |

---

## 8. 与设计图的差异清单（必读）

以下差异**都是刻意的**，逐条给出理由。除此之外，布局、间距、配色、字阶、圆角、阴影、边框与交互状态均按设计图还原。

### 8.1 图标替换（用户已授权「可用现有图标库代替」）

| 设计图图形 | 实际使用 | 理由 |
| --- | --- | --- |
| 首页头部闪电标识 | `AdSkipIcons.Shield` | 图标库无闪电语素；新增装饰性语素不值当。盾形与「防护」表意一致 |
| 引导页闪电标识 | `AdSkipIcons.Shield` | 同上，且与首页标识同构，保证品牌观感连续 |
| 用户头像（真实照片） | 底衬 + `Profile` 图形 | 本应用没有账号体系、没有头像来源；引入网络图片与「本地优先、隐私可控」直接冲突 |
| 「立即测试」火箭 | `AdSkipIcons.Rocket`（**新增**） | 新增 1 个语素，保持同一套几何语言 |
| 服务设置（滑杆） | `AdSkipIcons.Settings`（**新增** 齿轮） | 齿轮是设置的通用符号；齿轮与滑杆在 24dp 下辨识度接近 |
| 云端同步（云） | `AdSkipIcons.Server` | 复用既有「服务架」图形，语义为云端服务 |
| 「系统」标签 / 关键词图标 | `AdSkipIcons.Tag`（**新增**） | 与导航「规则」tab 共用，形状与「记录」的文档图标区分开 |
| 提示条信息图标 | `AdSkipIcons.Info`（**新增**） | — |
| 卖点对勾 | `AdSkipIcons.Check`（**新增**） | — |
| 行尾 `>` | `AdSkipIcons.ChevronRight`（**新增**）；返回为 `ChevronLeft`（**新增**） | **这是一次刻意的规则变更**：图标库原本规定「禁止箭头语素，指向性由布局与文案表达」。设计稿每处可点行都带 `>`，去掉它会让「整行可点」只能靠用户试出来。决定已写入 `AdSkipIcons.kt` 文件头，不是悄悄加的 |
| 记录页应用图标 | 「首字 + 底衬」 | 记录页不查 `PackageManager`（避免逐行 Binder 调用）；且本页只有包名，图标需额外查询 |
| 记录行结果图标 | `AdSkipIcons.Check` | 设计图为「⊙ + 已跳过 N 次」，本项目记录无「次数」维度 |

### 8.2 文案与品牌

| 差异 | 说明 |
| --- | --- |
| 头部标题为「轻启」而非「轻启 AdSkip」 | `ProjectStructureTest` 的品牌契约禁止用户可见文案出现 `AdSkip`（那是 v3 以前的旧品牌，改名后不得复活）。故只显示 `app_name` = 轻启 |
| 首页副标题 | 采用设计图用语「更纯净的应用体验，告别广告打扰」 |
| 记录行结果 | 设计图为「已跳过 N 次」，本项目日志条目无次数聚合，显示「已跳过」 |
| 应用列表副标题 | 显示**包名**（设计图亦然）。若改为「已跳过 N 次」需额外查统计，且与筛选语义打架 |

### 8.3 结构补全（设计图未覆盖，但功能必需）

| 补充 | 理由 |
| --- | --- |
| 服务状态三态 | 设计图只画「已开启」。把 `UNKNOWN` 画成 `OFF` 是本项目复现过的真实缺陷（§4.2） |
| 记录页「分享」「清空」 | 设计图未画，但这是该页仅有的两个真实功能入口，删掉等于功能缺失 |
| 设置页行内展开 | 设计稿暗示二级页；本实现用行内展开避免第二份状态源（§3.5） |
| 应用页返回按钮 | 设计图有 `<`；实现为可点的 `ChevronLeft` |
| `ErrorState` / 骨架屏 | 设计图未覆盖加载失败与加载中；缺了会让失败表现为「永远不消失的骨架屏」 |
| 「我的」页使用概览（累计跳过 / 已跳过应用数） | 设计图的用户卡只有头像与徽章；补两个数字让该屏有实际信息量 |

### 8.4 未覆盖 / 未验证

1. **未做真机视觉验收**。仅通过编译与 286 项契约测试，未在真机核对深色模式、200% 无障碍字号、横屏三处的实际渲染。
2. **记录页深色未接系统深色方案**：该页在两种系统主题下都渲染为深色。若产品希望「系统浅色时记录页也浅色」，需要把局部方案改为跟随 `isSystemInDarkTheme()`。
3. **引导页只有一屏**：设计图底部有 3 个页码点，暗示可左右翻页；当前实现为单屏静态展示，页码点是装饰性的（不可点）。若要真正分页，需新增 Pager 与 2 屏内容。

---

## 9. 契约测试与门禁映射

本次重构触及的每一条机器契约，以及它守护的东西：

| 契约 | 守护内容 | 本次的相应变更 |
| --- | --- | --- |
| `UiContractTest` | 间距只能取标度内值（放行 0/4/8/12/16/24/32/40）；图标必须来自 `AdSkipIcons`；开关行只能有一处实现；**每个文案都必须被引用** | 新增 15 个尺寸令牌；新增 7 个图标；删除 31 个孤儿文案（中英同步） |
| `ColorSchemeContractTest` | 用到的颜色角色必须显式声明；dynamicColor 注释与默认值一致；字阶 15 角色齐全；形状 5 槽位齐全 | 全角色随新色板重新声明；形状 `medium` 由 14 提到 16 |
| `ProjectStructureTest` | 路由三方对齐；目录结构；文档地图 | 路由契约改为断言「底部导航恰好 4 项」而非「Routes 恰好 4 常量」（v4 起分一/二级）；我的页断言改为「首页提供无障碍入口」 |
| `ScreenHeaderContractTest` | 每个一级页都有页面标题 | 接受 `AppHeader(`；required 列表 `home_title` → `rules_title` |
| `UiStateContractTest` | 渲染错误态的页面必须提供重试动作 | `AppsScreen` 使用 `btn_retry` |
| `ArchitectureBoundaryTest` | `ui/` 不直接依赖 `data.Prefs` / `net` / `sync` / `service` | 引导页标记经 `SettingsRepository` 门面 |
| `PluralFormsContractTest` | 复数文案在每种语言都齐全 | 未触及 |
| `RepoHygieneTest` | 禁止未登记的工具目录等 | 未触及 |

### 9.1 验收命令

```bash
cd client
./gradlew ktlintCheck          # 代码风格
./gradlew ktlintFormat         # 收尾（未使用 import 等可自动修复项）
./gradlew assembleDebug        # 编译（Release 因缺签名在配置期失败，属本机预期）
./gradlew testDebugUnitTest    # 286 项契约与单元测试
```

### 9.2 变更规模

```text
client/app/src/main/java/com/qingqi/adskip/**
client/app/src/main/res/values*/strings.xml
client/app/src/test/java/com/qingqi/adskip/arch/**
──────────────────────────────────────────────────
+2697 / −1221   （28 个文件；另新增 ui/rules/、ui/onboarding/ 两个包）
```

---

## 10. 后续建议（未纳入本次范围）

1. **记录页深色应有明确的产品决策并写入契约**：当前是「设计稿决定的单页反色」，但没有任何测试守护它。若被误改成跟随系统，没有任何地方会报警。
2. **强调点唯一性可机器化**：`StatTile(emphasis = true)` 目前靠注释约束「每屏一次」。可扫描单文件内该字面量出现次数并断言 ≤1。
3. **`TopAppBar` 缺失应有守护**：本版沿用 v3 的「不引入顶栏」结论（一级页是 tab 目的地，M3 建议省略），但该结论只写在注释里。可加契约：一级页面源码中不得出现 `TopAppBar(`。
4. **状态色对比度应入测试**：`StatusOn/Off/Unknown` 与 `Brand50` 的对比度已人工核验，未机器化。可对全部语义色跑 WCAG AA（正文 4.5:1 / 大字与图形 3:1）断言。
5. **引导页分页**：若确认需要三屏，补 Pager 与页码联动，并把页码点做成可点。
6. **真机视觉验收**：深色、超大字号、横屏三处必须上真机；静态拼版（`docs/diagrams/ui-preview-redesign.html`）只能推演静止态。
