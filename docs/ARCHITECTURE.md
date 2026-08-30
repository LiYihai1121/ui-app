# 净启动 AdSkip 架构文档

## 1. 系统全景

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android 客户端                            │
│                                                                  │
│  ui/（Jetpack Compose + MVVM）   service/        engine/         │
│  ├ MainActivity（单 Activity）   SkipAdService   SkipRuleEngine  │
│  ├ Navigation Compose           （薄编排层）───▶（纯匹配逻辑）    │
│  │  ├ HomeScreen + VM                │         ├ AdNode（接口）  │
│  │  ├ AppsScreen + VM                │         ├ FrameworkAdNode │
│  │  ├ LogsScreen + VM                │         ├ SafetyGuard     │
│  │  └ SettingsScreen + VM            │         └ RuleSet         │
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

## 2. 客户端分层职责

| 层 | 模块 | 职责 | 不做的事 |
|---|---|---|---|
| **ui/** | 4 个 Composable Screen + ViewModel | 声明式 UI 与状态管理，通过 StateFlow 驱动 UI | 不直接读 SharedPreferences、不碰网络 |
| **service/** | SkipAdService | 事件接收、节流去抖、点击执行 | 不含匹配规则逻辑、不做安全裁决 |
| **engine/** | SkipRuleEngine + RuleSet + AdNode + SafetyGuard | 纯匹配：文本/ViewID 双通道 | 不执行点击、不读存储 |
| **data/** | Prefs / RulesRepository / StatsRepository | 存储原语 + 领域仓库（合并/LruCache/合批落盘） | 不感知 UI 与网络格式 |
| **net/** | SyncClient | HTTP 传输（v1: ETag/304/批量补报） | 不直接改存储键值 |
| **core/** | AppEvents / AppExecutors / Clock / LogRing | 进程内事件总线、线程域收口、时钟注入、环形日志 | 不含业务逻辑 |
| **sync/** | SyncJobService | JobScheduler 周期同步 | 不含同步逻辑（委托 SyncClient） |

**关键设计**：
- **MVVM + StateFlow**：ViewModel 通过 `viewModelFactory { initializer { } }` 从 `AppContainer` 取依赖，暴露 `StateFlow` 给 Compose `collectAsStateWithLifecycle()`。
- **AppEvents**：`Service` 层通过 `AppEvents.setServiceRunning()` / `AppEvents.emitSkipped()` 推送状态，ViewModel 通过 `StateFlow` / `SharedFlow` 收集，取代旧架构中 UI 直接注册 `BroadcastReceiver` 的方式。
- **RulesRepository.ruleSetFor(pkg)** 是规则的唯一组装点——全局关键词 + 应用专属关键词 + 全局 ViewID + 应用专属 ViewID + 禁用开关，合并为一个不可变的 `RuleSet` 交给引擎。

## 3. 数据流

**跳过一次广告：**
```
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
```
SyncJobService / 设置页触发 → SyncClient GET /api/v1/rules/latest
          → If-None-Match: <已知 hash> → 304 Not Modified
          → 或 200 + 新规则 → RulesRepository.applyCloudRules（校验 schemaVersion）
          → Prefs.setRulesHash → 记录同步时间 → ViewModel 更新 UI
```

**自动规则同步（JobScheduler）：**
```
设置页开启 → SyncJobService.setEnabled(true)
          → JobScheduler.setPeriodic(12h) + setPersisted(true) + setRequiredNetworkType(ANY)
设备重启 → 系统自动恢复持久化 Job（无需 BootReceiver）
Doze 模式 → 系统推迟到维护窗口执行
```

## 4. 服务端分层职责

| 模块 | 职责 |
|---|---|
| `server.ts` | Bun.serve 入口、路由分发、优雅停机（SIGTERM/SIGINT → store.flush()） |
| `src/api/` | 路由拆分：rulesApi（v0+v1）/ statsApi / healthApi |
| `src/middleware/auth.ts` | Bearer token 鉴权，保护写接口 |
| `src/middleware/rateLimit.ts` | 内存令牌桶：per-IP（读/写）+ per-deviceId（上报） |
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
|---|---|---|
| **服务端鉴权** | Bearer token | `ADMIN_TOKEN` 环境变量；未配置时写接口返回 503；规则发布/模拟器需鉴权 |
| **服务端校验** | validate.ts | 包名正则 `^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$`、关键词 ≤12 字、总条目 ≤2000、body 键数/深度上限 |
| **客户端护栏** | SafetyGuard | 硬编码黑名单（支付/付款/确认/同意/购买/下单/授权/登录/免密/开通/安装/下载），云规则不可覆盖 |

## 6. 线程模型

| 操作 | 线程 | 说明 |
|---|---|---|
| 无障碍事件处理 | 主线程 | 节流去抖纯内存操作 |
| 规则匹配 | 主线程 | 引擎纯 CPU 计算 |
| 统计计数 | 主线程（内存）→ IO 线程（落盘） | 内存先记，5s 后 AppExecutors.io 合批写 SP |
| 规则同步 | IO 线程 | SyncClient 在 AppExecutors.io 执行网络请求 |
| Compose UI | 主线程 | StateFlow 收集 + UI 渲染，协程自动调度 |

## 7. 协议版本与兼容策略

| 版本 | 路由 | 说明 |
|---|---|---|
| v0（兼容期） | `/api/rules/latest`, `/api/rules`, `/api/skip`, `/api/stats/summary` | 旧客户端无感 |
| v1（当前） | `/api/v1/rules/latest` (ETag/304), `/api/v1/rules`, `/api/v1/reports/batch`, `/api/v1/rules/test`, `/api/v1/stats/summary`, `/api/v1/health` | 新增 schemaVersion/hash/ETag/批量上报 |

## 8. 设计决策

- **客户端 Compose + MVVM**：声明式 UI、单 Activity + Navigation Compose、Material3、StateFlow 驱动，符合 Google 推荐的现代 Android 架构。
- **服务端 Bun + TypeScript**：零运行时依赖、类型安全、`bun:test` 内置测试、`Bun.serve` 高性能 HTTP、`Bun.file` 零拷贝静态服务。
- **JSON 文件存储**：单进程本地服务，数据量小；tmp+rename 原子写避免损坏。统计按天分片，14 天趋势 = 读 14 个小文件。
- **离线优先**：客户端一切功能本地可用；服务端不可达时上报静默失败。
- **手动 DI**：AppContainer 收口所有依赖，不引入 Hilt/Koin 等第三方 DI 框架。
- **可测性**：AdNode 抽象使引擎可跑纯 JVM 单测；服务端 startServer API 支持注入端口/数据目录/令牌。

## 9. 扩展点

| 需求 | 改动位置 |
|---|---|
| 新匹配通道（坐标/图像规则） | `engine/RuleSet` 加字段 + `SkipRuleEngine.matches` 加分支 |
| 新增 UI 页面 | `ui/` 加 Composable Screen + ViewModel + NavHost 路由 |
| 服务端换数据库 | 只改 `src/store.ts` |
| 新增 API | `src/api/` 加路由文件 + `api/index.ts` 加分发 |
| 客户端换网络库 | 只改 `net/SyncClient` 内部实现 |
| 新增安全黑名单词 | `engine/SafetyGuard.DENY_WORDS` |

## 10. 测试

**客户端：**
- JVM 单测：`cd client && ./gradlew testDebugUnitTest`（引擎匹配 + SafetyGuard 护栏）
- 构建验证：`cd client && ./gradlew assembleDebug`

**服务端（bun:test）：**
- 全部测试：`cd server && bun test`
- 单元测试（24 项）：validate（14）/ auth（5）/ rateLimit（5）
- 冒烟测试（16 项）：in-process 启动服务器，覆盖全部 v0+v1 路由 + 鉴权 + 校验

## 11. 项目结构

```
AdSkip/                            全栈 monorepo
├── client/                        Android 客户端（Kotlin + Compose，Gradle 工程根）
│   ├── build.gradle.kts           模块与签名配置（签名参数读 local.properties）
│   ├── settings.gradle.kts        仓库配置（国内镜像优先）
│   └── app/src/main/java/com/ldp/adskip/
│       ├── ui/                   Compose UI（单 Activity + 4 Screen + ViewModel）
│       ├── core/                 AppEvents / Clock / AppExecutors / LogRing
│       ├── service/              SkipAdService（无障碍服务）
│       ├── engine/               规则引擎（纯 JVM 可测）
│       ├── data/                 Prefs / RulesRepository / StatsRepository
│       ├── net/                  SyncClient
│       └── sync/                 SyncJobService
├── server/                       Bun + TypeScript 后端
│   ├── src/
│   │   ├── api/                  路由拆分（rulesApi / statsApi / healthApi）
│   │   ├── middleware/           鉴权 + 限流（auth.ts / rateLimit.ts）
│   │   ├── utils/                HTTP 工具 + 校验（httpUtil.ts / validate.ts）
│   │   ├── storage/              规则 + 统计存储（store.ts）
│   │   └── config.ts             全部可调参数
│   ├── test/                     bun:test 单元 + 冒烟
│   ├── server.ts                 入口（Bun.serve）
│   └── public/                   落地页 + 管理后台
├── docs/                         ARCHITECTURE.md / ROADMAP.md
└── .github/workflows/ci.yml     CI：Android Build + Bun Server Tests
```
