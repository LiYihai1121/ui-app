# 文档地图（docs/README.md）

本页是**文档的唯一入口**：说明每份文档的职责、阅读顺序，以及**规划类信息的唯一事实源**。原则：同一事实只在一处维护，其他文档只引用、不重述；本页登记全部文档，新增/删除/改名必须同步本页。

## 阅读顺序

| 我想… | 读 |
| --- | --- |
| 了解这是什么、怎么用 | [../README.md](../README.md) |
| 了解架构、模块职责、协议与安全模型 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 对接服务端接口 | [API.md](API.md) |
| 知道接下来做什么、按什么顺序做 | [ROADMAP.md](ROADMAP.md) → [ROADMAP-ADS.md](ROADMAP-ADS.md) |
| 看某项能力的技术设计与实施步骤 | [DESIGN-PHASE1-SELECTOR.md](DESIGN-PHASE1-SELECTOR.md) |
| 配置本机开发环境 | [DEV-ENVIRONMENT.md](DEV-ENVIRONMENT.md) |
| 查某版本发布到哪个提交、哪个 tag | [RELEASE-HISTORY.md](RELEASE-HISTORY.md) |
| 了解提交/分支/发布/回滚规范 | [CONTRIBUTING.md](../CONTRIBUTING.md)（执行摘要见 [AGENTS.md](../AGENTS.md)） |
| 查用户可见变更 | [CHANGELOG.md](../CHANGELOG.md) |
| 了解规则审核服务原型 | [../server_python/README.md](../server_python/README.md) |

## 规划事实源（Single Source of Truth）

| 主题 | 唯一事实源 | 其他文档的角色 |
| --- | --- | --- |
| 版本序列与能力意图（`3.0.3` → `3.1.0` → …） | [ROADMAP.md](ROADMAP.md) | 只引用版本号，不另立序列 |
| 专项里程碑、周次、出口条件 | [ROADMAP-ADS.md](ROADMAP-ADS.md)（第 5~6 节） | 引用 ROADMAP 的版本号 |
| L1 选择器技术方案与步骤 A–F | [DESIGN-PHASE1-SELECTOR.md](DESIGN-PHASE1-SELECTOR.md) | 引用版本号与里程碑编号 |
| 已发布版本链路（tag / 提交 / Release / 制品） | [RELEASE-HISTORY.md](RELEASE-HISTORY.md) | CHANGELOG 只记用户可见变更，不复述链路 |
| 协议与接口 | [API.md](API.md) + [ARCHITECTURE.md](ARCHITECTURE.md)（第 7 节） | DESIGN 只描述增量字段 |
| 客户端/服务端分层与模块职责 | [ARCHITECTURE.md](ARCHITECTURE.md) | README 只给目录树摘要 |
| 流程规范（分支/提交/门禁/发版） | [CONTRIBUTING.md](../CONTRIBUTING.md) | [AGENTS.md](../AGENTS.md) 为执行摘要 |

> **改规划的顺序**：先改 `ROADMAP.md`（版本意图）→ 再改 `ROADMAP-ADS.md` 与 `DESIGN-PHASE1-SELECTOR.md` 的版本归属 → 最后同步 `CHANGELOG.md` 与 `RELEASE-HISTORY.md` 的对应行。`docs/API.md`、`ARCHITECTURE.md` 涉及协议字段时一并更新。

## 当前规划全景（更新于 2026-09-26）

| 版本 | 状态 | 内容 | 详见 |
| --- | --- | --- | --- |
| `3.0.2` | 已发布（tag 已建，制品待补传） | v3.0 系列补丁 | [RELEASE-HISTORY.md](RELEASE-HISTORY.md) |
| `3.0.3` | **待发版**（代码已在 `main`：PR #12） | 选择器第三通道内核（步骤 A/B，无用户可见行为变化） | [DESIGN-PHASE1-SELECTOR.md](DESIGN-PHASE1-SELECTOR.md) 第 10 节 |
| `3.1.0` | 规划 | 协议 v2（`selectors` 字段）+ 点击结果校验与本地规则黑名单（步骤 C/D） | 同上 |
| `3.2.0` | 规划 | 节点快照工具 + 设置页入口（步骤 E） | 同上 |
| `3.3.0` | 规划 | Top 30 规则入库 + 真机回归 + 规则审核通道（步骤 F） | [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 6 节 |
| `3.4.0` / `3.5.0` / `4.0.0` | 规划 | L2 DNS 过滤 / L3 防摇一摇 / L4 通知与系统层 | [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 5~6 节 |

未排期能力见 [ROADMAP.md](ROADMAP.md) 的「候选池」；L2/L3 启动前必须先完成 Phase 0 合规评审（见 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节）。

## 规划中尚未创建的文档

| 文档 | 归属版本 | 说明 |
| --- | --- | --- |
| `docs/RULE-AUTHORING.md` | `3.3.0` | 规则编写指南：选择器语法、快照 → 规则流程、审核提交流程 |
| 覆盖度矩阵公示页 | `3.3.0` | 对用户公示各广告类型的覆盖口径（当前为 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 4/9 节的内部口径） |

## 维护规则

- 本页登记全部文档；文档改名或移动后，用 `git grep` 检索全仓引用并同步。
- 规划类文档（ROADMAP / ROADMAP-ADS / DESIGN）首部必须保留「状态 + 最后更新」行。
- 版本号、里程碑编号、测试计数等事实只在一处维护，其余位置只引用。
- 发布核对清单见 [CONTRIBUTING.md](../CONTRIBUTING.md) 的「每次发布完成后的最小核对清单」。
