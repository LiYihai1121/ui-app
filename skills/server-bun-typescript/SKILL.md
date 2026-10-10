---
name: server-bun-typescript
description: Bun + TypeScript 服务端开发技能。触发场景：server/** 改动、rules.json 规则下发、协议字段或 schemaVersion 演进、双端契约夹具（selectors.contract.json ↔ 客户端）。零运行时依赖约束，validate.ts 校验必过；不触发纯客户端改动。
metadata:
  audience: server-contributors
  workflow: contract-first
---

# Bun + TypeScript 服务端（server-bun-typescript）

> **规则指针（唯一事实源，本文不复制正文）**
> - 服务端结构与入口：`server.ts`、`src/api/`、`src/middleware/`、`src/storage/`、`src/utils/`
> - 协议契约：[API.md](../../docs/api/API.md)
> - 双端契约夹具：`server/test/fixtures/selectors.contract.json` ↔ 客户端 `SelectorContractTest`
> - 门禁命令：[AGENTS.md](../../AGENTS.md)「验证与合并」（`cd server && bun test`、`bun run typecheck`）
> - 运行约束：`server/package.json`（Bun ≥ 1.1，零运行时依赖）

## 开工前自检

1. 规则 schema 变更：先更新 `src/types/rules.ts` + `src/utils/validate.ts` 校验函数 + 夹具，让校验测试先红；
2. 协议 / 数据形态变更：先改 `selectors.contract.json` 与客户端 `SelectorContractTest`（双端一致），再写实现；
3. 新路由：复用 `src/middleware/`（auth / rateLimit / accessLog）与 `src/api/index.ts` 注册模式，不另起炉灶；
4. 明确零运行时依赖：新增能力优先用标准库 / Bun 内建，npm 依赖须在 PR 说明理由并过评审。

## 自检动作

1. [ ] 字段校验集中在 `src/utils/validate.ts`，路由内不散落「宽松绕过」的校验分支；
2. [ ] `src/types/rules.ts` 与夹具字段一一对应，不出现服务端与客户端各执一词的字段名；
3. [ ] 类型安全：`bun run typecheck` 通过；不用 `any` / 类型断言抹掉真实类型；
4. [ ] 测试通过：`bun test` 全绿（smoke / unit / selectorSimulator / selectors.contract）；
5. [ ] 统计 / 健康 / 规则 API 的响应结构同步登记 [API.md](../../docs/api/API.md)。

## 自检：确认未出现以下情况

- 向 `server/` 引入未评审的 npm 运行时依赖（破坏零依赖约束）
- 客户端夹具与服务端夹具不一致（第二份真相）
- 协议字段改动却未同步 [API.md](../../docs/api/API.md) 与 [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md)
