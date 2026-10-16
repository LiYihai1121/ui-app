# 分层与模块化重构技术设计（DESIGN-MODULE-REARCH）

> 状态：待评审（本文随 feature 分支提交，合并前需领域负责人 review）；最后更新：2026-10-16。
> 范围：针对「**去广告覆盖率不够**」这一业务目标，评审客户端的**分层与模块化**重构路线，给出技术选型 ADR、目标模块结构、迁移顺序、契约测试清单与回滚策略。
> 依据：全量源码核对（2026-10-16，`client/app/src/main/java` 97 个 `.kt` / ≈14.6k LOC，`server/src` 14 个 `.ts` / ≈1.6k LOC）与既有文档 [DESIGN-ARCH-EVOLUTION.md](DESIGN-ARCH-EVOLUTION.md)、[DESIGN-BUILD-FRAMEWORK.md](DESIGN-BUILD-FRAMEWORK.md)、[DESIGN-ADS-FILTERING.md](DESIGN-ADS-FILTERING.md)、[ROADMAP-ADS.md](ROADMAP-ADS.md)、[ARCHITECTURE.md](../architecture/ARCHITECTURE.md)。

## 1. 结论先行

**本次重构的驱动因素是覆盖率，不是技术栈。** 因此结论分两半，且这两半是同一件事：

1. **覆盖率瓶颈在能力层，不在代码质量。** 现状只有 L1（无障碍点击）一层落地，而 L1 的物理上限就写死在 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 4 节矩阵里：开屏 ✅ 高、插屏 ✅ 中高，但 Banner / 信息流 / 联盟广告（国内占比最高）❌ 无解，推送通知 ✅ 待 L4，摇一摇 ✅ 需 L3。**不新增 L2/L4 能力层，重构完覆盖率仍是 45%**。§2.1 给出实测证据。
2. **分层/模块化的价值，是把新能力层做成「可独立开关、可独立审计、可独立回滚」的物理边界。** 这是本次重构唯一成立的理由（§3.1）。L2 的 `VpnService` 与 L4 的 `NotificationListenerService` 都是**系统级特权面**，若与 L1 无障碍引擎共存于同一 `:app` 模块、共享同一份 `SafetyGuard` 信任边界，则「默认关闭、用户明确授权、可一键撤销」的产品承诺在代码层面无法被强制——只能靠纪律。模块边界能把纪律变成编译器检查。

**建议路线：演进式模块化重构（Route A），不推倒重写（Route B）。** 与 [DESIGN-ARCH-EVOLUTION.md](DESIGN-ARCH-EVOLUTION.md) 既有结论一致——该文列的 4 条重写触发判据（iOS/桌面、第三方代码沙箱、多实例水平扩展、Bun 生态风险）**目前一条都未触发**，本次「覆盖率不足」不在其中，故不足以推翻既有决策；本设计是把它的 R1 从「待排期」提升为「覆盖率路线的前置工程」。

| 维度 | 现状评价 | 与覆盖率的关系 |
|---|---|---|
| 包分层与依赖方向 | ✅ 优（`ArchitectureBoundaryTest` 源码级强制，`engine/` 零 Android 依赖实测成立） | 拆分后可升级为**编译器强制**，是新能力层的隔离前提 |
| 构建框架 | ✅ 阶段 A/B 已落地（version catalog + 约定插件 + Gradle 硬化） | 阶段 C 是唯一缺口，本设计直接复用其蓝图 |
| 能力层覆盖 | ❌ **瓶颈**（仅 L1） | 本次重构的真实目标 |
| 模块粒度 | ⚠️ 单 `:app`（97 文件 / 14.6k LOC 全在一个模块） | 新能力层无处安放，只能继续往 `:app` 里塞 |
| 服务端 | ✅ 零依赖单进程，够用 | L2 只需新增一个 `filter-rules` 路由，**无需重构** |

## 2. 现状评审（均经源码/文档定位）

### 2.1 覆盖率瓶颈的实测定位（P0）

| 证据 | 位置 | 含义 |
|---|---|---|
| 覆盖矩阵里 Banner / 信息流 / Draw / 联盟广告走 **L2**，推送走 **L4**，摇一摇走 **L3** | `ROADMAP-ADS.md` §4 | L1 单层无法提高这些行的覆盖度——**这是能力层缺失，不是引擎不够强** |
| 「穿山甲八大样式**无一可由 L1 完整解决**（除开屏与插屏）」 | `ROADMAP-ADS.md` §4 附录 | 已由文档自证 |
| 「较好解决 ≈45% / 部分 ≈25% / 不可拦 ≈30%」 | `ROADMAP-ADS.md` §9 | 45% 即当前 L1-only 的天花板 |
| `PermissionCenter` 已管理 INTERNET / RECEIVE_BOOT_COMPLETED / POST_NOTIFICATIONS / REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 四类权限 | `app/src/main/AndroidManifest.xml` L5–L21 | L2 需新增 `BIND_VPN_SERVICE`、L4 需 `BIND_NOTIFICATION_LISTENER_SERVICE`——**后者是 major 级权限面**（`ROADMAP-ADS.md` §6 已标注 `4.0.0` 为 major 正因如此） |
| 无 `VpnService` / `NotificationListenerService` 任何实现 | 全仓 grep | L2/L4 零起点 |

### 2.2 模块化缺口（P1）

- **单模块 97 文件 / ≈14.6k LOC**：`ui/` 24、`device/` 10、`core/` 7、`data/` 7、`engine/` 7、`service/` 3、`net/` 1、`sync/` 1。规模本身尚可，但**没有物理隔离点**给新能力层。
- **边界靠扫描而非编译**：`ARCHITECTURE.md` §2.1 的 9 条依赖规则由 `ArchitectureBoundaryTest` 扫描 `import` 源码强制。有效，但属「事后检查」——违规代码能写、能提交，只在测试期失败。拆模块后同类违规**编译不过**。
- **`engine/` 已具备拆分条件**：实测 `grep` 全 `engine/` 无任何 `android.*` / `androidx.*` import，纯 Kotlin/JDK——`DESIGN-BUILD-FRAMEWORK.md` §4.2 的 `:core:engine` 拆分「零风险」判断成立。
- **`--enable-*` 与签名逻辑已就位**：`app/build.gradle.kts` 的 `local.properties` 四键签名三态策略、Compose 映射对齐 hack 均在 `:app` 内，**拆分时不得漂移**（`DESIGN-BUILD-FRAMEWORK.md` §3.4 坑 5）。

### 2.3 与既有计划的关系（避免重复建设）

- [DESIGN-BUILD-FRAMEWORK.md](DESIGN-BUILD-FRAMEWORK.md) §4 已给出 `core:common` / `core:engine` 的拆分蓝图与迁移顺序——**本设计不另起一套**，直接采纳并扩展一层（新增能力层模块）。
- [DESIGN-ARCH-EVOLUTION.md](DESIGN-ARCH-EVOLUTION.md) 的 R1 内容即「客户端多模块化 + 存储评估」——**本设计把 R1 的动机从「工程整洁」重述为「覆盖率路线的前置」**，并补上它未覆盖的能力层模块。
- R2（服务端 SQLite）与本设计**解耦**：L2 只需新增 `filter-rules` 路由，JSON 存储完全够用（规则集是读多写少的小数据）。**不要**把 R2 纳入本次重构范围（避免范围蔓延）。

## 3. 技术选型 ADR

### ADR-MOD-001：模块化粒度

**状态：Proposed。** 背景：需要为新能力层提供物理隔离，同时避免 `:feature:*` 过度拆分（`DESIGN-BUILD-FRAMEWORK.md` §4.3 已论证本项目规模下 feature 模块收益趋零）。

| 选项 | 优点 | 风险/成本 | 结论 |
|---|---|---|---|
| **A. 能力层模块**：`core:common` / `core:engine` / `capability:l1`(现 `service/`) / `capability:l2vpn` / `capability:l4notify` + `:app` 装配 | 每个能力层是独立模块，**权限面与信任边界物理隔离**；可独立开关/审计/回滚；新能力层有明确落点 | 需移动包路径，契约测试与 `ProjectStructureTest` 同步（已知坑位见 `DESIGN-BUILD-FRAMEWORK.md` §3.4）；模块数增至 ~6 | **推荐** |
| B. 推倒重写为多模块（含 KMP 跨平台） | 理论上更"干净" | 作废 `ArchitectureBoundaryTest` 等 30+ 契约测试、多 Agent 治理与刚收敛的发布链路；**不产生任何新覆盖能力**；触发判据 1 未成立 | **不采纳** |
| C. 保持单模块，新增能力层直接塞进 `:app` 新包 | 零迁移成本 | 信任边界靠纪律维持；L4 通知权限面与 L1 无障碍面同模块，撤销/审计无法编译期强制 | **不采纳**（与产品承诺冲突） |
| D. 仅做 `:core:engine` 拆分（R1 原案） | 风险最低 | 只解决工程整洁，**不解决能力层落点**；L2/L4 仍无隔离位置 | 作为 Route A 的**第一步**采纳 |

> **为何不是 `:feature:*`**：feature 模块的收益是「按需资源收缩 + 并行开发隔离」，在本项目（单 Activity + 4 屏）趋零；而**能力层模块**的收益是「权限面隔离 + 可独立回滚」，这恰好是覆盖率路线需要的。两者维度不同，不冲突。

### ADR-MOD-002：L2 的模块形态

**状态：Proposed。**（形态决策见 [DESIGN-ADS-FILTERING.md](DESIGN-ADS-FILTERING.md) ADR-ADS-001，此处只定**工程边界**。）

- L2 必须是**独立 Android library 模块** `capability:l2vpn`，**不得**被 `:app` 之外的模块依赖，也**不得**依赖 `capability:l1`。
- 理由：L2 持有 `VpnService`（设备级网络控制），与 L1 的「点击已展示按钮」是完全不同的风险等级。物理隔离让「L2 默认关闭、可一键卸载式停用」在代码层面可见。
- **接口反向**：`:app` 依赖 `capability:l2vpn` 的**接口**（`interface FilterEngine { fun start(); fun stop(); fun status(): FilterStatus }`），实现类不出模块——保证 `:app` 无法绕过开关直接启停。

### ADR-MOD-003：服务端是否同期重构

**状态：Accepted（不重构）。**

- L2 只需新增 `GET /api/v1/filter-rules`，复用既有鉴权/限频/ETag 基建（`ROADMAP-ADS.md` §5 Phase 2 已定）。
- 现有 `store.ts` 的 JSON 分片 + `tmp+rename` 原子写在**单进程、读多写少**场景下够用；`bun:sqlite`（R2）是规模问题，不是覆盖率问题。
- **决策**：服务端本次只做**一个路由的增量**，不做架构改动。避免「覆盖率重构」与「存储重构」两件事互相阻塞。

### ADR-MOD-004：重构与能力交付的排期耦合

**状态：Proposed。**

- **不搞「先重构半年再上能力」**：路由 A 的核心是**每一层能力都自带其模块边界**——即 `capability:l2vpn` 模块**随 L2 功能一起落地**，而不是先建空模块。
- 顺序：`core:engine` 拆分（纯工程，无用户可见变化）→ L2 落地时同时建 `capability:l2vpn` → L4 落地时同时建 `capability:l4notify` → `capability:l1` 重命名（可选，最后做）。
- 这样每一步都独立可验收、独立可回滚，且**重构从不阻塞覆盖率**。

## 4. 目标模块结构

```text
client/
├── app/                          # 应用装配层：MainActivity + Navigation + AppContainer + 权限中心
│   └── 依赖：全部 capability:* + core:*（唯一 DI 装配点）
├── core/
│   ├── common/                   # Kotlin JVM：AppEvents / AppExecutors / Clock / LogRing
│   └── engine/                   # Kotlin JVM：SkipRuleEngine / RuleSet / AdNode / SafetyGuard / selector
│       └── 约束：零 Android 依赖（实测已成立），编译器强制
├── capability/
│   ├── l1-accessibility/         # Android library：service/（SkipAdService + FrameworkAdNode + NodeSnapshot）
│   │   └── 权限面：BIND_ACCESSIBILITY_SERVICE（现状）
│   ├── l2-vpn/                   # Android library（随 L2 落地创建）
│   │   └── 权限面：BIND_VPN_SERVICE（新）· 对外仅暴露接口
│   └── l4-notify/                # Android library（随 L4 落地创建）
│       └── 权限面：BIND_NOTIFICATION_LISTENER_SERVICE（新，major）
├── data/                         # Android library：Prefs / RulesRepository / StatsRepository / SettingsRepository
├── net/                          # Android library：SyncClient
├── device/                       # Android library：VendorKeepAlive / QuickTileLogic / SkipTileService
└── sync/                         # Android library：SyncJobService
```

**依赖方向（编译器强制，取代/补强 §2.1 的扫描）**：

```text
app  ──▶ capability:l1 / l2 / l4 ──▶ core:engine / core:common
  │                                     ▲
  ├──▶ data ──▶ core:engine ────────────┘
  ├──▶ net ──▶ data
  ├──▶ device ──▶ core:common
  └──▶ sync ──▶ data / core:common

禁用：capability:* ←→ capability:*（任意两个能力层互不依赖）
       capability:* ──▶ app
       core:* ──▶ 任何上层
```

> **关键新约束（本次新增）**：**能力层之间禁止互相依赖**。L2 不得 import L1，L4 不得 import L1/L2。这条在单模块下无法强制（都在 `:app` 里，import 随便写），拆模块后由 Gradle `dependencies` 直接拒绝。它保证了「某个能力层出问题可以独立停用而不影响其他层」。

## 5. 迁移顺序（每步独立可验证、可回滚）

| 步 | 动作 | 契约测试先行 | 回滚 |
|---|---|---|---|
| **S1** | 拆 `:core:common`（`core/` 迁出 + 测试迁出 + 声明 `kotlinx-coroutines`）；建 `AdSkipJvmPlugin` 约定插件 | `ArchitectureBoundaryTest.core` 规则保留 + 新增模块级断言（失败测试先行） | 单步 `git revert` |
| **S2** | 拆 `:core:engine`（`engine/` 迁出，零 Android 依赖）；`ArchitectureBoundaryTest.engine` 从「扫 import」升级为「编译器直接拒绝」 | 同上，`SelectorContractTest` 随迁 | 单步 `git revert` |
| **S3** | 拆 `:data` / `:net` / `:sync` / `:device` 为 Android library（`data` 含 `SharedPreferences`，必须是 library 而非 JVM） | `RulesRepositoryTest` / `StatsRepositoryTest` / `SyncClientTest` 随迁 | 按步 `git revert` |
| **S4** | 重命名 `service/` → `capability:l1-accessibility`，迁 `SkipAdService` + `FrameworkAdNode` + `NodeSnapshot` | `ServiceIntentContractTest` / `AccessibilityStatusContractTest` 随迁 | 单步 `git revert` |
| **S5** | L2 落地时建 `capability:l2vpn`（含接口 + `VpnService` 实现 + 权限声明），`:app` 只依赖接口 | `FilterEngineContractTest`（新）+ `ProjectStructureTest` 白名单更新 | 功能开关关闭即回滚（模块可留） |
| **S6** | L4 落地时建 `capability:l4notify` | `NotifyFilterContractTest`（新）+ `ManifestContractTest` 扩展 | 同上 |

- **S1–S4 是纯工程重构**：用户可见行为**零变化**，验收口径 = 门禁全绿且测试数量不减。
- **S5–S6 与能力交付同步**（ADR-MOD-004），不做空模块。
- 每步都要**移动对应测试**并在该模块内声明 `junit` 依赖，否则会出现「源码迁走、测试还引用旧包」的半迁移态（`DESIGN-BUILD-FRAMEWORK.md` §4.2 已警示）。

## 6. 契约测试清单（合并前必须全绿）

| 测试 | 位置 | 断言 |
|---|---|---|
| 模块边界 | `ArchitectureBoundaryTest`（升级） | 模块级 `dependencies` 断言 + 包级扫描双重保险；**能力层互不依赖**为新增强断言 |
| 工程结构 | `ProjectStructureTest`（扩展） | `ALLOWED_ROOT_DIRS` 增加 `capability/`（或按 `client/capability/` 归入 `client/` 布局）；`settings.gradle.kts` 双向一致 |
| 清单契约 | `ManifestContractTest`（扩展） | L2/L4 权限声明与 `<queries>` 对齐；L2 权限在未授权时的 fail-closed 行为 |
| 引擎纯度 | 新增 `EnginePurityTest` | `core:engine` 模块 `.kt` 零 `android.*` import（编译期已强制，此处留编译期外的兜底断言） |
| L2 接口 | `FilterEngineContractTest`（新） | 未授权 → `start()` 拒绝；`stop()` 后系统网络恢复；接口不出模块 |
| 回归 | 既有 34 个测试类全绿 | `SelectorContractTest` / `RulesRepositoryTest` / `SyncClientTest` 等迁移后不减量 |

门禁（按 [AGENTS.md](../../AGENTS.md)）：`cd client && ./gradlew ktlintCheck`、`assembleDebug`、`testDebugUnitTest`；服务端新增 `filter-rules` 路由时 `bun test` + `bun run typecheck`。

## 7. 风险与回滚

| 风险 | 等级 | 缓解 | 回滚 |
|---|---|---|---|
| 拆模块移动包路径 → 契约测试与 `ProjectStructureTest` 同时红 | 中 | 每步一个提交、逐步跑全量单测；`ProjectStructureTest` 白名单**先改**（契约先行） | 按步 `git revert`，步骤间无耦合 |
| 签名/versionCode 逻辑漂移（`local.properties` 相对路径） | 中 | 保留在 `:app`，或在约定插件中用 `rootProject.file(...)` 并验证四种签名分支（`DESIGN-BUILD-FRAMEWORK.md` §3.4 坑 5） | 单提交 revert |
| `configuration-cache` 与配置期读文件冲突 | 中 | 最后单独提交验证；冲突则改 `providers.fileContents` 惰性读取，**不要**直接删该开关 | revert 该提交 |
| 能力层互不依赖导致共享逻辑重复 | 低 | 共享逻辑上移到 `core:common`；若确实需要跨层协作，由 `:app` 编排（依赖倒置） | 架构评审 |
| 拆模块期间与并发 Agent 冲突 | 中 | 按 [AGENT-WORKFLOW.md](../development/AGENT-WORKFLOW.md) 独占 `client/` 结构性路径，其他会话避免并行改包声明 | 串行化 |
| **重构完成后覆盖率仍未提升**（最大风险） | 高 | 本设计**明确把模块化定位为覆盖率的使能器而非结果**（§1）；S5/S6 与能力交付绑定（ADR-MOD-004），杜绝「重构半年、能力未动」 | 该判据本身即质检门 |

## 8. 验收（对「覆盖率不足」的闭环）

重构本身不产生覆盖率数字，因此验收分两层：

1. **工程层（S1–S4）**：`ktlintCheck` + `assembleDebug` + `testDebugUnitTest` 全绿；测试数量不减；`ArchitectureBoundaryTest` 能在拆分后拦截跨模块反向 import；用户可见行为零变化。
2. **能力层（S5–S6，即覆盖率闭环）**：
   - L2：Top 20 App 的 Banner/信息流素材拦截率 **≥70%**，正常业务域名**零误伤**（`ROADMAP-ADS.md` §5 Phase 2 口径）；
   - L4：通知过滤误杀率 **<1%**，提供手动恢复入口（同 §5 Phase 4）；
   - **前置**：L2/L4 启动前必须过 Phase 0 合规评审（[DESIGN-ADS-FILTERING.md](DESIGN-ADS-FILTERING.md) §9），未获批不得进入发布分支。

> **诚实提示**：覆盖率有物理上限。Banner/信息流属 L2 可解，但程序化 RTB、音频、可试玩广告技术上不可拦（`ROADMAP-ADS.md` §9）。本设计**不承诺**任何覆盖率数字，只保证「让 45% 以上的部分成为工程上可实现、可交付、可回滚的」。

## 9. 相关文档

- [ARCHITECTURE.md](../architecture/ARCHITECTURE.md)（分层职责 §2 / 边界契约 §2.1 / 目录契约 §2.2）
- [ARCHITECTURE-DIRECTORY-STRUCTURE.md](../architecture/ARCHITECTURE-DIRECTORY-STRUCTURE.md)（完整目录树单点事实源）
- [DESIGN-ARCH-EVOLUTION.md](DESIGN-ARCH-EVOLUTION.md)（R1–R3 演进计划；本设计为其 R1 的动机重述与范围扩展）
- [DESIGN-BUILD-FRAMEWORK.md](DESIGN-BUILD-FRAMEWORK.md)（阶段 C 拆分蓝图与已知坑位；本设计直接采纳）
- [DESIGN-ADS-FILTERING.md](DESIGN-ADS-FILTERING.md)（L2/L3/L4 能力形态与 Phase 0 合规门禁）
- [ROADMAP-ADS.md](ROADMAP-ADS.md)（覆盖矩阵 §4 / 里程碑 §6 / 公示口径 §9）
- [AGENT-WORKFLOW.md](../development/AGENT-WORKFLOW.md)（多 Agent 隔离与交接）
