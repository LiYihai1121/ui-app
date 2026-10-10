---
name: accessibility-engine-guard
description: Hard guardrails for L1 accessibility engine work - selector matching, SafetyGuard package blacklist, click-result validation and service receiver safety. Triggers on engine/SafetyGuard/SkipAdService/selector/rule-schema tasks; not for UI, network or server changes.
metadata:
  audience: android-engine-contributors
  workflow: feature-branch
---

## 规范唯一事实源（此处不复述规范）

| 关注点 | 事实源 |
| --- | --- |
| 选择器语法子集、文法与安全/复杂度硬限制 | [DESIGN-PHASE1-SELECTOR.md](../../docs/planning/DESIGN-PHASE1-SELECTOR.md) 第 3 节 |
| 匹配算法、性能预算、兄弟节点已知风险 | 同上第 5 节 |
| 点击结果校验（防劫持回退） | 同上第 7 节 |
| 协议 v2（`selectors` 字段）与服务端校验 | 同上第 6 节；[API.md](../../docs/api/API.md) |
| 安全模型（黑名单、导出面） | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 5 节 |
| 引擎实现 | `engine/`（`SkipRuleEngine.kt`、`SafetyGuard.kt`、`selector/`）与 `service/SkipAdService.kt` |

> 本文件**刻意不复制**黑名单包名清单、语法 BNF、性能阈值数字：以源码与 DESIGN 文档为准。

## 硬护栏（改引擎前逐条确认）

1. **SafetyGuard 硬编码黑名单不可被云规则覆盖**——支付/银行/数字钥匙类整包拒绝（`isPackageDenied()` 先于关键词/选择器匹配）；任何「让云端规则更聪明」的改动都不得绕过它；
2. **点击结果校验回退**——点击后 500ms 内异常跳转/页面切换 → 回退并加入本地黑名单；新增点击入口必须接同一校验路径；
3. **选择器保持语法子集**（3~4 种关系运算符）——完整 DSL 是明确的非目标，勿「顺手」扩展文法；
4. **`SkipAdService` 动态接收器必须 `RECEIVER_NOT_EXPORTED`**，intent 字面量与常量一致性由契约测试固化。

## 提交前自检

1. 契约先行：跨端（协议/夹具）改动先写失败测试——`SelectorContractTest`（客户端）与 server `utils/validate.ts` 夹具**双端一致**；
2. 引擎改动同步 `SelectorParserTest` / `SelectorMatcherTest` / `SkipRuleEngineTest` / `SafetyGuardTest` / `ServiceIntentContractTest`；
3. 性能预算不回退：`findTarget` 预算见 DESIGN 第 5.2 节，新增回溯逻辑须给出不劣化证据；
4. 旧客户端兼容：`schemaVersion` 演进保持 v1 行为不变（ARCHITECTURE 第 7 节）。
