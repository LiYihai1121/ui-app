---
name: architecture-boundary-guard
description: Contract-test-driven enforcement for cross-module, cross-package and cross-client/server boundary changes, plus root directory structure governance. Triggers on boundary/contract/structure/new-root-entry/protocol tasks; not for changes fully contained in one module with no boundary impact.
metadata:
  audience: all-contributors
  workflow: feature-branch
---

## 规范唯一事实源（此处不复述规范）

| 关注点 | 事实源 |
| --- | --- |
| 契约先行原则（先写失败测试再写实现） | [AGENTS.md](../../AGENTS.md)「多 Agent 协作」 |
| 边界契约（`ui/` 禁 import `service/` 等） | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 2.1 节 |
| 目录结构契约与根级白名单 | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 2.2 节 |
| 测试地图（哪些契约由哪个测试固化） | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 10 节 |
| 项目结构与根级条目 | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 11 节；`ProjectStructureTest` |
| 文档地图登记义务 | [docs/README.md](../../docs/README.md) |

> 本文件**刻意不复制**边界规则与白名单清单：它们由守护测试与 ARCHITECTURE 文档双源维护，复制第三份必然漂移。

## 契约测试地图（改动命中即先改测试）

| 守护的边界 | 测试 |
| --- | --- |
| 分层 import 边界 | `ArchitectureBoundaryTest` |
| Manifest 声明（组件/权限/`<queries>`） | `ManifestContractTest` |
| 根目录与模块布局 | `ProjectStructureTest` |
| 仓库卫生（分支前缀/忽略规则/文档登记） | `RepoHygieneTest` |
| intent 字面量 ↔ 常量一致 | `ServiceIntentContractTest` |
| 双端协议夹具一致 | `SelectorContractTest`（客户端）↔ server `utils/validate.ts` |

## 开工前自检

1. 这次改动是否跨包/跨端/动 manifest/动根目录？**是** → 先写会失败的契约测试，让门禁红起来，再写实现（契约先行）；
2. 新增仓库根级条目 → 先更新 `ProjectStructureTest` 白名单并登记 [docs/README.md](../../docs/README.md) 文档地图，PR 说明理由；
3. 多 Agent 并行时，在认领板登记要动的路径（细则见 [AGENT-WORKFLOW.md](../../docs/development/AGENT-WORKFLOW.md)）。

## 提交前自检

1. 命中地图的契约测试全绿，且是**因为实现正确**而绿、不是因测试被放宽——放宽契约必须在 PR 中单独说明理由；
2. 协议/数据形态变更同步 [API.md](../../docs/api/API.md) 与 [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md)；
3. 门禁命令全集见 [AGENTS.md](../../AGENTS.md)「验证与合并」——自行跑通再提交，不推给 CI。
