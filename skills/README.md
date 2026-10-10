# Agent 技能开发（skills/README.md）

> 状态：已生效；最后更新：2026-10-07。
> 适用范围：所有随仓库版本控制的 Agent 技能（`branch-guard`、`code-simplifier`、`software-development-full` 等）。
> 挂载方式：`kilo.json` 的 `skills.paths: ["./skills"]`；技能加载与权限见 [kilo.json](../kilo.json)。

本目录是**随仓库版本控制的 Agent 技能**的唯一存放处。技能不是「个人配置」：放进 `.kilo/` 等被
`.gitignore` 忽略的目录的技能无法入库，等于每个 Agent 各有一套技能集——那正是「第二份真相」的另一种
形态。因此所有技能放在 `skills/`，由 `kilo.json` 显式挂载，并由本文登记开发约定。

## 目录结构

```text
skills/
├── branch-guard/               自研技能：分支治理（规则指针 + 自检动作，不复述规则）
│   └── SKILL.md
├── code-simplifier/            第三方上游技能：代码简化（Apache-2.0，随包携带 LICENSE / NOTICE）
│   ├── SKILL.md
│   ├── README.md
│   ├── LICENSE / NOTICE.md
│   ├── docs/specs/             引入 Spec 与决策记录
│   ├── scripts/                校验脚本（validate.py）
│   └── tests/                  决策场景集
├── software-development-full/  通用完整版软件开发规则（全场景开发协议；来源与许可状态见 NOTICE.md，
│   │                         上游 LICENSE 未随附，对外分发前须补齐）
│   ├── SKILL.md
│   └── NOTICE.md
├── android-compose-design/     自研技能：Compose UI 规范（theme token / 共享组件 / UI 契约测试）
│   └── SKILL.md
├── accessibility-engine-guard/ 自研技能：L1 引擎硬护栏（选择器子集 / SafetyGuard / 点击校验）
│   └── SKILL.md
├── android-vpn-dns-filter/     自研技能：L2 DNS 过滤护栏（合规前置 / 默认关闭 / 只拦不改）
│   └── SKILL.md
├── vendor-rom-keepalive/       自研技能：厂商 ROM 保活与跳转（入口表 / 逐级降级 / queries 契约）
│   └── SKILL.md
└── architecture-boundary-guard/ 自研技能：架构边界与契约测试驱动（契约先行 / 测试地图）
    └── SKILL.md
```

## SKILL.md 格式

每个技能是一个 `skills/<skill-name>/SKILL.md`，frontmatter 必须包含：

```yaml
---
name: <小写连字符命名，如 branch-guard>
description: <触发描述：何时该被自动发现、何时不该误触发>
metadata:            # 可选；按需补充
  audience: ...
  workflow: ...
---
```

正文原则：

- **规则不复制**：技能只保留「指向规范文件的指针 + 自检动作」，**不复制**分支命名表、门禁命令等规则正文。
  复制出去的规则会静默漂移，成为第二份（往往是错的）真相。规则变更一律改规范文件（[CONTRIBUTING.md](../CONTRIBUTING.md)、
  [AGENTS.md](../AGENTS.md)、[AGENT-WORKFLOW.md](../docs/development/AGENT-WORKFLOW.md) 等），技能只跟随指针。
- **描述要防误触发**：description 写明适用场景与边界（例如审查类技能：只报告有证据的发现、不在未授权时改代码），
  避免无关任务误加载。

## 开发一个新技能

1. 从最新 `main` 创建 `feature/<id>-<slug>` 分支（遵循 [CONTRIBUTING.md](../CONTRIBUTING.md) 分支策略）；
2. 新建 `skills/<skill-name>/SKILL.md`，按上文格式编写 frontmatter 与正文；
3. 若技能需要额外材料（校验脚本、测试场景、spec），随技能目录一并提供，不预建无用的通用框架；
4. 第三方技能必须随包携带 `LICENSE` 与 `NOTICE.md`（来源与署名），遵守上游许可；
5. 提交前跑通相关门禁（`ktlintCheck` / `assembleDebug` / `testDebugUnitTest`，技能若带 Python 校验脚本则一并运行）；
6. 在 [docs/README.md](../docs/README.md) 的文档地图登记本目录与技能文档（目录结构 + 阅读顺序 + 事实源表）。

## 校验

- 自带 `scripts/validate.py` 的技能（如 `code-simplifier`）：改动 SKILL.md 或插件清单后必须运行，输出技能清单格式与
  唯一公开技能断言；
- 仓库门禁 `ProjectStructureTest` 将根目录 `skills/` 列入白名单（新增技能目录不需要改测试，但根级**新目录**仍需登记）；
- `RepoHygieneTest` 与文档地图要求：技能文档若落在 `docs/` 下必须登记；本 `skills/README.md` 已在 [docs/README.md](../docs/README.md) 登记。

## 与流程规范的关系

| 关注点 | 唯一事实源 |
| --- | --- |
| 分支 / 提交 / PR 门禁 / 版本发布 | [CONTRIBUTING.md](../CONTRIBUTING.md)（摘要见 [AGENTS.md](../AGENTS.md)） |
| 多 Agent 并行协作（隔离 / 所有权 / 契约 / 交接） | [AGENT-WORKFLOW.md](../docs/development/AGENT-WORKFLOW.md) |
| 本机工具链与命令 | [DEV-ENVIRONMENT.md](../docs/development/DEV-ENVIRONMENT.md) |
| 版本序列与开发路线 | [ROADMAP.md](../docs/planning/ROADMAP.md) + [ROADMAP-ADS.md](../docs/planning/ROADMAP-ADS.md) |

> 技能只做「提醒与自检」，不建立第二套流程或隐藏状态；需求、分支、提交、合并与交付仍由仓库既有流程控制。
