---
name: branch-guard
description: Enforce branch governance for every change - traceable branch, self-run gates, then PR into protected main. This skill only triggers and self-checks the rules; the rules themselves live in CONTRIBUTING.md.
metadata:
  audience: all-contributors
  workflow: github-flow
---

## 规则唯一事实源（此处不复述规则）

| 关注点 | 规范文件（唯一事实源） |
| --- | --- |
| 分支模型、提交格式、PR 门禁、版本发布 | [CONTRIBUTING.md](../../../CONTRIBUTING.md) |
| 日常执行摘要（分支/门禁/文档顺序） | [AGENTS.md](../../../AGENTS.md) |
| 并行多 Agent 协作（隔离/所有权/契约/交接） | [docs/development/AGENT-WORKFLOW.md](../../../docs/development/AGENT-WORKFLOW.md) |
| 本机工具链与命令 | [docs/development/DEV-ENVIRONMENT.md](../../../docs/development/DEV-ENVIRONMENT.md) |

> 本文件**刻意不复制**规则表格与命令清单：复制出去的规则会静默漂移，并成为第二份（往往是错的）真相。
> 新规则一律加到上表的规范文件里，这里只保留指针与自检动作。

## 开工前自检

1. `git status --short --branch` —— 工作区干净、位于可追踪分支
2. `git worktree list` —— 你在**已登记的 worktree** 内（含 `.git` 元数据）
3. 读 [AGENTS.md](../../../AGENTS.md)，从最新 `main` 创建 `type/<id>-<slug>` 分支
4. 并行协作时，另读 [AGENT-WORKFLOW.md](../../../docs/development/AGENT-WORKFLOW.md) 并在认领板登记路径

## 提交前自检

- 跑通 [AGENTS.md](../../../AGENTS.md)「验证与合并」小节列出的门禁命令（**自行执行**，不把验证推给 CI）
- 一个提交一件事；Conventional Commits；标题 ≤ 72 字符；正文说明原因、影响与验证方式

## 合并前自检

- PR 目标为 `main`（或当前 `release/*`），关联 Issue，自审栏填写完整
- Squash Merge，合并后删除源分支
- 绝不 push / force-push / amend / rebase 他人分支；不在 `main` 上直接提交
- 涉及安全、协议、数据、发布配置时请求领域负责人审查

## 会被构建拦下的硬规则

- 架构边界 → `ArchitectureBoundaryTest`
- 清单与入口表对齐（磁贴、`<queries>`） → `ManifestContractTest`
- 仓库卫生（忽略规则、无元数据仓库副本、文档登记、分支命名） → `RepoHygieneTest`

以上任一违反都会在 `testDebugUnitTest` 阶段失败，**不依赖代码评审才发现**。

