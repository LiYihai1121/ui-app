# 变更日志

本文件记录面向用户和运维的版本变更。版本号遵循 Semantic Versioning，正式版本标签以 Git 中的 `vX.Y.Z` 为准。

版本与提交的完整对应关系见 [发布历史与提交链路](docs/planning/RELEASE-HISTORY.md)。

## 目录

- [Unreleased](#unreleased)
- [3.1.0](#310---2026-09-28)
- [3.1.0-rc.1](#310-rc1---2026-09-28)
- [3.0.4](#304---2026-09-27)
- [3.0.3](#303---2026-09-26)
- [3.0.2](#302---2026-09-04)
- [3.0.1](#301---2026-09-04)
- [3.0.0](#300---2026-09-04)
- [2.2.0](#220---2026-08-24)
- [2.1.0](#210---2026-08-24)
- [2.0.0](#200---2026-08-24)

## [Unreleased]

本轮是「UI 重新设计与权限体系重构」，贯穿三条原则：**权限与系统开关一律三态**（无法确认 ≠ 未开启）、**同一事实只有一份真值源**、**能机器检查的约定都写成门禁**。

### Added

- **权限与系统开关清单**：「我的」页新增一张可核对清单（`device/PermissionCenter` + `ui/profile/PermissionCard`），把此前分散在首页状态环、「我的」页无障碍卡片与设置页三处的四类开关——无障碍服务、电池优化豁免、厂商自启动、快捷磁贴——收敛到一处，每项给出真实状态与直达入口。清单顶部显示「已就绪 N / 4 项」。
- **错误态组件 `ErrorState`**：此前全 app 只有空态，加载失败会显示成「暂无内容」——用户既不知道出了错，也不知道重试有用。错误态与空态分开，并带 `error()` 语义供读屏播报。
- **可复用组件**：`StatusDot`（状态点，颜色 + 形状双重编码，色盲用户可辨）、`Modifier.screenContentWidth()`（大屏内容封顶，此前硬编码在 MainActivity）。
- **`device/AccessibilityStatus` / `device/BatteryExemption`**：把「系统真值 / 进程信号 / 查询失败」收敛为 `ON`/`OFF`/`UNKNOWN` 三态，判定逻辑是纯函数，可 JVM 单测穷举。

### Changed

- **「我的」页按来访目的重排**：权限清单提到首位（唯一会阻塞全部功能的内容），其后依次为使用概览、设置、无障碍入口与关于。此前权限清单被压在设置之后，而权限没配好时设置项几乎都是无效配置。
- **首页补页面标题**：此前是四个一级页面里唯一没有标题的，用户切过去后没有任何位置锚点。
- **无障碍状态改读系统真值**：UI 此前只订阅进程信号，用户在系统设置里关掉无障碍而 Service 的 `onDestroy` 回调未到时，首页与「我的」页仍显示「运行中」，而快捷磁贴（读系统真值）已显示「已停止」——同一台设备两个说法。两屏现在都在 `ON_RESUME` 时重查真值。
- **厂商识别统一入口**：`ProfileViewModel` 改为经 `KeepAliveNavigator.detectVendor()`（含异常保护与日志），与设置页同源；此前直调裸函数，失败时无迹可查。
- **配色补全**：`Theme.kt` 补上一直走 M3 默认值的 `surfaceContainer` / `surfaceContainerLow` / `surfaceContainerHighest`（默认值是紫调中性色，与品牌蓝不同色相，卡片底色一直与应用配色脱节）。
- **深色窗口主题**：新增 `values-night/themes.xml`，深色系统下窗口背景与启动闪屏不再沿用浅色主题（Compose 只在首帧绘制后接管，此前中间那段是刺眼白屏）；状态栏由写死品牌蓝改为透明。

### Fixed

- **权限清单行按钮文案过长导致标题不可读**：行内按钮复用了「打开自启动 / 后台管理」（英文 `Open Auto-start / Background Manager`），在 1080px 屏上约占 850px；而该行给标题列 `weight(1f)`，剩余空间被压到约 210px，三字标题只能显示一个字符加省略号（实测 `B...` / `A...` / `Q...`）。现改用专用短文案（`permission_open` = 去设置 / Open）。**此缺陷由模拟器目视验证发现，编译与全部契约测试均未能拦住。**
- **英文单复数错误**：四条计数文案（`apps_count` / `logs_subtitle` / `stats_total_short` / `fake_ad_countdown`）此前是普通 `<string>`，英文下会产出 `Skipped 1 times`、`1 records`、`Auto-close in 1 seconds` 这类语法错误。现改为 `<plurals>`（中文只需 `other`，英文补 `one`/`other`），四个读取点同步改用 `pluralStringResource`。
- **英文默认关键词回落中文**：`default_keywords` / `default_view_ids` 只在默认 locale 声明，英文环境会回落到「跳过 / 跳過 / 跳过广告 / 关闭广告」——对英文广告一个都命不中。现补齐英文数组（`skip` / `skip ad` / `skip ads` / `close ad` / `close ads` / `no ads`）。
- **「我的」页同一事实说两遍**：权限清单的无障碍行报状态，紧随其后的卡片又渲染整句提示。现清单行负责状态、卡片作为唯一行动入口，并删除失去引用方的 `profile_accessibility_section`。
- **应用管理页加载失败卡死**：`queryIntentActivities` 无异常保护，抛异常时协程中断、`loading` 永远停在 `true`，用户看到永不消失的骨架屏且无重试入口。现捕获异常并落到可重试的错误态。
- **应用管理页空态文案错配**：「已加载完且无应用」的空态显示的是「正在加载应用列表…」。
- **电池豁免跳转后不刷新**：状态只在进屏时读一次，用户点按钮跳去系统设置允许后返回，按钮仍停在「允许后台运行」。
- **权限清单里重复的无障碍入口**：清单行与紧随其后的无障碍卡片提供同一个跳转按钮，改为状态留在清单、入口归卡片。
- **无障碍播报缺陷**：底部导航图标与同行文字重复播报 tab 名；状态环与相邻文字重复播报服务状态；骨架屏对读屏是空白；全屏测试浮层未做语义隔离（被遮住的控件仍可被 TalkBack 聚焦激活）；关键词 chip 点本体即删除但删除语义只挂在尾部图标上。
- **`UiContractTest` 两条守护自上线起从未生效**：间距检查的正则匹配的是 Kotlin 里不存在的 `20dp` 写法，对真实写法 `20.dp` 永不命中（22 处标度外取值长期潜伏）；文案检查用裸子串匹配，死文案 `settings_battery` 被 `settings_battery_allow` 命中而逃检。修复后暴露的违规已全部落到设计令牌。

### Tests

- 新增契约测试：`PermissionCenterTest`、`AccessibilityStatusTest` / `AccessibilityStatusContractTest`、`BatteryExemptionTest` / `BatteryExemptionContractTest`、`ColorSchemeContractTest`、`ScreenHeaderContractTest`、`VendorDetectionContractTest`、`UiStateContractTest`、`PluralFormsContractTest`。
- 契约文件由 4 个增至 10 个；`testDebugUnitTest` 由 189 个用例增至 223 个。

> **验证范围**：已在模拟器（Android 14 / API 34，`AdSkipTest` AVD）上实装并逐屏目视确认首页、应用管理、跳过日志、「我的」四个一级页面；上述「权限清单行按钮」缺陷即由此发现。**厂商 ROM 相关行为（自启动入口、后台管理跳转、磁贴）仍未经真机验证**，与 `ROADMAP` 中「模拟器不能替代厂商 ROM 验收」的既有保留一致。
>
> 字阶 15 个 M3 角色中 7 个、形状 5 个槽位中 2 个当前无调用方——这是 M3 要求完整体系所致，已用契约固化「体系完整」，非遗漏。

## [3.1.0] - 2026-09-28

正式版。相对 3.1.0-rc.1 的变更：协议升到 schema 2 并接入选择器第三通道、品牌更名为「轻启」并重设计图标、UI 规范对齐、引入静态检查门禁。

> **发布验收覆盖声明（重要）**：按 CONTRIBUTING.md「发布验收」，触及无障碍服务、快捷磁贴、厂商跳转、后台调度、系统权限的变更**必须完成真机验收后才能发正式版**。本次发布的真机矩阵（Android 8/13/14/15 × MIUI/HarmonyOS/ColorOS/OriginOS）**尚未完成**，已知验证仅覆盖模拟器（Android 15 / API 35）。本次由维护者显式决定覆盖该规则先行发布正式版。
> 已知风险：磁贴与厂商保活路径在真实 ROM 上的行为可能与模拟器不同；如出现问题，按 CONTRIBUTING「紧急变更与回滚」走 hotfix/*（制品回滚或 git revert）。

### Added

- **更名：净启动 AdSkip → 轻启**（仅用户可见名称；`applicationId` 仍为 `com.ldp.adskip`，可覆盖升级，无需卸载）：
  - 桌面名称 `app_name`、无障碍服务名 `service_name`、快捷磁贴名 `tile_label` 三方同步改为「轻启」——这三处分别显示在桌面、系统无障碍列表和快捷设置里，只改其中一处就会出现「同一个应用两个名字」；
  - 全部 locale（`values` / `values-en`）内的用户可见提示同步清理旧品牌（无障碍引导「开启「轻启」」、保活提示「把轻启加入后台运行白名单」等）；
  - 新增守护测试 `ProjectStructureTest.product brand is declared consistently across every locale`：把「品牌名唯一事实源」「每个 locale 都必须声明 `app_name` / `service_name` / `tile_label`」「旧品牌名不得复活」升级为可执行门禁，防止后续再出现只改一种语言的「改一半」改名；
  - 包名、`Theme.AdSkip`、`rootProject.name`、服务端英文标识与文档中的历史命名**刻意保持不变**：它们不面向用户，改动会破坏升级链与发布脚本追溯。

- **静态检查门禁 ktlint**：新增仓库根 `.editorconfig` 作为「代码长什么样」的机器可读事实源（charset/行尾/缩进/行宽 + ktlint 规则集），并接入 `ktlintCheck` 任务——CI 的 `Android Build & Test` job 与发布流水线均已纳入，AGENTS.md、PR 模板、ARCHITECTURE、ROADMAP-ADS、DEV-ENVIRONMENT 的门禁清单同步更新。此前客户端**没有任何格式或静态检查工具**，`ArchitectureBoundaryTest` 只管 import 边界，不管单文件内的写法一致性。启用即修掉存量违规：6 处 `import org.junit.Assert.*` 通配导入改为显式导入，`Color.kt` 与 `Feedback.kt` 的文件级说明由悬空 KDoc 改为块注释。

### Changed

- **启动器图标与快捷磁贴图标重设计**：原图标是通用的「快进」符号（两个三角加一根竖条），与任意播放器/音乐类应用撞脸，在 48dp 下细节已糊成一条白杠。现改为**实心圆盘 + 负空间「跳过」箭头**——箭头从圆盘中穿出的形态直指「开屏广告被跳过」，负空间在小尺寸下对比度最高，磁贴 24dp 与主题图标下同样可辨。
  - 背景层由纯色改为品牌蓝对角渐变（#1E88E5 → #0D47A1，夹住 M3 主色 #1565C0），删除不再被引用的 `ic_launcher_background` 颜色资源；
  - **新增 `monochrome` 单色层**：此前缺失，Android 13+ 开启主题图标后本应用不会出现在主题图标选择器中；
  - 磁贴图标改为与启动器同源的箭头符号（原为另一套坐标的独立图形，24dp 下圆盘会糊，只保留符号）；
  - 几何按自适应图标的 66dp 安全区校核：圆盘直径 62（23..85）落在安全区内，负空间完全包含于圆盘内——否则 evenOdd 填充会把箭头重新填上，负空间消失。
- **Jetpack Compose BOM 2026.06.01 → 2026.08.00（Compose 1.12 / material3 1.5 Expressive）**：官方明确要求「always use the latest Compose BOM」——BOM 是各 Compose 库互相兼容的同一时刻快照，单独升某个库反而会制造不匹配组合。因 Compose 1.12 强制要求 `compileSdk 37`，`compileSdk` 同步 36 → 37；`targetSdk` 刻意仍留 35（升 36 会引入 Android 16 强制 edge-to-edge 等行为变更，与 UI 改动耦合会让「这次界面为什么变了」难以归因）。
- **CI 的 Android SDK 声明与约定插件收口同源**：`ci.yml` 两处 `packages` 由长期滞后的 `platforms;android-35 build-tools;35.0.0` 改为 `platform-tools build-tools;36.0.0` + 单独的 beta 通道安装步骤。此前靠 Gradle 自动解析兜住，属于「靠工具兜底」而非「显式声明」，AGP 一收紧校验就会在 CI 上突然失败。
  **注意**：Compose 1.12 硬性要求 `compileSdk 37`，而 Android 17 截至 2026-09 仍是 **Beta**、只发布在 beta/canary 通道，且**包名带次版本号**（正确写法是 platforms;android-37.0，同级还有 37.1 / 37.2）。`setup-android` 默认只解析稳定通道，直接在 `packages` 里写 `platforms;android-37` 会报 `Failed to find package`——因此必须用 `sdkmanager --install "platforms;android-37.0" --channel=1` 显式安装。Compose 1.13.0-alpha01 的发布说明已写「Remove requirement for compileSdk 37」，Android 17 转正后这一步可简化。
- **Agent 工具配置收敛到 `kilo.json`**：`opencode.json` 已是 legacy 路径（Kilo 仍会加载，但排在 `kilo.json` 之后），其唯一有效内容是 `permission.skill."*": "allow"`，现迁至根 `kilo.json` 并补 `skills.paths: ["./skills"]`，原文件删除。顺带根治「工具每次运行都重写配置、把工作区改脏」——扩展写入的 `commit_message.prompt` 之类的字段会随文件一起消失。
- **Agent 技能从 `.opencode/skills/` 迁到受版本控制的 `skills/`**：Kilo 只扫描 `.kilo` 与 `.kilocode` 两个配置目录，**`.opencode` 目录不会被加载**，其中的 `branch-guard` 技能在当前工具链下从不生效；而 `.kilo/` 已被根 `.gitignore` 整体忽略（Agent Manager 状态目录），技能放进去无法入库，等于每个人的技能集各不相同——那正是「第二份真相」的另一种形态。现随仓库版本控制并由 `kilo.json` 显式挂载；`ProjectStructureTest` 与 `ARCHITECTURE.md` 2.2 的根白名单同步更新（移除 `.opencode`、登记 `skills/` 与 `kilo.json`，治理文件仍为 9 个）。
- **首页重设计（信息层级 + 动作分级）**：依据 v3.1 真机截图在 1080×2340 上的实测——旧版状态卡就占掉首屏约 60% 高度，**关键词输入框必须滑过一次才看得到**，而关键词是这个 app 唯一需要用户主动操作的东西。据此重排：
  - `StatusHero` 由竖排改为**横排**，状态环 108dp → 56dp，状态卡高度从约 300dp 降到约 150dp，整页内容**无需滚动即全部可见**；
  - **动作分级**：服务未开启时主 CTA 用 filled（这是用户必须做的事），运行中降级为 tonal（避免让用户误以为还有必须点的操作）；「测试」由独立全宽按钮收进状态卡并降为 `TextButton`，它是可选的验证动作，不该和「去开启服务」抢焦点；测试按钮同时受 `enabled = running` 约束；
  - 统计由两张 `headlineMedium` 大数字卡降为**一行摘要**（`累计跳过 N 次 · 最近 X`）——统计的作用是佐证服务在干活，不是用户的主要目标；「最近应用」为空时整段不显示，空占位会让用户误以为数据丢了。
- **设置并入「我的」页，底部导航收敛为四项**：原独立的「设置」一级页签已移除，云端规则、免打扰时段、系统保活与快捷磁贴等内容整体内嵌进「我的」页。理由是设置与「关于本机」属同一心智模型，分成两页会让用户为改一个免打扰时段而去第三个页签。底部导航现为 首页 / 应用管理 / 跳过日志 / 我的 四项。实现上 `SettingsScreen` 改造为 `SettingsContent`——**去掉自带标题与滚动容器**，避免在「我的」页里嵌一个自带标题的滚动列（标题重复 + 滚动冲突）；全部既有 ViewModel 与逻辑原样复用，不做复制。
- **「打开无障碍」入口移入「我的」页**：新增「无障碍服务」卡片与「打开无障碍设置」按钮。首页状态环的主按钮保留（服务未开启时的首屏行动不变），「我的」页作为第二入口，覆盖服务被系统强杀后用户直接翻到末页找设置的路径。跳转仍统一经 `KeepAliveNavigator`，UI 不自行拼 `Intent`。


### Added

- **「我的」用户页**：底部导航新增一级入口（本页），展示本机使用概览（累计跳过 / 已跳过应用数 / 服务状态）与应用、设备信息（应用版本、系统版本、设备型号、ROM 厂商），并明示「跳过记录与统计均只保存在本机」。实现遵循既有单向下行数据流：`ui/profile/ProfileScreen` + `ProfileViewModel`（StateFlow 驱动），统计经 `StatsRepository`、版本经 `SettingsRepository` 读取，UI 不自行查 `PackageManager`（遵守 ARCHITECTURE.md 2.1 边界）。
- **导航契约守护测试**：`ProjectStructureTest` 新增断言，把「一级路由恰为 4 项且不再声明 `SETTINGS`」「「我的」页必须内嵌 `SettingsContent()` 与无障碍入口、且跳转须经 `device/` 层」「新增页面必须在每个 locale 声明文案」升级为 CI 可执行规则。此规则源于本仓库的真实缺陷——`Routes.SETTINGS` 与 `SettingsScreen` 曾早已存在却无任何入口，用户进不去保活与磁贴设置；本次重构反过来又可能把「设置删了却忘了接回」写成新缺陷，故一并守护。


### Fixed

- **首页边到边缺陷**：`targetSdk = 35` 在 Android 15 起强制边到边，而四个页面中只有 `HomeScreen` 没有 `Scaffold`，标题会压在状态栏时钟上（已在 Android 15 / API 35 模拟器复现）。inset 改由外壳统一分发，页面只消费 `innerPadding`。
- **软键盘遮挡**：首页关键词输入框、设置页服务器地址输入框在键盘弹出后被完全遮住（实测）。两页补 `imePadding()`。
- **设置页不可达**：`Routes.SETTINGS` 与 `SettingsScreen` 早已存在却无任何入口，用户进不去保活与磁贴设置。改用底部导航栏后四个一级页面均可达。
- **统计区直显包名**：改为经 PackageManager 解析应用显示名，查不到时回退包名；同时修复长文本被硬截断（无省略号）的问题。
- **时间选择器硬编码 `OK` / `Cancel`**，未走 `strings.xml`。
- 清除 `ui/` 下 10 个源文件的 UTF-8 BOM（仓库规则要求外部工具文件 UTF-8 无 BOM）。

### Changed

- **完整视觉重设计**：新增 `ui/theme`（Material You 动态取色、完整深色色板、自定义字阶与圆角）与 `ui/components`（`SectionCard` / `PageHeader` / `StatTile` / `StatusOrb`）两层。
- 首页改为「自绘状态环 + 结论式文案 + 主行动按钮」主视觉；关键词由整行 `Text` 改为 `InputChip` 平铺，删除命中区域从数个字符扩大到 48dp。
- 四个页面统一由底部 `NavigationBar` 承载一级导航，不再各自重复 `Scaffold` + `TopAppBar`。
- 关闭 Android 10+ 三键导航栏的对比度强制，消除边到边下底部突兀白带。
- 底部导航栏**刻意不使用** `material-icons-extended`：实测令 `assembleDebug` 的 dex 合并多耗数分钟、debug APK 增大约 7 MB，对以 `assembleDebug` 为 CI 门禁的轻量工程不划算，改用 core 图标集。

## [3.1.0-rc.1] - 2026-09-28

> **预发布版本，不是正式版。** 本次触及无障碍服务、快捷磁贴与厂商跳转，发布时无可用真机，
> 厂商入口表与磁贴行为尚未在 MIUI / HarmonyOS / ColorOS / OriginOS / Android 13 上实测。
> 依据 [CONTRIBUTING.md](CONTRIBUTING.md)「发布验收」规则，先发 `rc` 供小范围验证；
> 真机验收通过后再发 `v3.1.0` 正式版。**不建议分发到应用商店或大范围推送。**

### Added (3.1.0-rc.1)

- **构建框架工程化（阶段 B）**：仓库构建体系切换为 version catalog + build-logic 约定插件——所有插件/依赖版本收口到 `gradle/libs.versions.toml` 单一事实源；公共构建配置下沉到 `client/build-logic/convention` 复合构建中的 `adskip.android.application` 约定插件（namespace/compileSdk/minSdk/targetSdk、Java 17 与 `jvmTarget`、`buildFeatures.compose`、单测返回默认值、release lint 关闭），`:app` 只保留 applicationId/版本号/签名/依赖等模块自有内容。`gradle.properties` 同步硬化（并行、构建缓存、configuration cache、`nonTransitiveRClass`、`nonFinalResClass`），wrapper 补齐 `distributionSha256Sum`（官方发行版校验和，防下载中途篡改）。CI 引入 `gradle/actions/setup-gradle` 自动缓存与 wrapper-validation，覆盖新版「第二事实源」（`.toml` 与 `build-logic/`），详见 [DESIGN-BUILD-FRAMEWORK.md](docs/planning/DESIGN-BUILD-FRAMEWORK.md) 阶段 B。
- 目录结构守护测试 `ProjectStructureTest`（随 `testDebugUnitTest` 运行，CI 独立 job `Structure Contract` 最先执行）：把「仓库长什么样」升级为门禁——根目录白名单、构建产物与临时文件不得入库（仅放行 `gradle-wrapper.jar`）、`settings.gradle.kts` 的 `include()` 与磁盘模块目录双向一致、`client/` 工程根整洁、标准 Android 源集布局、Kotlin `package` 声明与目录对位、文档必须登记到 `docs/README.md`。这直接堵住根目录再次长出 `*.apk` 与工具残留目录的路径。
- 构建框架工程化设计文档 [docs/planning/DESIGN-BUILD-FRAMEWORK.md](docs/planning/DESIGN-BUILD-FRAMEWORK.md)：version catalog、`build-logic` 约定插件、Gradle 硬化与模块拆分的完整方案，含 5 个具体坑位（included build 镜像、CI 缓存键不覆盖 `.toml`、wrapper 缺 SHA-256、`checkReleaseBuilds=false` 继承、签名路径漂移）与分步回滚策略。
- 多 Agent 协作规范（[docs/development/AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md)）：一人一 worktree 的隔离约定、文件级唯一写入者与认领板、文档单写者事实源、契约先行、最小交接信息与冲突裁决规则。
- 新增仓库卫生守护测试 `RepoHygieneTest`（随 `testDebugUnitTest` 运行），把协作约定升级为 CI 门禁：根 `.gitignore` 必须覆盖 Agent 产物/工作区目录、仓库内不得出现无 `.git` 元数据的整仓副本、协作规范必须在文档地图登记、分支名必须可追踪。
- 根 `.gitignore` 补齐 `.kilo/`、`.kilocode/`、`.worktrees/`、`.agents/`：此前相关规则只存在于本机 `.git/info/exclude`，导致 `git status` 干净但文件检索仍能命中第二份源码（已一并清理该残留副本）。
- 系统级体验：下拉通知栏快捷磁贴（`SkipTileService`）——一眼查看无障碍服务状态，一次点击即可启停（运行中点按等价于在系统设置中关闭服务）；Android 13+ 可在设置页一键请求系统添加磁贴，低版本引导手动从「编辑磁贴」拖动。
- 厂商保活引导：设置页新增「系统保活与快捷入口」，自动识别 MIUI / HarmonyOS / MagicOS / ColorOS / OriginOS / Flyme / OxygenOS / One UI 并一键跳转对应的自启动 / 后台管理页面，入口不可用时逐级降级到系统通用页面并提示手动路径——针对「后台被强杀导致跳过静默失效」这一高频问题。
- 客户端新增 `device/` 系统集成层（ROM 识别与入口表、跳转出口、快捷磁贴），设置页与首页不再自行拼装系统 `Intent`；新增 JVM 单测覆盖厂商识别、磁贴点击决策与 `AndroidManifest.xml` 契约（磁贴注册、`<queries>` 包可见性声明）。

### Changed (3.1.0-rc.1)

- **构建体系重构（阶段 B，工程基础设施、面向开发者）**：`.gradle.kts` 中不再出现任何版本号硬编码（改由 `gradle/libs.versions.toml` 承载）；`client/build-logic/` 新增为 included build 并纳入 `ProjectStructureTest` 的 `NON_MODULE_DIRS` 白名单；「运行时无任何用户可见行为变化」（依赖解析到同一版本集合，`app-debug.apk` 可对比安装）。
- 规范去重为单一事实源：`.opencode/skills/branch-guard` 不再复述分支命名表与门禁命令，改为指向 [CONTRIBUTING.md](CONTRIBUTING.md)（流程规范）、[AGENTS.md](AGENTS.md)（执行摘要）与 [docs/development/AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md)（并行协作）；[AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md) 的门禁命令也改为引用 `AGENTS.md`，PR 模板只保留勾选项加规则指针。
- 清理无人引用的重复定义：`res/values/colors.xml` 删除 4 个与 `ui/theme/Theme.kt` 品牌色板重复的色值（同时消除与 `R.string.status_on/off` 的命名撞车），`Theme.kt` 删除死变量 `TextSecondary`。
- 去除会随迭代漂移的绝对数字：README 目录说明与 CHANGELOG 不再写死单测总数（当前值以门禁输出为准），历史条目中的数字保持原样作为版本记录。
- 工作目录卫生：移除根目录遗留的构建产物 `AdSkip-latest.apk`（分发以 GitHub Releases + `SHA256SUMS` 为准，[DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md) 的校验步骤同步改为「下载到本地后校验」），并在交付说明中明确仓库根不保留任何构建产物。
- `ManifestContractTest` 新增两条守护：广播必须收窄到本应用；使用 `setPersisted()` 时必须保留 `RECEIVE_BOOT_COMPLETED` 权限（否则规则跨重启不再自动同步）。判定依据与踩坑记录见 [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) 第 2.1 节。

### Fixed (3.1.0-rc.1)

- 修正产品落地页版本信息漂移（[Issue #27](https://github.com/LiYihai1121/ui-app/issues/27)）：导航与 CTA 的下载按钮、Hero 徽章改为 `{{APP_VERSION}}` 占位符，由服务端在响应 `GET /` 时按 `server/package.json` 版本注入，发布时自动同步；同时修正安装包体积（858 KB → 1.5 MB）与路线图状态（v3.0 已完成、v3.1+ 规划中），与 [docs/planning/ROADMAP.md](docs/planning/ROADMAP.md) 对齐。
- **隐私：跳过行为可被第三方应用监听**。应用此前以未限定接收方的进程内广播上报「服务是否在运行」与「用户刚跳过了哪个应用」，任意第三方应用注册同名广播即可监听这些数据。三个广播已统一收窄到本应用，外部应用不再能观察到使用习惯。

## [3.0.4] - 2026-09-27

### Fixed (3.0.4)

- 修复发布 APK 未签名导致手机安装报「解析软件包时出现问题」：`assembleRelease` 在缺少正式签名配置时回退 debug 签名，保证产物可直接安装；需要未签名包时必须显式设置 `adskip.unsignedRelease=true`。
- 发布流水线在打包后强制 `apksigner verify`（签名证书与 `SHA256SUMS` 一并写入工作流 Summary），未签名制品直接失败，不再发布不可安装的包。

### Added (3.0.4)

- 发布流水线支持通过仓库 Secrets（`ADSKIP_KEYSTORE_BASE64` / `ADSKIP_STORE_PASSWORD` / `ADSKIP_KEY_ALIAS` / `ADSKIP_KEY_PASSWORD`）注入正式签名密钥；本仓库已启用，Release 制品使用 `CN=AdSkip Release` 签名，可直接安装并覆盖升级。
- 安装排障说明：README 与 [docs/development/DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md) 增加「解析软件包时出现问题」「应用未安装」的归因与校验命令。

### Changed (3.0.4)

- 版本号同步：Android `versionCode` 9（`versionName` 3.0）、服务端 `server/package.json` 3.0.4；无协议与数据形态变化。

## [3.0.3] - 2026-09-26

### Added (3.0.3)

- 引擎内核新增类 CSS 选择器第三通道（`engine/selector/`：AST / 解析器 / 匹配器，纯 JVM、零第三方依赖）。
- 节点通道序变为 ① 选择器 → ② 文本 → ③ ViewID；`AdNode` 增加 `parent` 与 `previousSibling()`，`RuleSet` 增加 `selectors`（`isEmpty` 计入）。
- JVM 单测 44 → 121 项；失败用例不抛异常（解析失败即丢弃该条规则，fail-safe）。
- 本版本为纯内核增量：服务端尚未下发选择器规则，用户可见行为与 3.0.2 一致；停发选择器字段即可回退 v1 行为，无数据迁移。

### Changed (3.0.3)

- 原规划的 `3.1.0`「L1 引擎 + 快照工具 + Top 30 规则」大礼包里程碑**已剥离**，改为增量发版：
  `3.0.3`（引擎内核）→ `3.1.0`（协议 v2 + 点击校验）→ `3.2.0`（快照工具）→ `3.3.0`（Top 30 规则与真机验收）→ `3.4.0` / `3.5.0` / `4.0.0`（L2 / L3 / L4）。
- 详见 [docs/planning/ROADMAP.md](docs/planning/ROADMAP.md) 与 [docs/planning/ROADMAP-ADS.md](docs/planning/ROADMAP-ADS.md)；技术方案与步骤划分见 [docs/planning/DESIGN-PHASE1-SELECTOR.md](docs/planning/DESIGN-PHASE1-SELECTOR.md)。

## [3.0.2] - 2026-09-04

### Fixed (3.0.2)

- 修正正式发布标签指向旧提交的问题，确保 Release 从合并后的 `main` 构建。

## [3.0.1] - 2026-09-04

### Fixed (3.0.1)

- 修正 Release 工作流使用 Debug APK、版本校验缺失和正式标签冲突后的发布路径。
- 发布前校验 Android 与服务端版本一致，并生成 Release APK 的 SHA-256 校验和。

## [3.0.0] - 2026-09-04

### Added (3.0.0)

- Android 客户端迁移至 Jetpack Compose + MVVM。
- 服务端迁移至 Bun + TypeScript，并按 API、中间件、存储和工具分层。
- 增加 v1 规则同步、ETag/304、批量上报、健康检查和管理日志接口。
- 增加服务端单元测试与进程内冒烟测试，以及 Android JVM 单测。

### Changed (3.0.0)

- 使用 JobScheduler 执行周期规则同步。
- 使用 SafetyGuard、载荷校验、鉴权、限流和 CORS 白名单加强安全性。

## [2.2.0] - 2026-08-24

- 完成安全加固、可测试性改造、规则缓存、合批落盘和架构增强。

## [2.1.0] - 2026-08-24

- 增加免打扰时段、定时同步、保活引导和日志导出。

## [2.0.0] - 2026-08-24

- 发布 Android 客户端、规则同步、统计上报和 Node.js 服务端基础能力。
