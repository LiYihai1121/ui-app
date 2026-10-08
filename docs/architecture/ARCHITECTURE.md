# 净启动 AdSkip 架构文档（ARCHITECTURE）

## 1. 系统全景

```text
┌─────────────────────────────────────────────────────────────────┐
│                        Android 客户端                            │
│                                                                  │
│  ui/（Jetpack Compose + MVVM）   service/        engine/         │
│  ├ MainActivity（单 Activity）   SkipAdService   SkipRuleEngine  │
│  ├ Navigation Compose           （薄编排层）───▶（纯匹配逻辑）    │
│  │  ├ HomeScreen + VM                │         ├ AdNode（接口）  │
│  │  ├ AppsScreen + VM                │         ├ SafetyGuard     │
│  │  ├ LogsScreen + VM                │         └ RuleSet         │
│  │  └ SettingsScreen + VM            │                           │
│  │  └ AppEvents（进程内状态总线）      │                          │
│  │                                    ▼                          │
│  │                              data/                            │
│  │                             ├ Prefs（SharedPreferences）      │
│  │                             ├ RulesRepository（合并/LruCache）│
│  │                             └ StatsRepository（合批落盘）     │
│  │                                                                │
│  └─── AppContainer ──▶ net/SyncClient（v1: ETag/批量）           │
│        (手动 DI)     └ sync/SyncJobService（JobScheduler）      │
└──────────────────────────────┬───────────────────────────────────┘
                               │ HTTP（v1 协议）
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                   后端服务（Bun + TypeScript，零运行时依赖）       │
│                                                                  │
│  server.ts（Bun.serve + 优雅停机 + 路由分发）                     │
│     ├─ src/api/      规则/统计/健康检查路由拆分                    │
│     │     ├ rulesApi   v0 + v1 规则下发/发布/模拟器              │
│     │     ├ statsApi   统计汇总                                   │
│     │     └ healthApi  健康检查                                   │
│     ├─ src/middleware/  鉴权 + 限流                                │
│     │     ├ auth.ts       Bearer token 鉴权                       │
│     │     └ rateLimit.ts  内存令牌桶限流                            │
│     ├─ src/utils/      HTTP 工具 + 校验                            │
│     │     ├ httpUtil.ts   CORS / 安全 JSON 解析 / 响应构建        │
│     │     └ validate.ts   载荷校验（PKG_RE/VID_RE/长度上限）       │
│     ├─ src/storage/    规则 + 统计存储                             │
│     │     └ store.ts      规则（缓存+备份轮转）/ 统计（分日分片）  │
│     └─ src/config.ts   全部可调参数（env 覆盖）                    │
│                                                                  │
│  public/index.html   产品落地页                                  │
│  public/admin.html   管理后台（登录 + diff 预览 + 规则模拟器）    │
│  data/rules.json     规则包      data/stats/  分日统计            │
│  data/backups/       规则备份轮转                                 │
└─────────────────────────────────────────────────────────────────┘
```

> 架构图源文件 [diagrams/adskip-architecture.json](../diagrams/adskip-architecture.json)，浏览器渲染版 [diagrams/adskip-architecture.html](../diagrams/adskip-architecture.html)。
> 节点匹配有**三条通道**：文本/描述关键词、控件 ViewID、选择器（`engine/selector/`，类 CSS 子集）。分层职责见第 2 节，技术方案见 [DESIGN-PHASE1-SELECTOR.md](../planning/DESIGN-PHASE1-SELECTOR.md)。

## 2. 客户端分层职责

| 层 | 模块 | 职责 | 不做的事 |
| --- | --- | --- | --- |
| **ui/** | 4 个 Composable Screen + ViewModel + `UiEffect` | 声明式 UI 与单向数据流状态管理：状态入 `UiState`（StateFlow），一次性事件经 `effects: SharedFlow<UiEffect>` 下发 | 不直接读 SharedPreferences、不碰网络、不依赖 `service/`、`net/`、`sync/`（设置读写经 `data/SettingsRepository`，边界契约见 2.1） |
| **service/** | SkipAdService + FrameworkAdNode + FloatingToggleService / PointPickService（悬浮层前台服务） | 事件接收、节流去抖、点击执行、AccessibilityNodeInfo 节点适配、悬浮快捷开关/取点模式 | 不含匹配规则逻辑、不做安全裁决、不依赖 `ui/` |
| **engine/** | SkipRuleEngine + RuleSet + AdNode + SafetyGuard + selector/ | 纯匹配：文本/ViewID/选择器 三通道（选择器为类 CSS 子集，右到左求值，DESIGN-PHASE1） | 不执行点击、不读存储、不依赖任何 Android 类型 |
| **data/** | Prefs / RulesRepository / StatsRepository / SettingsRepository / PointRules（取点规则） | 存储原语 + 领域仓库（合并/LruCache/合批落盘）+ 设置门面（收口 net/sync 委托） | 不感知 UI 与网络格式 |
| **net/** | SyncClient + RulesSignature（规则响应验签，协议字段见 API.md `X-Rules-Signature`） | HTTP 传输（v1: ETag/304/批量补报）+ 链路签名验证 | 不直接改存储键值 |
| **core/** | AppEvents / AppExecutors / Clock / LogRing / SecureStore（加密偏好）/ LanguagePreferences | 进程内事件总线、线程域收口、时钟注入、环形日志、加密偏好唯一入口 | 不含业务逻辑 |
| **device/** | VendorKeepAlive（纯数据）/ QuickTileLogic（纯逻辑）/ KeepAliveNavigator（跳转出口）/ SkipTileService（快捷磁贴）/ OverlayToggle（悬浮层门面） | 系统级入口：厂商 ROM 识别与自启动/后台管理跳转、下拉磁贴、系统设置页跳转、悬浮层启停门面 | 不读业务数据、不发起网络/同步、不依赖 `ui/`（允许依赖 `core/` 与 `service/` 的只读查询与受控动作） |
| **sync/** | SyncJobService | JobScheduler 周期同步 | 不含同步逻辑（委托 SyncClient） |

**关键设计**：
- **MVVM + StateFlow + UiEffect 单向数据流**：ViewModel 通过 `viewModelFactory { initializer { } }` 从 `AppContainer` 取依赖，可回退状态聚合为单一 `UiState` 暴露 `StateFlow`（Compose `collectAsStateWithLifecycle()` 收集）；Toast 等一次性事件统一经 `effects: SharedFlow<UiEffect>` 下发，由 Screen 在 `LaunchedEffect` 中消费，四个页面同一套模式。
- **AppEvents**：`Service` 层通过 `AppEvents.setServiceRunning()` / `AppEvents.emitSkipped()` 推送状态，ViewModel 通过 `StateFlow` / `SharedFlow` 收集，取代旧架构中 UI 直接注册 `BroadcastReceiver` 的方式；模拟测试标记 `testActive` 同样经 AppEvents 中转，ui 与 service 互不依赖。
- **RulesRepository.ruleSetFor(pkg)** 是规则的唯一组装点——全局关键词 + 应用专属关键词 + 全局 ViewID + 应用专属 ViewID + 选择器（全局/应用级，`3.0.3` 起）+ 禁用开关，合并为一个不可变的 `RuleSet` 交给引擎。选择器在**合并后才编译**（`SelectorParser.parse`），解析失败的条目在组装期丢弃，不进入 `RuleSet.selectors`。
- **选择器的两端分工**：服务端只做快检（长度 ≤256、字符白名单、括号引号配平），语法权威在客户端解析器——避免两套文法实现随发布节奏漂移。两端判定相同的向量固化在 `server/test/fixtures/selectors.contract.json`，由 `server/test/selectors.contract.test.ts` 与 `SelectorContractTest.kt` 双端消费；有意判定不同的向量记在 `divergences` 段并写明原因。
- **系统入口收口**：`device/` 是唯一允许触碰系统设置页与磁贴的层。`KeepAliveNavigator` 做「Intent 组装 + `PackageManager` 可解析性探测 + 逐级降级」，UI 只调用它、不再自行拼 `Intent`；`VendorKeepAlive`（ROM 识别 + 入口表）与 `QuickTileLogic`（磁贴点击决策）是零 Android 依赖的纯数据/纯函数，故可在 JVM 中单测，而 `TileService` 只做 API 落地。

### 2.1 边界契约

分层不只是目录约定，更是**可执行的依赖规则**。下表为各包允许/禁止的 import 关系，
由 `client/app/src/test/java/com/ldp/adskip/arch/ArchitectureBoundaryTest.kt` 在每次
`testDebugUnitTest`（及 CI 门禁）中扫描源码强制校验，违反即测试失败。

| 包 | 允许依赖 | 禁止依赖（守护测试强制） |
| --- | --- | --- |
| `engine/` | 仅 Kotlin/JDK 与 `engine/` 自身 | `android.*`、`androidx.*`、其他所有 `com.ldp.adskip.*` |
| `core/` | Kotlin/JDK/协程、`core/` 自身 | 其他所有 `com.ldp.adskip.*` |
| `ui/` | Compose/AndroidX、组合根（`AdskipApp`/`AppContainer`）、`core/`、`engine/`、`data/` 领域仓库（`RulesRepository`/`StatsRepository`/`SettingsRepository`）、`device/`（系统入口门面：`KeepAliveNavigator`/`OverlayToggle` 等）、`R` | `service/`、`net/`、`sync/`、`data.Prefs` |
| `data/` | `engine/`（RuleSet）、`core/`（横切设施：`SecureStore`/`LanguagePreferences`）、`net/`（设置门面委托）、`sync/`（调度委托）、Android SDK | `ui/`、`service/` |
| `net/` | `data/`、Android SDK、org.json | `ui/`、`service/` |
| `sync/` | `data/`、`core/`、Android SDK | `ui/`、`service/` |
| `service/` | `core/`、`data/`、`engine/`、`net/`、组合根 | `ui/` |
| `device/` | `core/`（LogRing）、`service/`（只读状态查询 `isEnabled` + 受控动作 `requestShutdown` / 悬浮服务 `start`·`stop`）、Android SDK | `ui/`、`data/`、`net/`、`sync/` |
| 组合根（`AdskipApp`/`AppContainer`） | 全部（唯一 DI 装配点） | —（不承载业务逻辑） |

规则解读：
- **engine 纯 JVM**：不触碰任何 Android 类型——引擎单测可在纯 JVM 毫秒级运行，也是未来若需拆分 `:engine` module 的前提。
- **ui 单向取值**：UI 只经 `StateFlow`/`UiEffect` 收状态与事件、经 `AppContainer` 拿仓库；不直连网络、后台调度与原始偏好。`data/SettingsRepository` 是设置页的唯一数据出口（收口 `SyncClient`/`SyncJobService`/`Prefs`）。
- **core 零业务依赖**：`AppEvents` 初值不再引用 `SkipAdService`，Service 连接时主动写入真实状态；ui/service 双向都只经 core 中转。
- **组合根兜底**：进程级初始化（如 JobScheduler 周期任务重注册）在 `AdskipApp.onCreate` 完成，不进 UI 层。
- **device 单向向下**：`device/` 只允许「读系统状态 + 拉起系统页面 + 受控启停自身服务」，因此可以依赖 `service/` 的只读查询与白名单动作（`SkipAdService.requestShutdown`、悬浮服务 `start`/`stop`），但不得持有 Service 实例状态、不得反向依赖 `ui/`；磁贴关闭服务走 `SkipAdService.requestShutdown()` 发出的进程内定向广播，而非跨包持有 Service 实例。

> 另一条由测试守护的隐式约定：保活入口表依赖 Android 11+ 的包可见性，`<queries>` 声明必须与 `device/VendorKeepAlive.kt` 的入口表逐条对齐，
> 由 `client/app/src/test/java/com/ldp/adskip/arch/ManifestContractTest.kt` 校验（漏声明只会让跳转静默失败，不会编译报错）。

> **广播必须收窄到本应用**：所有 `sendBroadcast` 都要带 `setPackage(...)`。
> `ACTION_SKIPPED` 携带用户正在使用的应用名，未收窄的隐式广播可被任意第三方应用注册同名 action 监听；
> 同样由 `ManifestContractTest` 强制。

> ⚠️ **`RECEIVE_BOOT_COMPLETED` 不是可清理的残留权限**：同步任务用 `setPersisted(true)` 跨重启恢复，
> 而 `JobInfo.Builder.setPersisted` 标注了 `@RequiresPermission(RECEIVE_BOOT_COMPLETED)`——
> 缺少该权限时 `JobScheduler.schedule()` **静默返回 0**，表现为「重启后规则不再自动同步」，无崩溃无日志。
> 「已删除 BootReceiver」指的是不再需要广播接收器，**不代表**可以删除该权限。
> 该耦合由 `ManifestContractTest` 的 `boot permission is kept while a persisted job is used` 强制。

### 2.2 目录结构契约

2.1 管的是**包之间的依赖**，2.2 管的是**仓库与工程结构本身**。后者同样由
`client/app/src/test/java/com/ldp/adskip/arch/ProjectStructureTest.kt` 强制，
随 `testDebugUnitTest` 运行，并在 CI 中作为独立 job（`Structure Contract`）最先执行——让结构问题在几分钟内失败，而不是等完整构建结束。

| 契约 | 强制内容 | 背景（实测故障） |
| --- | --- | --- |
| 根目录白名单 | 只允许 `.github/` `.kilo/` `.kilocode/` `.agents/` `.worktrees/` `.cursor/` `.vscode/` `skills/` `client/` `docs/` `server/` 与 9 个治理文件 | 根目录曾长期滞留 `AdSkip-latest.apk` 与 `.kilo/`、`.mimosa/` 工具残留目录；Cursor 会在根目录写 `.cursor/` |
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
        → SyncClient.reportSkip（v1 批量上报，带 deviceId）
        → AppEvents.emitSkipped → ViewModel 刷新统计
```

**云端规则同步（v1 协议）：**

```text
SyncJobService / 设置页触发 → SyncClient GET /api/v1/rules/latest
          → If-None-Match: <已知 hash> → 304 Not Modified
          → 或 200 + 新规则 → RulesRepository.applyCloudRules（校验 schemaVersion）
          → Prefs.setRulesHash → 记录同步时间 → ViewModel 更新 UI
```

**自动规则同步（JobScheduler）：**

```text
设置页开启 → SyncJobService.setEnabled(true)
          → JobScheduler.setPeriodic(12h) + setPersisted(true) + setRequiredNetworkType(ANY)
设备重启 → 系统自动恢复持久化 Job（无需 BootReceiver）
Doze 模式 → 系统推迟到维护窗口执行
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

## 4. 服务端分层职责

| 模块 | 职责 |
| --- | --- |
| `server.ts` | Bun.serve 入口、路由分发、优雅停机（SIGTERM/SIGINT → store.flush()） |
| `src/api/` | 路由拆分：rulesApi（v0+v1）/ statsApi / healthApi |
| `src/middleware/auth.ts` | Bearer token 鉴权（常数时间比较），保护写接口 |
| `src/middleware/rateLimit.ts` | 内存令牌桶：per-IP（读/写）+ per-deviceId（上报）+ 读体前 IP 预检 |
| `src/middleware/accessLog.ts` | 内存访问日志环形缓冲（200 条），`/api/v1/admin/logs` 暴露 |
| `src/utils/validate.ts` | 载荷校验（PKG_RE / VID_RE / 长度上限 / body 键数/深度上限） |
| `src/storage/store.ts` | 规则（缓存+备份轮转）/ 统计（分日分片+内存缓存+延迟刷盘） |
| `src/utils/httpUtil.ts` | CORS 白名单、安全 JSON 解析、响应构建工具 |
| `src/config.ts` | 全部可调参数（env 覆盖） |

**Bun 特性利用**：

- `Bun.serve()` 单进程 HTTP 服务器，自带 TLS/HTTP2 支持
- `Bun.file()` 零拷贝静态文件服务
- `bun:test` 内置测试运行器，in-process 冒烟测试（无需外部进程管理）
- `server.requestIP(req)` 获取客户端真实 IP（支持反向代理）

## 5. 安全模型

三层安全防护，从信任服务器改为本地最小权限：

| 层 | 机制 | 说明 |
| --- | --- | --- |
| **服务端鉴权** | Bearer token | `ADMIN_TOKEN` 环境变量；未配置时写接口返回 503；规则发布/模拟器需鉴权 |
| **服务端校验** | validate.ts | 包名正则 `^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$`、关键词 ≤12 字、总条目 ≤2000、body 键数/深度上限；选择器 ≤256 字符 + 字符白名单（放行 Unicode 字母数字，否则中文关键词场景整条被丢）+ 括号引号配平 |
| **请求约束** | readBody + maxRequestBodySize | body 必须 application/json（415）；>1MiB 协议层拒绝（413/断连），不进应用内存 |
| **客户端护栏** | SafetyGuard | 硬编码黑名单（支付/付款/确认/同意/购买/下单/授权/登录/免密/开通/安装/下载），云规则不可覆盖 |

## 6. 线程模型

| 操作 | 线程 | 说明 |
| --- | --- | --- |
| 无障碍事件处理 | 主线程 | 节流去抖纯内存操作 |
| 规则匹配 | 主线程 | 引擎纯 CPU 计算 |
| 统计计数 | 主线程（内存）→ IO 线程（落盘） | 内存先记，5s 后 AppExecutors.io 合批写 SP |
| 规则同步 | IO 线程 | SyncClient 在 AppExecutors.io 执行网络请求 |
| Compose UI | 主线程 | StateFlow 收集 + UI 渲染，协程自动调度 |

## 7. 协议版本与兼容策略

| 版本 | 路由 | 说明 |
| --- | --- | --- |
| v0（兼容期） | `/api/rules/latest`, `/api/rules`, `/api/skip`, `/api/stats/summary` | 旧客户端无感 |
| v1（当前） | `/api/v1/rules/latest` (ETag/304), `/api/v1/rules`, `/api/v1/reports/batch`, `/api/v1/rules/test`, `/api/v1/stats/summary`, `/api/v1/health` | 新增 schemaVersion/hash/ETag/批量上报 |
| v2（规划，随 `3.1.0`） | 复用 v1 路由，仅扩展载荷 | `schemaVersion` 升 2：新增 `selectors` 字段（全局 + 应用级）；`MIN_SCHEMA_VERSION` 保持 1，旧客户端忽略未知字段、行为不变。方案见 [DESIGN-PHASE1-SELECTOR.md](../planning/DESIGN-PHASE1-SELECTOR.md) 步骤 C |

## 8. 设计决策

- **客户端 Compose + MVVM**：声明式 UI、单 Activity + Navigation Compose、Material3、StateFlow 驱动，符合 Google 推荐的现代 Android 架构。
- **UiEffect 单向数据流**：状态与副作用分离——可回退状态入 `UiState`，一次性提示统一走 `UiEffect.ShowMessage`，杜绝各页自定义消息类型（原 `HomeMessage`/裸 `String` 两种并存已收敛）。
- **架构边界守护**：层间依赖规则写入 2.1 节并由 `ArchitectureBoundaryTest` 源码级强制校验，边界违规在 CI 即失败，而非代码评审时才发现。
- **服务端 Bun + TypeScript**：零运行时依赖、类型安全、`bun:test` 内置测试、`Bun.serve` 高性能 HTTP、`Bun.file` 零拷贝静态服务。
- **JSON 文件存储**：单进程本地服务，数据量小；tmp+rename 原子写避免损坏。统计按天分片，14 天趋势 = 读 14 个小文件。种子规则固化在 `seed/rules.json`（入库），`data/` 仅存运行时数据且不入库。
- **离线优先**：客户端一切功能本地可用；服务端不可达时上报静默失败。
- **手动 DI**：AppContainer 收口所有依赖，不引入 Hilt/Koin 等第三方 DI 框架。
- **可测性**：AdNode 抽象使引擎可跑纯 JVM 单测；服务端 startServer API 支持注入端口/数据目录/令牌。

## 9. 扩展点

| 需求 | 改动位置 |
| --- | --- |
| 新匹配通道（坐标/图像规则） | `engine/RuleSet` 加字段 + `SkipRuleEngine.matches` 加分支 |
| 新增 UI 页面 | `ui/` 加 Composable Screen + ViewModel + NavHost 路由 |
| 服务端换数据库 | 只改 `server/src/storage/store.ts` |
| 新增 API | `src/api/` 加路由文件 + `api/index.ts` 加分发 |
| 客户端换网络库 | 只改 `net/SyncClient` 内部实现 |
| 新增安全黑名单词 | `engine/SafetyGuard.DENY_WORDS` |

> 选择器通道（`3.0.3` 内核）即按上表第一行「新匹配通道」的路径落地：`RuleSet` 增 `selectors` 字段 + `SkipRuleEngine` 增通道分支 + 新增 `engine/selector/` 子包，未改动既有两通道语义。

## 10. 测试

**客户端：**

- JVM 单测：`cd client && ./gradlew testDebugUnitTest`（引擎匹配 + SafetyGuard 护栏 + 架构边界守护 `ArchitectureBoundaryTest` + 清单契约守护 `ManifestContractTest` + 仓库卫生守护 `RepoHygieneTest` + 厂商识别/磁贴决策 `VendorKeepAliveTest`/`QuickTileLogicTest` + 双端选择器契约 `SelectorContractTest`）
- 构建验证：`cd client && ./gradlew assembleDebug`
- 格式与静态检查：`cd client && ./gradlew ktlintCheck`（规则集与行宽的唯一事实源是仓库根 `.editorconfig`；插件应用与版本锁定分别在约定插件与 `client/build.gradle.kts`，版本号一律来自 version catalog）

**服务端（bun:test）：**

- 全部测试：`cd server && bun test`
- 单元测试：覆盖 validate、auth、rateLimit 和 CORS 等核心约束
- 冒烟测试：in-process 启动服务器，覆盖全部 v0+v1 路由、鉴权和校验

## 11. 项目结构

```text
AdSkip/                            全栈 monorepo
├── client/                        Android 客户端（Kotlin + Compose，Gradle 工程根）
│   ├── build.gradle.kts           模块与签名配置（签名参数读 local.properties）
│   ├── settings.gradle.kts        仓库配置（国内镜像优先）
│   └── app/src/main/java/com/ldp/adskip/
│       ├── ui/                   Compose UI（单 Activity + 4 Screen + ViewModel + UiEffect）
│       ├── core/                 AppEvents / Clock / AppExecutors / LogRing / SecureStore / LanguagePreferences
│       ├── service/              SkipAdService + FrameworkAdNode（无障碍服务/节点适配）
│       │                       / FloatingToggleService（悬浮快捷开关）/ PointPickService（取点模式）
│   │       ├── device/               系统级入口：VendorKeepAlive（ROM 识别 + 入口表）
│   │       │                         / QuickTileLogic（磁贴决策）/ KeepAliveNavigator（跳转出口）
│   │       │                         / SkipTileService（下拉磁贴）/ OverlayToggle（悬浮层门面）
│       ├── engine/               规则引擎（纯 JVM 可测：AdNode / RuleSet / SkipRuleEngine / SafetyGuard）
│       │   └── selector/         选择器引擎（SelectorAst / SelectorParser / SelectorMatcher）
│       ├── data/                 Prefs / RulesRepository / StatsRepository / SettingsRepository / PointRules
│       ├── net/                  SyncClient / RulesSignature（规则响应验签）
│       └── sync/                 SyncJobService
├── server/                       Bun + TypeScript 后端
│   ├── scripts/                  密钥生成（gen-totp.ts / gen-rules-keys.ts，密钥不入库）
│   ├── src/
│   │   ├── api/                  路由拆分（rulesApi / statsApi / healthApi·health+metrics）
│   │   ├── middleware/           鉴权 + 限流 + 访问日志（auth / rateLimit / accessLog）
│   │   ├── utils/                HTTP 工具 + 校验 + 日志 + TOTP + 规则签名（httpUtil.ts / validate.ts / logger.ts / totp.ts / rulesSigner.ts）
│   │   ├── storage/              规则 + 统计存储（store.ts）
│   │   ├── types/                域模型类型（rules.ts）
│   │   └── config.ts             全部可调参数
│   ├── test/                     bun:test 单元 + 冒烟
│   ├── server.ts                 入口（Bun.serve）
│   └── public/                   落地页 + 管理后台
├── docs/                         README.md（文档地图）+ api/ architecture/ development/ planning/ diagrams/
└── .github/workflows/ci.yml     CI：Android Build + Bun Server Tests
```
