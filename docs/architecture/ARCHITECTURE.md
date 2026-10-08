# 净启动 AdSkip 架构文档（ARCHITECTURE）

## 1. 系统全景

```text
┌─────────────────────────────────────────────────────────┐
│              Android 客户端（纯本地架构）                  │
│                                                          │
│  ui/（Jetpack Compose + MVVM）   service/        engine/ │
│  ├ MainActivity（单 Activity）   SkipAdService   SkipRuleEngine │
│  ├ Navigation Compose           （薄编排层）───▶（纯匹配逻辑） │
│  │  ├ HomeScreen + VM                │         ├ AdNode（接口） │
│  │  ├ AppsScreen + VM                │         ├ SafetyGuard   │
│  │  ├ LogsScreen + VM                │         └ RuleSet       │
│  │  └ SettingsScreen + VM            │                         │
│  │  └ AppEvents（进程内状态总线）      │                        │
│  │                                    ▼                        │
│  │                              data/                        │
│  │                             ├ Prefs（EncryptedSharedPreferences）│
│  │                             ├ RulesRepository（合并/LruCache）│
│  │                             ├ StatsRepository（合批落盘）   │
│  │                             └ SettingsRepository（设置门面）│
│  │                                                          │
│  └ AppContainer（手动 DI，统一装配所有依赖）              │
└─────────────────────────────────────────────────────────┘
```

> 架构图源文件 [diagrams/adskip-architecture.json](../diagrams/adskip-architecture.json)，浏览器渲染版 [diagrams/adskip-architecture.html](../diagrams/adskip-architecture.html)。
> 节点匹配有**三条通道**：文本/描述关键词、控件 ViewID、选择器（`engine/selector/`，类 CSS 子集）。分层职责见第 2 节，技术方案见 [DESIGN-PHASE1-SELECTOR.md](../planning/DESIGN-PHASE1-SELECTOR.md)。

## 2. 客户端分层职责

| 层 | 模块 | 职责 | 不做的事 |
| --- | --- | --- | --- |
| **ui/** | 4 个 Composable Screen + ViewModel + `UiEffect` | 声明式 UI 与单向数据流状态管理：状态入 `UiState`（StateFlow），一次性事件经 `effects: SharedFlow<UiEffect>` 下发 | 不直接读 SharedPreferences、不依赖 `service/`（设置读写经 `data/SettingsRepository`，边界契约见 2.1） |
| **service/** | SkipAdService + FrameworkAdNode | 事件接收、节流去抖、点击执行、AccessibilityNodeInfo 节点适配 | 不含匹配规则逻辑、不做安全裁决、不依赖 `ui/` |
| **engine/** | SkipRuleEngine + RuleSet + AdNode + SafetyGuard + selector/ | 纯匹配：文本/ViewID/选择器 三通道（选择器为类 CSS 子集，右到左求值，DESIGN-PHASE1） | 不执行点击、不读存储、不依赖任何 Android 类型 |
| **data/** | Prefs / RulesRepository / StatsRepository / SettingsRepository | 存储原语 + 领域仓库（合并/LruCache/合批落盘）+ 设置门面（免打扰/语言/版本） | 不感知 UI |
| **core/** | AppEvents / AppExecutors / Clock / LogRing | 进程内事件总线、线程域收口、时钟注入、环形日志 | 不含业务逻辑 |
| **device/** | VendorKeepAlive（纯数据）/ QuickTileLogic（纯逻辑）/ KeepAliveNavigator（跳转出口）/ SkipTileService（快捷磁贴） | 系统级入口：厂商 ROM 识别与自启动/后台管理跳转、下拉磁贴、系统设置页跳转 | 不读业务数据、不依赖 `ui/`（允许依赖 `core/` 与 `service/` 的只读状态查询） |

**关键设计**：
- **MVVM + StateFlow + UiEffect 单向数据流**：ViewModel 通过 `viewModelFactory { initializer { } }` 从 `AppContainer` 取依赖，可回退状态聚合为单一 `UiState` 暴露 `StateFlow`（Compose `collectAsStateWithLifecycle()` 收集）；Toast 等一次性事件统一经 `effects: SharedFlow<UiEffect>` 下发，由 Screen 在 `LaunchedEffect` 中消费，四个页面同一套模式。
- **AppEvents**：`Service` 层通过 `AppEvents.setServiceRunning()` / `AppEvents.emitSkipped()` 推送状态，ViewModel 通过 `StateFlow` / `SharedFlow` 收集，取代旧架构中 UI 直接注册 `BroadcastReceiver` 的方式；模拟测试标记 `testActive` 同样经 AppEvents 中转，ui 与 service 互不依赖。
- **RulesRepository.ruleSetFor(pkg)** 是规则的唯一组装点——全局关键词 + 应用专属关键词 + 全局 ViewID + 应用专属 ViewID + 选择器（全局/应用级，`3.0.3` 起）+ 禁用开关，合并为一个不可变的 `RuleSet` 交给引擎。选择器在**合并后才编译**（`SelectorParser.parse`），解析失败的条目在组装期丢弃，不进入 `RuleSet.selectors`。
- **选择器语法的单端契约**：服务端移除后语法权威完全在客户端解析器，向量夹具迁入 `client/app/src/test/resources/selectors.contract.json`，由 `SelectorContractTest.kt` 单独消费——解析器收紧或文法变更时若未同步夹具，第三通道会「配了没生效」而不报任何错。夹具三段：`accepted` / `rejected` 锁定解析成败，`divergences` 记录客户端独有的拒绝理由，且每条强制写明 `why`，否则后人无从判断该不该改。
- **系统入口收口**：`device/` 是唯一允许触碰系统设置页与磁贴的层。`KeepAliveNavigator` 做「Intent 组装 + `PackageManager` 可解析性探测 + 逐级降级」，UI 只调用它、不再自行拼 `Intent`；`VendorKeepAlive`（ROM 识别 + 入口表）与 `QuickTileLogic`（磁贴点击决策）是零 Android 依赖的纯数据/纯函数，故可在 JVM 中单测，而 `TileService` 只做 API 落地。

### 2.1 边界契约

分层不只是目录约定，更是**可执行的依赖规则**。下表为各包允许/禁止的 import 关系，
由 `client/app/src/test/java/com/ldp/adskip/arch/ArchitectureBoundaryTest.kt` 在每次
`testDebugUnitTest`（及 CI 门禁）中扫描源码强制校验，违反即测试失败。

| 包 | 允许依赖 | 禁止依赖（守护测试强制） |
| --- | --- | --- |
| `engine/` | 仅 Kotlin/JDK 与 `engine/` 自身 | `android.*`、`androidx.*`、其他所有 `com.ldp.adskip.*` |
| `core/` | Kotlin/JDK/协程、`core/` 自身 | 其他所有 `com.ldp.adskip.*` |
| `ui/` | Compose/AndroidX、组合根（`AdskipApp`/`AppContainer`）、`core/`、`engine/`、`data/` 领域仓库（`RulesRepository`/`StatsRepository`/`SettingsRepository`）、`R` | `service/`、`data.Prefs` |
| `data/` | `engine/`（RuleSet）、Android SDK | `ui/`、`service/` |
| `service/` | `core/`、`data/`、`engine/`、组合根 | `ui/` |
| `device/` | `core/`（LogRing）、`service/`（无障碍真实状态 `isEnabled` 与关闭请求 `requestShutdown`）、Android SDK | `ui/`、`data/` |
| 组合根（`AdskipApp`/`AppContainer`） | 全部（唯一 DI 装配点） | —（不承载业务逻辑） |

规则解读：
- **engine 纯 JVM**：不触碰任何 Android 类型——引擎单测可在纯 JVM 毫秒级运行，也是未来若需拆分 `:engine` module 的前提。
- **ui 单向取值**：UI 只经 `StateFlow`/`UiEffect` 收状态与事件、经 `AppContainer` 拿仓库；不直连网络、后台调度与原始偏好。
- **core 零业务依赖**：`AppEvents` 初值不再引用 `SkipAdService`，Service 连接时主动写入真实状态；ui/service 双向都只经 core 中转。
- **组合根兜底**：进程级初始化（如 JobScheduler 周期任务重注册）在 `AdskipApp.onCreate` 完成，不进 UI 层。
- **device 单向向下**：`device/` 只允许「读系统状态 + 拉起系统页面」，因此可以依赖 `service/` 的只读查询，但不得反向依赖 `ui/`；磁贴关闭服务走 `SkipAdService.requestShutdown()` 发出的进程内定向广播，而非跨包持有 Service 实例。

> 另一条由测试守护的隐式约定：保活入口表依赖 Android 11+ 的包可见性，`<queries>` 声明必须与 `device/VendorKeepAlive.kt` 的入口表逐条对齐，
> 由 `client/app/src/test/java/com/ldp/adskip/arch/ManifestContractTest.kt` 校验（漏声明只会让跳转静默失败，不会编译报错）。

> **广播必须收窄到本应用**：所有 `sendBroadcast` 都要带 `setPackage(...)`。
> `ACTION_SKIPPED` 携带用户正在使用的应用名，未收窄的隐式广播可被任意第三方应用注册同名 action 监听；
> 同样由 `ManifestContractTest` 强制。

### 2.2 目录结构契约

2.1 管的是**包之间的依赖**，2.2 管的是**仓库与工程结构本身**。后者同样由
`client/app/src/test/java/com/ldp/adskip/arch/ProjectStructureTest.kt` 强制，
随 `testDebugUnitTest` 运行，并在 CI 中作为独立 job（`Structure Contract`）最先执行——让结构问题在几分钟内失败，而不是等完整构建结束。

| 契约 | 强制内容 | 背景（实测故障） |
| --- | --- | --- |
| 根目录白名单 | 只允许 `.github/` `.kilo/` `.kilocode/` `.agents/` `.worktrees/` `.cursor/` `.vscode/` `skills/` `client/` `docs/` 与 9 个治理文件 | 根目录曾长期滞留 `AdSkip-latest.apk` 与 `.kilo/`、`.mimosa/` 工具残留目录；Cursor 会在根目录写 `.cursor/` |
| `.vscode/` 内容 | **选择性入库**：只允许 `settings.json` 与 `extensions.json`，`launch.json` 等个人调试状态一律不入库 | 共享的编辑器行为若只存在于各自本机，任何一条都可能在某台机器上悄悄失效；而带本机路径与断点的个人状态入库即噪声 |
| 产物不入库 | 禁止 `*.apk/*.aab/*.aar/*.log/*.zip/*.keystore/*.iml`、`.DS_Store` 等（仅放行 `gradle-wrapper.jar`） | 分发以 Releases + `SHA256SUMS` 为准；构建产物属于被忽略目录 |
| 模块双向一致 | `settings.gradle.kts` 的 `include(":x")` ↔ 磁盘模块目录**双向**校验 | 模块目录被删却仍注册，或建了目录忘注册（代码写了但不编译） |
| Gradle 工程根 | `client/` 只保留 wrapper、构建脚本、`gradle/libs.versions.toml`、`build-logic/` 与模块目录；本机 `.vscode/` / `.idea/` 允许存在但不入库 | 防止脚本/产物随手落进工程根；以 `client/` 为工程根打开时语言服务会写本机配置 |
| 标准源集布局 | `src/main/{java,res}` + `AndroidManifest.xml`（应用模块）、`src/test/java`；非常规源集须在 `build.gradle.kts` 声明 | 非常规源集目录默认不参与编译，属静默失效 |
| 约定插件复合构建 | `client/build-logic/` 是 included build（托管 `adskip.android.application` 约定插件），不是主构建模块，列入 `NON_MODULE_DIRS` 白名单 | 约定插件若被误当模块注册，会出现「include 但无 src」或重复配置的双重真相 |
| 包声明对位 | 每个 `.kt` 的 `package` 必须与其目录路径一致 | 包路径错位会让 2.1 的包级扫描按错误边界生效 |
| 文档登记 | `docs/` 子目录限 `api/ architecture/ development/ planning/ diagrams/`，且每份文档必须出现在 `docs/README.md` | 与文档地图的单一事实源要求对齐 |

> **白名单是刻意收紧的**：确需新增根级条目时，先更新 `ProjectStructureTest` 的
> `ALLOWED_ROOT_DIRS` / `ALLOWED_ROOT_FILES` 并在 PR 说明理由——让结构调整成为一次显式决策，
> 而不是一次顺手拖拽。若条目属于本机生成物，正确做法是写入根 `.gitignore` 而非加白名单。

> version catalog 与 build-logic 约定插件（阶段 B）已完成，模块拆分（阶段 C）待排期；
> 迁移细节、具体坑位与回滚策略见
> [DESIGN-BUILD-FRAMEWORK.md](../planning/DESIGN-BUILD-FRAMEWORK.md)。

## 3. 数据流

**跳过一次广告：**
```text
窗口事件 → SkipAdService（150ms 节流 / 1.2s 去抖，Clock 注入）
        → RulesRepository.ruleSetFor(pkg) 取规则（LruCache 命中）
        → SkipRuleEngine.findTarget(root: AdNode, ruleSet) 找目标
        → SafetyGuard.canClick(target, pkg) 安全护栏复核
        → clickNode：ACTION_CLICK，兜底坐标手势
        → StatsRepository.recordSkip（内存计数 → 5s 合批落盘）
        → AppEvents.emitSkipped → ViewModel 刷新统计
```

**系统级入口（快捷磁贴 / 厂商保活引导）：**

```text
下拉磁贴点击 → QuickTileLogic.decide(SkipAdService.isEnabled(ctx))   // 纯函数决策
            ├─ 运行中 → SkipAdService.requestShutdown(ctx) → 进程内定向广播
            │        → SkipAdService.disableSelf() → onDestroy（刷盘 + 状态广播）
            │        → 磁贴乐观置为「已停止」并在 600ms 后复查系统真实状态
            └─ 未运行 → KeepAliveNavigator.openAccessibilitySettings() → 系统无障碍设置

设置页「打开自启动/后台管理」→ KeepAliveNavigator.openKeepAliveSettings(ctx)
          → VendorKeepAlive.detect(Build.MANUFACTURER, Build.BRAND)   // 纯数据识别
          → candidates(vendor)：厂商入口 → 通用兜底（应用详情 / 无障碍 / 电池优化）
          → 逐条 PackageManager.resolveActivity 复核 → 首个可解析者 startActivity
          → 全部不可解析 → UiEffect 提示手动路径（不静默失败）
```

## 4. 安全模型

纯本地架构下的安全防护，仅在客户端层面实施：

| 层 | 机制 | 说明 |
| --- | --- | --- |
| **存储加密** | EncryptedSharedPreferences | `Prefs.kt` 使用 `MasterKey` + AES-256-GCM 加密；规则、统计、设置均经加密存储 |
| **安全护栏** | SafetyGuard | 硬编码黑名单（支付/付款/确认/同意/购买/下单/授权/登录/免密/开通/安装/下载），不可被规则覆盖 |
| **广播收窄** | setPackage | 所有 `sendBroadcast` 都带 `setPackage(...)`，防止第三方注册同名 action 监听 |
| **网络隔离** | 无网络权限 | `AndroidManifest` 移除 `INTERNET` 权限，客户端不发起任何网络请求 |

## 5. 线程模型

| 操作 | 线程 | 说明 |
| --- | --- | --- |
| 无障碍事件处理 | 主线程 | 节流去抖纯内存操作 |
| 规则匹配 | 主线程 | 引擎纯 CPU 计算 |
| 统计计数 | 主线程（内存）→ IO 线程（落盘） | 内存先记，5s 后 AppExecutors.io 合批写 SP |
| Compose UI | 主线程 | StateFlow 收集 + UI 渲，协程自动调度 |

## 6. 设计决策

- **客户端 Compose + MVVM**：声明式 UI、单 Activity + Navigation Compose、Material3、StateFlow 驱动，符合 Google 推荐的现代 Android 架构。
- **UiEffect 单向数据流**：状态与副作用分离——可回退状态入 `UiState`，一次性提示统一走 `UiEffect.ShowMessage`，杜绝各页自定义消息类型（原 `HomeMessage`/裸 `String` 两种并存已收敛）。
- **架构边界守护**：层间依赖规则写入 2.1 节并由 `ArchitectureBoundaryTest` 源码级强制校验，边界违规在 CI 即失败，而非代码评审时才发现。
- **纯本地架构**：一切功能本地可用，无网络访问、无后台同步、无上报；规则仅由本机编辑产生。
- **手动 DI**：AppContainer 收口所有依赖，不引入 Hilt/Koin 等第三方 DI 框架。
- **可测性**：AdNode 抽象使引擎可跑纯 JVM 单测。

## 7. 扩展点

| 需求 | 改动位置 |
| --- | --- |
| 新匹配通道（坐标/图像规则） | `engine/RuleSet` 加字段 + `SkipRuleEngine.matches` 加分支 |
| 新增 UI 页面 | `ui/` 加 Composable Screen + ViewModel + NavHost 路由 |
| 新增安全黑名单词 | `engine/SafetyGuard.DENY_WORDS` |

> 选择器通道（`3.0.3` 内核）即按上表第一行「新匹配通道」的路径落地：`RuleSet` 增 `selectors` 字段 + `SkipRuleEngine` 增通道分支 + 新增 `engine/selector/` 子包，未改动既有两通道语义。

## 8. 测试

- JVM 单测：`cd client && ./gradlew testDebugUnitTest`（引擎匹配 + SafetyGuard 护栏 + 架构边界守护 `ArchitectureBoundaryTest` + 清单契约守护 `ManifestContractTest` + 仓库卫生守护 `RepoHygieneTest` + 厂商识别/磁贴决策 `VendorKeepAliveTest`/`QuickTileLogicTest` + 选择器语法契约 `SelectorContractTest`）
- 构建验证：`cd client && ./gradlew assembleDebug`
- 格式与静态检查：`cd client && ./gradlew ktlintCheck`（规则集与行宽的唯一事实源是仓库根 `.editorconfig`；插件应用与版本锁定分别在约定插件与 `client/build.gradle.kts`，版本号一律来自 version catalog）

## 9. 项目结构

```text
AdSkip/                            Android-only monorepo（纯本地）
├── client/                        Android 客户端（Kotlin + Compose，Gradle 工程根）
│   ├── build.gradle.kts           模块与签名配置（签名参数读 local.properties）
│   ├── settings.gradle.kts        仓库配置（国内镜像优先）
│   └── app/src/main/java/com/ldp/adskip/
│       ├── ui/                   Compose UI（单 Activity + 4 Screen + ViewModel + UiEffect）
│       ├── core/                 AppEvents / Clock / AppExecutors / LogRing
│       ├── service/              SkipAdService + FrameworkAdNode（无障碍服务/节点适配）
│       │   ├── device/               系统级入口：VendorKeepAlive（ROM 识别 + 入口表）
│       │       │                     QuickTileLogic（磁贴决策）/ KeepAliveNavigator（跳转出口）
│       │       │                     SkipTileService（下拉磁贴）
│       ├── engine/               规则引擎（纯 JVM 可测：AdNode / RuleSet / SkipRuleEngine / SafetyGuard）
│       │   └── selector/         选择器引擎（SelectorAst / SelectorParser / SelectorMatcher）
│       └── data/                 Prefs / RulesRepository / StatsRepository / SettingsRepository
├── docs/                         README.md（文档地图）+ api/ architecture/ development/ planning/ diagrams/
└── .github/workflows/ci.yml     CI：Android Build + Contract Tests
```
