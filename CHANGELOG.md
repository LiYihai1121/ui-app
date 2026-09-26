# 变更日志

本文件记录面向用户和运维的版本变更。版本号遵循 Semantic Versioning，正式版本标签以 Git 中的 `vX.Y.Z` 为准。

版本与提交的完整对应关系见 [发布历史与提交链路](docs/planning/RELEASE-HISTORY.md)。

## 目录

- [未发布](#未发布)
- [3.0.2](#302---2026-09-04)
- [3.0.1](#301---2026-09-04)
- [3.0.0](#300---2026-09-04)
- [2.2.0](#220---2026-08-24)
- [2.1.0](#210---2026-08-24)
- [2.0.0](#200---2026-08-24)

## 未发布

### 3.0.3（待发版）

- 引擎内核新增类 CSS 选择器第三通道（`engine/selector/`：AST / 解析器 / 匹配器，纯 JVM、零第三方依赖）。
- 节点通道序变为 ① 选择器 → ② 文本 → ③ ViewID；`AdNode` 增加 `parent` 与 `previousSibling()`，`RuleSet` 增加 `selectors`（`isEmpty` 计入）。
- JVM 单测 44 → 121 项；失败用例不抛异常（解析失败即丢弃该条规则，fail-safe）。
- 本版本为纯内核增量：服务端尚未下发选择器规则，用户可见行为与 3.0.2 一致；停发选择器字段即可回退 v1 行为，无数据迁移。

### 路线调整（2026-09-26）

- 原规划的 `3.1.0`「L1 引擎 + 快照工具 + Top 30 规则」大礼包里程碑**已剥离**，改为增量发版：
  `3.0.3`（引擎内核）→ `3.1.0`（协议 v2 + 点击校验）→ `3.2.0`（快照工具）→ `3.3.0`（Top 30 规则与真机验收）→ `3.4.0` / `3.5.0` / `4.0.0`（L2 / L3 / L4）。
- 详见 [docs/planning/ROADMAP.md](docs/planning/ROADMAP.md) 与 [docs/planning/ROADMAP-ADS.md](docs/planning/ROADMAP-ADS.md)；技术方案与步骤划分见 [docs/planning/DESIGN-PHASE1-SELECTOR.md](docs/planning/DESIGN-PHASE1-SELECTOR.md)。

## [3.0.2] - 2026-09-04

### Fixed

- 修正正式发布标签指向旧提交的问题，确保 Release 从合并后的 `main` 构建。

## [3.0.1] - 2026-09-04

### Fixed

- 修正 Release 工作流使用 Debug APK、版本校验缺失和正式标签冲突后的发布路径。
- 发布前校验 Android 与服务端版本一致，并生成 Release APK 的 SHA-256 校验和。

## [3.0.0] - 2026-09-04

### Added

- Android 客户端迁移至 Jetpack Compose + MVVM。
- 服务端迁移至 Bun + TypeScript，并按 API、中间件、存储和工具分层。
- 增加 v1 规则同步、ETag/304、批量上报、健康检查和管理日志接口。
- 增加服务端单元测试与进程内冒烟测试，以及 Android JVM 单测。

### Changed

- 使用 JobScheduler 执行周期规则同步。
- 使用 SafetyGuard、载荷校验、鉴权、限流和 CORS 白名单加强安全性。

## [2.2.0] - 2026-08-24

- 完成安全加固、可测试性改造、规则缓存、合批落盘和架构增强。

## [2.1.0] - 2026-08-24

- 增加免打扰时段、定时同步、保活引导和日志导出。

## [2.0.0] - 2026-08-24

- 发布 Android 客户端、规则同步、统计上报和 Node.js 服务端基础能力。
