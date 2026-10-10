---
name: planning-docs-guard
description: 规划文档登记与版本序列守卫技能。触发场景：改动 docs/planning/**、新增/改名/删除文档、版本号或里程碑变更、FOLLOW-UP 项落地。按 ROADMAP → ADS/DESIGN → CHANGELOG/RELEASE-HISTORY 顺序维护并在 docs/README.md 登记；不触发日常代码提交。
metadata:
  audience: planning-docs-contributors
  workflow: roadmap-first
---

# 规划文档守卫（planning-docs-guard）

> **规则指针（唯一事实源，本文不复制正文）**
> - 文档地图与登记义务：[docs/README.md](../../docs/README.md)
> - 版本序列事实源：[ROADMAP.md](../../docs/planning/ROADMAP.md)
> - 里程碑与出口条件：[ROADMAP-ADS.md](../../docs/planning/ROADMAP-ADS.md)
> - 技术步骤：[DESIGN-PHASE1-SELECTOR.md](../../docs/planning/DESIGN-PHASE1-SELECTOR.md)
> - 发布链路台账：[RELEASE-HISTORY.md](../../docs/planning/RELEASE-HISTORY.md)
> - 后续功能与待修复：[FOLLOW-UP.md](../../docs/planning/FOLLOW-UP.md)

## 自检动作

1. [ ] 改版本号 / 里程碑 → 先更新 [ROADMAP.md](../../docs/planning/ROADMAP.md)，再同步 [ROADMAP-ADS.md](../../docs/planning/ROADMAP-ADS.md) 与 DESIGN 文档的版本归属；
2. [ ] 新增 / 改名 / 删除文档 → 同步 [docs/README.md](../../docs/README.md) 文档地图（目录结构 + 阅读顺序 + 事实源表），并检查全文引用（含技能指针）；
3. [ ] 规划项落地 → 核查 [FOLLOW-UP.md](../../docs/planning/FOLLOW-UP.md)「待修复」清单：已实现的标注完成或移除，不保留陈旧状态；
4. [ ] 版本发布 → 回填 [RELEASE-HISTORY.md](../../docs/planning/RELEASE-HISTORY.md) 台账（版本 / 提交 / tag / 制品 / 回滚基线）；
5. [ ] 协议或数据形态变更 → 同步 [API.md](../../docs/api/API.md) 与 [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md)；
6. [ ] 用户可见变更 → [CHANGELOG.md](../../CHANGELOG.md) 补对应版本段。

## 自检：确认未出现以下情况

- 只改 CHANGELOG / RELEASE-HISTORY，ROADMAP 序列未同步（顺序颠倒）
- 新文档漏登记文档地图，或删除文档后引用悬空
- 用「待修复」状态描述已实现能力（陈旧真相）
