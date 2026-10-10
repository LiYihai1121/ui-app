---
name: android-vpn-dns-filter
description: Planning and implementation guardrails for the L2 network filtering layer (local VpnService DNS, filter-rules route). Triggers on L2/DNS/VPN/filter-rules/ad-domain tasks; must NOT drive implementation before the Phase 0 compliance review passes.
metadata:
  audience: android-network-contributors
  workflow: feature-branch
---

## 规范唯一事实源（此处不复述规范）

| 关注点 | 事实源 |
| --- | --- |
| 合规前提与发布前置条件（L2 未获批不进发布分支） | [ROADMAP-ADS.md](../../docs/planning/ROADMAP-ADS.md) 第 3 节 |
| Phase 2 方案 A/B、验收口径 | [ROADMAP-ADS.md](../../docs/planning/ROADMAP-ADS.md) 第 5 节 |
| 里程碑与出口条件（M2 / 边界解除扩展） | [ROADMAP-ADS.md](../../docs/planning/ROADMAP-ADS.md) 第 6、10 节 |
| 版本序列登记（L2 = 哪个版本号） | [ROADMAP.md](../../docs/planning/ROADMAP.md) |
| 规则下发协议与鉴权/限频基建 | [API.md](../../docs/api/API.md) |

## 启动前自检（不满足就停下，先走流程）

1. **Phase 0 合规评审获批了吗？** 未获批时本技能只用于完善方案与 ADR，不产出进入发布分支的代码；
2. 版本号已在 [ROADMAP.md](../../docs/planning/ROADMAP.md) 序列登记、技术选型 ADR 已定（方案 A 本地 VpnService DNS 免 Root / 方案 B 外接 AdGuard Home 规则聚合）；
3. 规则源采用成熟订阅集（AWAvenue 等）——**不自造轮子**自建域名清单。

## 实现期硬护栏

1. **默认关闭**：L2 过滤出厂不启用，用户显式开启；
2. **只拦不改**：仅拦截已知广告域名的网络请求，不改写、不解密任何内容；
3. **关停零开销**：VPN 关闭时不得有常驻轮询/流量成本；
4. **误伤回归**：正常业务域名零误伤依赖冒烟测试框架的回归集——新增拦截域名族必须先进回归集再上线；
5. 服务端 `GET /api/v1/filter-rules` 复用现有鉴权/限频/ETag 基建，不另起炉灶。

## 提交前自检

1. 契约先行：`filter-rules` 协议字段先补 [API.md](../../docs/api/API.md) 与双端契约夹具（失败测试），再写实现；
2. 验收口径按 ROADMAP-ADS 对应里程碑执行（拦截率与误伤指标以该文档为准，此处不复述数字）；
3. 门禁命令见 [AGENTS.md](../../AGENTS.md)「验证与合并」——自行跑通再提交。
