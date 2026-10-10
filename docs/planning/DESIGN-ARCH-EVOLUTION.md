# 架构演进计划（DESIGN-ARCH-EVOLUTION）

> 状态：待评审（方向已与项目所有者对齐，本文档为其正式化）；最后更新：2026-10-11。
> 本文档回答「前后端是否可以重新设计」：结论是**演进式重架构，不推倒重写**。给出客户端与服务端的技术选型、全盘重写的触发判据，以及 R1–R3 三个阶段的边界与验收口径。版本号以 [ROADMAP.md](ROADMAP.md) 为准，里程碑与 L1–L5 能力分层见 [ROADMAP-ADS.md](ROADMAP-ADS.md)。

## 1. 背景与结论

v3.4.0 刚完成 monorepo 解耦、`applicationId` 迁移与安全加固批次（M1/M2），客户端（Kotlin 2.x + Jetpack Compose M3 + MVVM 单 Activity）与服务端（Bun + TypeScript 零运行时依赖）均为当前主流栈且测试全绿（client 248 / server 87）。**推倒重写没有任何一项收益能覆盖其成本**——重写作废契约测试体系、多 Agent 治理机制与刚收敛的发布链路，且不产生新用户价值。

因此本计划的定位是**演进式重架构**：只在存在可证收益的结构性债务点上动手，其余保持现有栈。全盘重写仅在第 2 节判据触发时重新评估。

## 2. 全盘重写的触发判据（防止过度设计）

出现以下**任一**情况时，重新评估换栈并另立 DESIGN 文档；未触发前本计划的「保持」决策不许被顺手推翻：

1. 需要 iOS / 桌面客户端（→ 评估 Kotlin Multiplatform 共享 `core:` 模块）；
2. 规则引擎需要沙箱执行第三方代码（→ 引擎层隔离架构）；
3. 服务端需要多实例水平扩展（→ R2 的 SQLite 迁移是其前置）；
4. Bun 运行时出现生态级风险（→ 评估 Node LTS 迁移，HTTP/存储层解耦后成本可控）。

## 3. 客户端技术选型

| 领域 | 现状 | 候选 | 决策 | 理由 |
| --- | --- | --- | --- | --- |
| 模块化 | 单 `:app` 模块 | `core:common` / `core:engine` 拆分（DESIGN-BUILD-FRAMEWORK 阶段 C 原案） | **R1 执行** | 边界从 import 约定升级为编译器强制；`ArchitectureBoundaryTest` 随之升级；构建并行 |
| DI | 手写 `AppContainer` | Hilt / Koin | **保持** | 4 屏规模手写足够；模块数 > 5 再引入 Hilt，避免为小项目付注解处理器成本 |
| 加密存储 | `security-crypto` 1.1.0-alpha07 | DataStore + Keystore 自管封装 | **R1 做 spike，结论回填本文档** | 上游停在 alpha 是真实供应链风险；迁移必须兼容既有密文与幂等迁移测试（`PrefsTest`） |
| 网络 | OkHttp 4.12 + CertificatePinner | — | **保持** | v3.3.0 刚迁移（MockWebServer 回归齐备），无重做理由 |
| 异步 | Coroutines + `AppExecutors` | — | **保持** | 线程模型见 ARCHITECTURE 第 6 节，稳定 |
| SDK | minSdk 26 / targetSdk 35 | — | **保持** | targetSdk 35 的归因决策见 `build-logic` 约定插件注释 |
| UI | Compose M3（BOM 2026.08） | — | **保持** | 两轮重设计刚落地（v3.2 / #100），设计系统（theme token + 共享组件）已收敛 |

## 4. 服务端技术选型

| 领域 | 现状 | 候选 | 决策 | 理由 |
| --- | --- | --- | --- | --- |
| 运行时 | Bun + TS，零外部依赖 | Node LTS / Deno | **保持** | 零依赖是发布卖点；判据 4 触发前不动 |
| 统计/规则存储 | JSON 分天分片文件 + 轮转备份 | **`bun:sqlite`（Bun 内置，零新依赖）** | **R2 执行** | 分片文件的原子性与查询能力是真实短板；内置模块不破坏零依赖承诺；`rotateStatsBackup` 备份策略沿用并适配 |
| Web 层 | 手写 `Bun.serve` 路由 | Hono / Fastify | **保持** | 路由个位数，引入框架是负资产；中间件语义已自建（限频/鉴权/CORS） |
| 部署 | 单进程裸跑 | Dockerfile（吸收 PR #63 方案） | **R3 执行** | 与 `/metrics` 同批；部署单元化后回滚点更清晰 |
| 可观测 | JSONL 结构化日志 | OpenTelemetry | **延后（P3）** | FOLLOW-UP 已列；等多实例需求出现再做，单实例不上 APM |
| 管理后台 | 服务端内置网页 | 独立前端工程 | **保持** | 单文件页面服务于运维场景，独立前端是过度设计 |

## 5. 分阶段计划（R1–R3）

版本承载遵循「不与 L2/L3/L4 能力里程碑抢道」：R1 挂在 M1d 之后的下一个可用 minor；R2 与 M2（L2 DNS 过滤）同期落地（其 `filter-rules` 路由直接受益于 SQLite 查询能力）；R3 为独立工程批次。每个阶段走「ROADMAP 登记 → 契约测试先行 → 实现 → ARCHITECTURE.md 同步」的既有顺序。

### R1 — 客户端多模块化 + 存储评估

- **内容**：按 DESIGN-BUILD-FRAMEWORK 阶段 C 拆出 `core:common`（AppExecutors/Clock/LogRing）与 `core:engine`（engine/ + service/ 的引擎部分）；`SecurityCrypto` 迁移 spike（一页结论回填第 3 节表格）。
- **前置**：DESIGN-BUILD-FRAMEWORK 状态从「待排期」改为「已排期」；`ArchitectureBoundaryTest` 升级为模块边界断言（失败测试先行）。
- **验收**：`ktlintCheck` + `assembleDebug` + `testDebugUnitTest` 全绿且测试数量不减；`ArchitectureBoundaryTest` 能在拆分后拦截跨模块反向 import；用户可见行为零变化。
- **风险**：拆分会移动既有文件的包路径，契约测试与 `ProjectStructureTest` 需同步——已知坑位与分步回滚策略见 DESIGN-BUILD-FRAMEWORK。

### R2 — 服务端存储迁移 JSON → `bun:sqlite`

- **内容**：统计分片与规则存储迁移至单 SQLite 库（WAL 模式）；`rotateStatsBackup` 备份轮转适配（备份对象从分片文件变为库文件 + 保留 5 份策略不变）；对外 API 响应形状**零变化**（契约夹具双端一致）。
- **前置**：新增迁移设计小节（本文件第 4 节扩展）；`server/src/storage/store.ts` 接口不变、实现替换——`bun test` 87 项中的存储相关用例先行改写为失败态。
- **验收**：`bun test` + `bun run typecheck` 全绿；API 响应逐字节等价（以现有契约测试为准）；旧 JSON 数据提供一次性迁移脚本；`backupStamp` 命名机制保留。
- **风险**：并发写（统计上报 + 规则轮转）——SQLite WAL + 单写者队列；迁移失败回滚 = 保留 JSON 路径一个版本周期。

### R3 — 部署与可观测批次

- **内容**：Dockerfile（吸收 PR #63 的方案并关闭该 PR）+ `/metrics` 端点（文本格式即可，不上 OpenTelemetry）。
- **验收**：容器内 `bun test` 通过；`/metrics` 暴露请求量/限频命中/规则条数三类指标；文档同步 DEV-ENVIRONMENT.md 部署节。
- **风险**：低；不改变应用运行时行为。

## 6. 治理约束

- **契约先行**：R1 的模块边界、R2 的存储接口，均以「先写失败测试」开工（AGENTS.md「多 Agent 协作」）；放宽任何契约必须在 PR 单独说明理由。
- **文档顺序**：本计划的状态变更按「ROADMAP → 本文档 → CHANGELOG（发布时）」推进；涉及协议字段时同步 API.md 与 ARCHITECTURE.md。
- **多 Agent**：R1 拆分涉及 `client/` 大范围文件移动，执行期间在认领板独占 `client/` 结构性路径，其他会话避免并行改 `client/app/src` 的包声明。
- **公示约束**：R2 的「API 响应零变化」是对外承诺，任何响应形状变更都必须走协议版本策略（ARCHITECTURE 第 7 节），不得随存储迁移顺手改。

## 7. 与既有规划的关系

- 本计划**不改变** ROADMAP-ADS 的 L1–L5 能力里程碑（M1–M5/M6），R1–R3 是工程承载侧的演进；
- R2 与 M2 的合并点：L2 的 `filter-rules` 路由直接建立在 SQLite 查询之上，先 R2 后 L2 可避免路由在 JSON 存储上实现两次；
- FOLLOW-UP.md 中「端到端集成测试」「备份压缩/增量」两项在 R2 完成后自动获得更优解载体，届时更新其条目。
