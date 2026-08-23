# 净启动 AdSkip 架构文档

## 1. 系统全景

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android 客户端                            │
│                                                                  │
│  ui/                 service/           engine/                  │
│  ├ MainActivity      SkipAdService      SkipRuleEngine           │
│  ├ AppListActivity   （薄编排层）──────▶（纯匹配逻辑，依赖 AdNode）│
│  ├ LogsActivity           │            ├ AdNode（抽象接口）      │
│  └ SettingsActivity       │            ├ FrameworkAdNode         │
│        │                 │            ├ SafetyGuard（安全护栏）   │
│        │                 ▼            └ RuleSet（+ schemaVersion）│
│        │                 data/                                   │
│        │                ├ Prefs（SharedPreferences 原语）         │
│        │                ├ RulesRepository（合并/开关/LruCache）    │
│        │                └ StatsRepository（合批落盘）              │
│        │                                                         │
│        └─── AppContainer ──▶ net/SyncClient（v1: ETag/批量）      │
│              (手动 DI)     └ sync/SyncJobService（JobScheduler） │
└──────────────────────────────┬───────────────────────────────────┘
                               │ HTTP（v1 协议）
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      后端服务（Node.js 零依赖）                   │
│                                                                  │
│  server.js（路由引导 + 优雅停机）                                  │
│     ├─ src/api/      规则/统计/健康检查路由拆分                    │
│     │     ├ rulesApi   v0 + v1 规则下发/发布/模拟器              │
│     │     ├ statsApi   统计汇总                                   │
│     │     └ healthApi  健康检查                                   │
│     │     └ validate.js / auth.js / rateLimit.js                 │
│     ├─ src/store.js    规则（缓存+备份轮转）/ 统计（分日分片）      │
│     ├─ src/httpUtil.js CORS 白名单 / 安全 JSON 解析               │
│     └─ src/config.js   env 覆盖配置                               │
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
| **ui/** | 4 个 Activity | 展示与用户交互 | 不直接读 SharedPreferences、不碰网络、经 container 取依赖 |
| **service/** | SkipAdService | 事件接收、节流去抖、点击执行、广播通知 | 不含匹配规则逻辑、**不做安全裁决（交 SafetyGuard）** |
| **engine/** | SkipRuleEngine + RuleSet + AdNode + SafetyGuard | 纯匹配：文本/ViewID 双通道，返回目标节点；安全护栏复核 | 不执行点击、不读存储 |
| **data/** | Prefs / RulesRepository / StatsRepository | 存储原语 + 领域仓库（规则合并、开关、LruCache、合批计数、日志） | 不感知 UI 与网络格式 |
| **net/** | SyncClient | HTTP 传输（v1: ETag/304/批量补报），落地委托 RulesRepository | 不直接改存储键值 |
| **core/** | Clock / AppExecutors / LogRing | 时钟注入、线程域收口、环形日志 | 不含业务逻辑 |
| **sync/** | SyncJobService | JobScheduler 周期同步（跨重启持久化） | 不含同步逻辑（委托 SyncClient） |

**关键设计**：`RulesRepository.ruleSetFor(pkg)` 是规则的唯一组装点——全局关键词 + 应用专属关键词 + 全局 ViewID + 应用专属 ViewID + 禁用开关，合并为一个不可变的 `RuleSet` 交给引擎。`LruCache` 按 `(pkg → version)` 缓存合并结果，事件高频路径上只剩查表。新增规则来源只需扩展 RuleSet 与引擎。

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
        → 广播 ACTION_SKIPPED → 主页刷新统计
```

**云端规则同步（v1 协议）：**
```
SyncJobService / 设置页触发 → SyncClient GET /api/v1/rules/latest
          → If-None-Match: <已知 hash> → 304 Not Modified（省流量省电）
          → 或 200 + 新规则 → RulesRepository.applyCloudRules（校验 schemaVersion）
          → Prefs.setRulesHash → 记录同步时间 → 主页回调提示
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
| `server.js` | 进程引导、路由分发、优雅停机（SIGTERM/SIGINT → store.flush()） |
| `src/api/` | 路由拆分：rulesApi（v0+v1）/ statsApi / healthApi |
| `src/auth.js` | Bearer token 鉴权，保护写接口 |
| `src/rateLimit.js` | 内存令牌桶：per-IP（读/写）+ per-deviceId（上报） |
| `src/validate.js` | 载荷校验（与客户端同源约束：PKG_RE / VID_RE / 长度上限 / 未知字段拒绝） |
| `src/store.js` | 规则（缓存+备份轮转）/ 统计（分日分片+内存缓存+延迟刷盘） |
| `src/httpUtil.js` | CORS 白名单、安全 JSON 解析（键数/深度上限）、请求体大小保护 |
| `src/config.js` | 全部可调参数（env 覆盖） |

## 5. 安全模型

三层安全防护，从信任服务器改为本地最小权限：

| 层 | 机制 | 说明 |
|---|---|---|
| **服务端鉴权** | Bearer token | `ADMIN_TOKEN` 环境变量；未配置时写接口返回 503（拒绝服务而非裸奔）；规则发布/模拟器需鉴权 |
| **服务端校验** | validate.js | 包名正则约束 `^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$`、关键词 ≤12 字、总条目 ≤2000、未知字段拒绝、body 键数/深度上限 |
| **客户端护栏** | SafetyGuard | 硬编码黑名单（支付/付款/确认/同意/购买/下单/授权/登录/免密/开通/安装/下载），云规则**不可覆盖**；防自触发死循环；可见性/面积校验 |

**设计原则**：无论云端规则怎么被污染，客户端都有硬底线。SafetyGuard 在引擎找到目标后、执行点击前做最终复核。

## 6. 线程模型

| 操作 | 线程 | 说明 |
|---|---|---|
| 无障碍事件处理 | 主线程 | onAccessibilityEvent 在主线程；节流去抖纯内存操作 |
| 规则匹配 | 主线程 | 引擎纯 CPU 计算，无 I/O |
| 统计计数 | 主线程（内存）→ IO 线程（落盘） | 内存先记，5s 后 AppExecutors.io 合批写 SP |
| 规则同步 | IO 线程 | SyncClient 在 AppExecutors.io 执行网络请求 |
| 上报 | IO 线程 | SyncClient.reportSkip 在独立线程（静默失败） |
| JobService | IO 线程 | onStartJob → executors.io → syncRulesSilently |

**设计原则**：事件高频路径（onAccessibilityEvent → ruleSetFor → findTarget）不碰磁盘；I/O 操作统一走 AppExecutors.io 单线程域。

## 7. 协议版本与兼容策略

| 版本 | 路由 | 说明 |
|---|---|---|
| v0（兼容期） | `/api/rules/latest`, `/api/rules`, `/api/skip`, `/api/stats/summary` | 旧客户端无感，字段格式不变 |
| v1（当前） | `/api/v1/rules/latest` (ETag/304), `/api/v1/rules`, `/api/v1/reports/batch`, `/api/v1/rules/test`, `/api/v1/stats/summary`, `/api/v1/health` | 新增 schemaVersion/hash/ETag/批量上报/规则模拟器 |

**兼容策略**：旧路由保留一个版本期；客户端同步前先比 `hash`（If-None-Match）；`deviceId` 同时作为限频维度；`schemaVersion` 低于客户端支持版本时拒载并提示升级。

## 8. 设计决策

- **零第三方依赖**：客户端仅 Kotlin 标准库 + 框架 API，服务端仅 Node 原生模块。安装包 <1MB，服务端无需 npm install。
- **JSON 文件存储**：单进程本地服务，数据量小；tmp+rename 原子写避免损坏。统计按天分片（data/stats/YYYY-MM-DD.json），14 天趋势 = 读 14 个小文件。
- **离线优先**：客户端一切功能本地可用；服务端不可达时上报静默失败。
- **节流三重保护**：同应用 1.2s 去抖、全局 150ms 扫描节流、单次遍历 ≤500 节点。
- **手动 DI**：AppContainer 收口所有依赖，不引入任何第三方 DI 框架。
- **可测性**：AdNode 抽象使引擎可跑纯 JVM 单测（FakeAdNode + 注入 Clock），不依赖 Android 框架。

## 9. 扩展点

| 需求 | 改动位置 |
|---|---|
| 新匹配通道（坐标/图像规则） | `engine/RuleSet` 加字段 + `SkipRuleEngine.matches` 加分支 |
| 定时自动同步 | `sync/SyncJobService`（JobScheduler，替代旧 AlarmManager 套件） |
| 服务端换数据库 | 只改 `src/store.js` |
| 新增 API | `src/api/` 加路由文件；客户端 `SyncClient` 加方法 |
| 客户端换 Room/网络库 | 只改 `data/Prefs` 与 `net/SyncClient` 内部实现 |
| 新增安全黑名单词 | `engine/SafetyGuard.DENY_WORDS` |

## 10. 测试

- 服务端单元测试：`cd server && npm run test:unit`（24 项：validate/auth/rateLimit）
- 服务端冒烟测试：`cd server && npm run test:smoke`（16 项：全部路由 v0+v1 + 鉴权 + 校验）
- 客户端 JVM 单测：`./gradlew testDebugUnitTest`（引擎匹配 + SafetyGuard 护栏）
- 客户端端到端：App 内「测试：模拟开屏广告」验证无障碍链路
