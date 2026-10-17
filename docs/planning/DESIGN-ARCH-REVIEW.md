# 整体架构评审与技术设计（DESIGN-ARCH-REVIEW）

> 状态：已合并（[#179](https://github.com/LiYihai1121/ui-app/pull/179)，`723a315`）；最后更新：2026-10-16。
> 范围：轻启/AdSkip 客户端（Kotlin + Compose）+ 服务端（Bun + TypeScript）整体架构现状评审，以及 FOLLOW-UP 中两条 P1、一条 P2 的落地技术设计。
> 依据：[ARCHITECTURE.md](../architecture/ARCHITECTURE.md)、[API.md](../api/API.md)、[FOLLOW-UP.md](FOLLOW-UP.md) 与全量关键源码核对（2026-10-16，含 server/src/*、client/app/src/main/java 下 engine/data/net/service/sync/core）。
>
> **落地状态（2026-10-16 勘误）**：本文 §6 五项修复**已全部落地**——§6.1/§6.2 随 v1.0.1（[#167](https://github.com/LiYihai1121/ui-app/pull/167)，`c68133a`）先行修入 main（评审基线误记为未修）；§6.3/§6.4/§6.5 随 [#181](https://github.com/LiYihai1121/ui-app/pull/181)（`465a5e8`）落地。各节标题下标注实际落地形态与本文设计的差异（落地实现择优调整处）。

## 1. 评审结论

架构整体成熟：**分层边界由契约测试机器强制**（`ArchitectureBoundaryTest` / `ManifestContractTest` / `ProjectStructureTest` / `RepoHygieneTest`），引擎纯 JVM 可单测，安全链（HMAC 验签 + 证书锁定 + HTTPS-only + fail-closed）闭环，v1 协议 ETag/304 + schemaVersion 纯增量演进有双端契约夹具守护。未发现需要推翻重来的架构级缺陷；剩余问题集中在**统计计数一致性、自检态安全护栏、系统界面误跳**三处实现层缺口（均有 FOLLOW-UP 登记），以及两处评审新发现。

| 维度 | 评价 | 说明 |
|---|---|---|
| 分层与依赖 | ✅ 优 | 包边界可执行、守护测试在 CI 即失败；engine 零 Android 依赖 |
| 安全模型 | ✅ 优 | 验签/证书锁定/限流双桶/XFF 信任模型/包级黑名单齐备，fail-closed 一致 |
| 协议演进 | ✅ 优 | schema 2 纯增量 + 双端契约夹具（`selectors.contract.json`，含 divergences 段） |
| 数据一致性 | ⚠️ 待修 | 客户端计数双重累加（P1）；云端规则落地非原子（新发现 P2） |
| 可测性 | ✅ 良 | 引擎/边界/选择器契约有 JVM 单测；缺口：E2E 为 0、无覆盖率度量 |
| 可观测 | ⚠️ 一般 | 服务端结构化日志（JSONL）已落地；无指标导出（P3 规划） |

## 2. 评审问题清单（均经源码定位）

### 2.1 P1 发布版自检必然超时（FOLLOW-UP §待修复 #1）

- **证据**：`SafetyGuard.SELF_PKG = "com.qingqi.adskip"` 与 `applicationId` 相同；`canClick()` 对自身包**一律返回 false**（防自触发死循环）。`SkipAdService.onAccessibilityEvent` 在 `AppEvents.testActive == true` 时放行自身包事件进入 `trySkip`，但随后 `canClick(target, pkg)` 仍将自身包拦死——自检浮层「跳过」永远点不了。
- **影响**：发布版开屏自检功能不可用（#145 曾用 `.debug` 后缀并行包绕过护栏而误判修复）。
- **修复设计**见 §6.1。
- **✅ 已落地**：v1.0.1（#167，`c68133a`）——本文评审基线误记为未修，实际参数化形态为 `canClick(node, pkg, allowSelfPackage = false)`（参数名与 §6.1 的 `testActive` 不同，语义一致）。

### 2.2 P1 跳过计数双重累加（FOLLOW-UP §待修复 #2）

- **证据**：`StatsRepository.recordSkip()` 调用 `Prefs.recordSkip()`（**立即写 SP** `total_skips` 与 `pkg_count:$pkg`），随后又 `pendingTotal++` / `pendingByPkg[pkg]++`；`total() = Prefs.getTotalSkips() + pendingTotal` **双算**。且 `flush()` 落盘后**不重置** `pendingTotal` / `pendingByPkg`，计数无界膨胀。
- **影响**：首页计数虚高（单次跳过后立即显示 2、5 秒后 3）；进程生命周期内 `total()` 持续漂移；结构自 v3.4.0 #78 引入。
- **修复设计**见 §6.2。
- **✅ 已落地**：v1.0.1（#167，`c68133a`）——落地形态比本文设计更进一步：`Prefs` 拆为 `appendSkipLog`（只写日志）+ `addSkipCounts`（计数唯一落盘路径），`StatsRepository` 改经 `StatsStore` 端口注入（单测用假实现），flush 前置重置待落盘增量。

### 2.3 P2 引擎误跳系统界面文本（FOLLOW-UP §待修复）

- **证据**：`SkipRuleEngine.matches()` 仅校验 `isVisible` / `isEditable`，**不校验 `clickable`**；`SafetyGuard.canClick()` 校验 visible + 面积，同样不校验 `clickable`；`clickNode()` 末尾兜底坐标手势。于是任何含关键词的可见文本（含不可点击的纯文本行）都会被点击——API 33 设置页本应用服务行「轻启 · Skip Splash Ads」被点并落库 `com.android.settings`。
- **影响**：系统界面误跳计数；用户可感知的异常点击。
- **修复设计**见 §6.3。
- **✅ 已落地**：#181（`465a5e8`）——落地形态为「可点击路径护栏」：节点自身或 ≤4 层祖先可点击（`isClickable` 或 `clickableParent()` 命中），纯文本行不再被点。

### 2.4 P2 云端规则落地非原子（评审新发现）

- **证据**：`RulesRepository.applyCloudRules()` 先 `Prefs.clearAllPkgRules()` 删除全部 `pkg_*` 键，再逐条 `savePkgKeywords/ViewIds/Selectors` 写回。中间进程死亡（同步正在执行时被系统杀掉）→ 应用专属规则全丢，仅剩全局关键词。
- **影响**：极端场景下云端规则半覆盖；表现为「同步过反而应用专属规则消失」。概率低但不可自愈。
- **修复设计**见 §6.4。
- **✅ 已落地**：#181（`465a5e8`）——落地形态为端口化：`RulesStore` 接口 + `PrefsRulesStore`，`Prefs.replaceAllRules` 单次 edit 原子提交全部键；分次写入路径（`savePkgKeywords` 等五方法）已删除。

### 2.5 P3 v1 上报失败回退 v0 的双重上报风险（评审新发现）

- **证据**：`SyncClient.reportSkip()` v1 批量上报**任何异常**（含 429 限流、5xx）都回退 `POST /api/skip` 单条上报。
- **影响**：网络抖动时同一跳过事件可能双份入库（v1 失败后 v0 成功），服务端 `byApp` 计数虚增；v0 通道继续消耗写令牌桶。
- **修复设计**见 §6.5。
- **✅ 已落地**：#181（`465a5e8`）——回退捕获收紧为仅 `IOException`；429/4xx/5xx 传播外层静默失败，不回退。

## 3. 目标设计（关键模块契约）

### 3.1 引擎匹配与安全护栏契约（修正后）

```
matches(node, rules): node.isVisible ∧ ¬node.isEditable ∧
    (∃ selector ∈ rules.selectors: SelectorMatcher.matches(selector, node)) ∨
    (text/desc 含关键词，且 text 长度 ≤ maxTextLen) ∨
    (viewId 含规则串，且规则长度 ≥ minViewIdLen)

canClick(node, pkg, testActive):          ← 新增 testActive 参数
    (pkg == SELF_PKG → 仅 testActive 为 true 时放行) ∧
    ¬isPackageDenied(pkg) ∧               ← 包级黑名单（测试态也生效）
    ¬(pkg ∈ DENY_SYSTEM_PACKAGES) ∧       ← 敏感系统整包（测试态也生效）
    node.isVisible ∧ 面积 > 0 ∧
    (node.isClickable ∨ node.isFocusable) ← 新增可点击/可聚焦校验
    ∧ ¬(自身+父链≤3 层文案命中 DENY_WORDS)
```

- `RuleSet` 数据结构不变（keywords / viewIds / selectors / disabled / schemaVersion）。
- `AdNode` 接口**不需要扩展**——`isClickable` 已存在（`FrameworkAdNode` 映射 `AccessibilityNodeInfo.isClickable`）。
- engine 层**不得**依赖 `core/AppEvents`（边界契约）；测试态标记由 service 层以参数注入，engine 保持纯 JVM。

### 3.2 统计计数契约（修正后）

- **单一写者**：计数只进内存 `pendingTotal` / `pendingByPkg`；`Prefs.recordSkip()` 只写日志，不再写 total / pkg_count。
- **唯一落盘点**：`flush()` 批量提交全部增量（`total_skips += pendingTotal`；`pkg_count:$pkg += delta`），落盘后 `pendingTotal = 0`、`pendingByPkg.clear()`、`dirty = false`。
- **读路径不变**：`total() = SP + pending`、`countFor(pkg) = SP + pendingByPkg[pkg]`——实时一致且落盘后不变。
- 触发点不变：5s `postDelayed` + `onDestroy` / 服务停止时强制 flush。

### 3.3 规则同步契约

```
securityGuard(HTTPS-only + 签名密钥 + pins)      ← fail-closed 拒连
  → GET /api/v1/rules/latest  (If-None-Match: hash)
      → 304：仅更新 lastSyncAt
      → 200：验签 HMAC → parseRulesResponse → applyCloudRules（schemaVersion ≥ MIN）
            → setRulesHash + setLastSyncAt
失败任一环节：不动本地规则（旧规则继续生效）
```

- `applyCloudRules` 原子化：内存中构建完整新映射后，用**单次** `SharedPreferences.Editor` 批量 remove 旧 `pkg_*` 键 + put 新键，一次 `apply()`（§6.4）。

### 3.4 服务端上报契约

```
probeReportIp（读体前 IP 探测，不扣减）→ readBody（协议层 ≤1MiB）→ safeJsonParse
  → cleanBatchReport（deviceId ≥8、事件 ≤50、包名 PKG_RE）→ limitReport（IP 桶 + 设备桶双扣）
  → recordSkip × N（当日 byApp 封顶 2000 → _other）
错误码：400 载荷非法 / 413 体超限 / 415 类型 / 429 限流 / 503 未配令牌 / 5xx 归一化
```

## 4. 关键时序

**时序 1 — 云端规则同步（含 304 路径）**

```text
设置页/SyncJobService → SyncClient.syncRules(io 线程)
  → securityGuard: ServerEndpoint.isValid(https) + 签名密钥非空 + pins 非空
  → GET /api/v1/rules/latest, If-None-Match: <hash>
      ├─ 304 → setLastSyncAt → 「已是最新」
      └─ 200 → readBodyCapped(≤2MiB) → verifyRulesSignature(HMAC-SHA256, 常数时间)
               → parseRulesResponse(全局+应用级三通道，非法包名键跳过)
               → applyCloudRules(schemaVersion ≥ 1) → setRulesHash + setLastSyncAt
任一步失败 → 返回失败提示，本地规则不动
```

**时序 2 — 跳过主链路**

```text
窗口事件 → onAccessibilityEvent: 系统 UI 忽略 → 包级黑名单拦截 → (自身包 ∧ ¬testActive) 拦截
  → trySkip: DND 检查 → 150ms 节流 → 1.2s 同应用去抖
  → ruleSetFor(pkg)（LruCache 命中）→ engine.findTarget（DFS ≤500 节点）
  → canClick(target, pkg, testActive) ← 修正后传 testActive
  → clickNode: ACTION_CLICK → 父链可点击 → 坐标手势兜底
  → statsRepo.recordSkip（内存计数 + 日志）→ 5s 后 flush 落盘
  → syncClient.reportSkip（v1 批量，证书锁定；修正后失败不回退 v0）
  → AppEvents.emitSkipped → UI 刷新；Toast（testActive 时抑制）
```

**时序 3 — 统计合批落盘（修正后）**

```text
recordSkip: pendingTotal++; pendingByPkg[pkg]++; postDelayed(flush, 5s)
flush: dirty 检查 → ioExecutor: SP total += pendingTotal; pkg_count += delta; apply()
       → pendingTotal = 0; pendingByPkg.clear(); dirty = false   ← 新增：落盘后重置
onDestroy → flush()（强制）
```

**时序 4 — 发布版自检（修正后）**

```text
UI 开自检 → AppEvents.setTestActive(true)
  → 自身包事件放行进入 trySkip（现有逻辑）
  → canClick(target, "com.qingqi.adskip", testActive=true) → 放行（测试态无死循环回路）
  → 点击成功 → 计数 + 上报（testActive 时 Toast 抑制）
自检结束 → setTestActive(false) → 自身包重新被 canClick 拦死（防自触发恢复生效）
```

## 5. 错误处理与降级规格

| 场景 | 行为 | 降级路径 |
|---|---|---|
| 同步 304 | 更新 lastSyncAt，规则不动 | — |
| 同步 200 验签失败 | 拒载并提示「签名校验失败」 | 旧规则继续生效 |
| 未配签名密钥 / pins / 非 HTTPS | fail-closed 拒连 | 本地规则照常工作（离线优先） |
| 同步网络异常 | 静默失败（JobService）/ 提示（手动） | 本地规则照常 |
| 上报失败 | 静默失败（**修正后**不回退 v0） | 本地统计不受影响 |
| 引擎未命中 | 本次扫描跳过 | 150ms 全局节流兜住扫描成本 |
| 服务端 5xx | 响应体归一化 `internal error` | 细节只进服务端日志 |
| 云端规则半套载荷 | **修正后**单次 edit 批量提交 | 旧规则继续生效（非半覆盖） |

## 6. 待修复项落地设计

### 6.1 P1 自检超时修复（0.5 天）— ✅ 已落地（v1.0.1，#167 `c68133a`）

- `SafetyGuard.canClick(node, pkg, testActive: Boolean = false)`：自身包判定改为 `if (pkg == SELF_PKG && !testActive) return false`。默认参数保持所有既有调用点行为不变。
- `SkipAdService.trySkip` 调用处传入 `AppEvents.testActive`（service 已依赖 core，不违反边界；engine 不引入 core 依赖）。
- 测试态说明：自检由 UI 显式开启/关闭，不存在「广告页面递归触发自身事件」的死循环回路，放行自身包不破坏防自触发设计的意图。
- **契约测试**：JVM 单测断言——发布包名 `com.qingqi.adskip` + `testActive=false` → `canClick=false`；+ `testActive=true` → `canClick=true`（其余黑名单/系统包断言不变，包级黑名单测试态也生效）。

### 6.2 P1 计数双重累加修复（0.5 天）— ✅ 已落地（v1.0.1，#167 `c68133a`）

- `Prefs.recordSkip()` 删除 `total_skips` 与 `pkg_count:$pkg` 的写入，**只保留日志**（`addLog`）。
- `StatsRepository.flush()` 末尾新增：`pendingTotal = 0; pendingByPkg.clear()`。
- 读路径（`total()` / `countFor()`）与触发点（5s 延迟 / onDestroy 强制）不变。
- **契约测试**：JVM/Robolectric 单测断言「N 次 `recordSkip` → `total() == N`，且 `flush()` 后仍为 N」。

### 6.3 P2 引擎误跳修复（1 天）— ✅ 已落地（#181，`465a5e8`）

> 落地差异：实现为「可点击路径护栏」（自身或 ≤4 层祖先可点击即放行），而非本文 `isClickable || isFocusable` 的并集校验——`isFocusable` 单独不构成放行条件，父链可达点击仍放行（广告按钮的可点击父容器场景）。落地契约见 `SafetyGuardTest` 的 4 个新增用例。

- `SafetyGuard.canClick()` 合法性校验增加 `node.isClickable || node.isFocusable`（复用 `AdNode` 既有抽象，**不改接口**）。广告跳过按钮均为可点击节点；纯文本行不再被点。
- 包级豁免（备选，与上者取其一或并存）：`DENY_SYSTEM_PACKAGES` 已覆盖 `com.android.settings` / `systemui`；按需补充 launcher 类系统包。
- **回归用例**：真实窗口回归（API 33 设置页服务行不再被点；`com.android.settings` 不再产生落库计数）。

### 6.4 P2 规则落地原子化（0.5 天）— ✅ 已落地（#181，`465a5e8`）

> 落地差异：经 `RulesStore` 端口 + `Prefs.replaceAllRules` 单次 edit 实现（参照 StatsStore 先例，使原子性可在纯 JVM 单测断言）；**分次写入路径已删除**（`savePkgKeywords/ViewIds/Selectors`、`clearAllPkgRules`、`replaceDisabledPackages`）——保留即给「半新半旧」复发留门。另：`RulesRepository` 的 `android.util.LruCache` 换为纯 Kotlin `LinkedHashMap`（isReturnDefaultValues 下 LruCache 是空壳，缓存失效无法在 JVM 断言）。落地契约见 `RulesRepositoryTest` 7 项。

- `RulesRepository.applyCloudRules()` 改为两阶段：① 在内存构建 `新 pkg 键值映射`（含 disabled 集合）；② 单次 `Prefs.sp(context).edit()`：先 remove 全部旧 `pkg_*` 键，再 put 新键，一次 `apply()`。
- `SharedPreferences` 的 `apply()` 本身异步落盘但**语义原子**（同一 edit 要么整体生效要么整体不生效），消除半覆盖窗口。
- **契约测试**：JVM/Robolectric 单测断言——`applyCloudRules` 后旧应用专属规则全部被替换、新规则完整生效（用计数断言，无需模拟崩溃）。

### 6.5 P3 v1→v0 回退收紧（0.5 天）— ✅ 已落地（#181，`465a5e8`）

> 落地差异：回退捕获由「所有 Exception」收紧为仅 `IOException`（网络层异常，含连接失败/DNS/超时）；`SyncClientTest` 落地 3 个用例（500 不回退 / 429 不回退 / 断网 DISCONNECT_AT_START 才回退）。

- `SyncClient.reportSkip()` 的 v0 回退仅保留**网络层异常**（`IOException` 等连接失败）；HTTP 状态非 2xx（429/5xx/4xx）直接静默失败，不再回退。
- v0 端点（`/api/skip`）在服务端继续保留供旧客户端兼容，客户端新代码不再主动回退。
- **回归**：`SyncClientTest`（MockWebServer）补一例——v1 返回 429 时不再产生 v0 请求。

## 7. 契约测试清单（合并前必须全绿）

> 落地状态（2026-10-16）：下表全部已落地——§6.1/§6.2 随 v1.0.1（#167）；§6.3/§6.4/§6.5 随 #181（RulesRepositoryTest 为新建，其余为扩展）。全量门禁实测：`ktlintCheck + testDebugUnitTest`（286 项 / 34 套件 / 0 失败）+ `assembleDebug` 全绿。

| 测试 | 文件（拟） | 断言 |
|---|---|---|
| SafetyGuard 测试态自身包 | `engine/SafetyGuardTest.kt`（扩展） | 发布包名 + testActive=true 可点；=false 拦截；包级黑名单不受 testActive 影响 |
| 计数一致性 | `data/StatsRepositoryTest.kt`（新建） | N 次 recordSkip → total()==N；flush 后不变 |
| clickable 护栏 | `engine/SafetyGuardTest.kt`（扩展） | 不可点击/不可聚焦文本不通过 canClick |
| 规则落地原子化 | `data/RulesRepositoryTest.kt`（新建） | applyCloudRules 后旧 pkg 规则全清、新规则完整 |
| v1 失败不回退 v0 | `net/SyncClientTest.kt`（扩展） | 429 时无 v0 请求发出 |
| 双端选择器契约 | `engine/SelectorContractTest.kt`（已存在） | 不变，回归全绿 |
| 边界/结构/卫生守护 | 四大 arch 测试（已存在） | 不变，回归全绿 |

门禁（按 [AGENTS.md](../../AGENTS.md)）：`cd client && ./gradlew ktlintCheck`、`assembleDebug`、`testDebugUnitTest`；服务端改动若涉及 `bun test` 一并执行。

## 8. 风险与回滚

- **§6.1 放行自身包**：风险为 testActive 误置位时对自身页面自动点击——但自检由 UI 显式开关驱动且 `setTestActive(false)` 在结束时复位；包级黑名单/系统包拒绝在测试态依旧生效，风险可控。回滚：canClick 恢复默认参数 `testActive=false` 即等价旧行为。
- **§6.2 计数语义变更**：升级后首次 flush 之前读到的总数与旧版不可比（旧版 SP 值虚高一次）。属一次性纠正，无需迁移逻辑；回滚即恢复 Prefs.recordSkip 写计数。
- **§6.3 可点击校验**：误伤风险为「命中关键词但目标节点不可点击、点击实际发生在不可聚焦父节点」的极少数广告形态；以真机回归用例兜底。回滚：去掉该校验。
- **§6.4/§6.5**：纯实现层收窄，行为面更小，回滚成本低。

## 9. 相关文档

- [ARCHITECTURE.md](../architecture/ARCHITECTURE.md)（分层职责 / 安全模型 / 数据流 / 协议版本）
- [API.md](../api/API.md)（v1 协议与限频契约）
- [FOLLOW-UP.md](FOLLOW-UP.md)（待修复项登记，本文 §6 为其落地设计）
- [DESIGN-PHASE1-SELECTOR.md](DESIGN-PHASE1-SELECTOR.md)（选择器第三通道，契约夹具来源）
