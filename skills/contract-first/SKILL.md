---
name: contract-first
description: 契约先行开发自检技能。触发场景：跨包/跨端边界改动、协议或数据形态变更、新增门禁。先写失败的契约测试再写实现；无边界的普通改动不触发。
metadata:
  audience: 开发执行者
  workflow: 定边界契约 → 失败测试 → 实现 → 门禁绿
---

# 契约先行（contract-first）

> **规则指针（唯一事实源，本文不复制正文）**
> - 契约先行与门禁清单：[AGENTS.md](../../AGENTS.md)「多 Agent 协作」「验证与合并」
> - 边界契约定义：[docs/architecture/ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 2.1
> - 协议同步要求：[docs/api/API.md](../../docs/api/API.md)

## 自检动作

1. [ ] 识别边界类型：跨包（ui / service / data / net / device / core / engine）、跨端（client / server）、协议/数据形态
2. [ ] 先写**失败的**契约测试（架构边界 / 清单契约 / 目录结构 / 仓库卫生 / 协议夹具），确认先红
3. [ ] 再写实现，让契约测试转绿
4. [ ] 协议或数据形态变更时同步 API.md 与 ARCHITECTURE.md
5. [ ] 跑相关最小测试；跨模块变更跑完整门禁

## 自检：确认未出现以下情况

- 先实现后补测试（倒因为果）
- 只改实现不更新契约（第二份真相）
- 无边界变化却强加契约（Speculative Generality）
