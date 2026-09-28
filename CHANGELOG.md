# 变更日志

本文件记录面向用户和运维的版本变更。版本号遵循 Semantic Versioning，正式版本标签以 Git 中的 `vX.Y.Z` 为准。

版本与提交的完整对应关系见 [发布历史与提交链路](docs/planning/RELEASE-HISTORY.md)。

## 目录

- [Unreleased](#unreleased)
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

### Added

- **「我的」用户页**：底部导航新增第五个一级入口（本页），展示本机使用概览（累计跳过 / 已跳过应用数 / 服务状态）与应用、设备信息（应用版本、系统版本、设备型号、ROM 厂商），并明示「跳过记录与统计均只保存在本机」。实现遵循既有单向下行数据流：`ui/profile/ProfileScreen` + `ProfileViewModel`（StateFlow 驱动），统计经 `StatsRepository`、版本经 `SettingsRepository` 读取，UI 不自行查 `PackageManager`（遵守 ARCHITECTURE.md 2.1 边界）。
- **导航契约守护测试**：`ProjectStructureTest` 新增三项断言，把「路由声明 ↔ 底部导航 ↔ NavHost」三方对齐与「新增页面必须在每个 locale 声明文案」升级为 CI 可执行规则。此规则源于本仓库的真实缺陷——`Routes.SETTINGS` 与 `SettingsScreen` 曾早已存在却无任何入口，用户进不去保活与磁贴设置。

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
