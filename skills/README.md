# Agent 技能开发（skills/README.md）

> 状态：已生效；最后更新：2026-10-11。
> 适用范围：所有随仓库版本控制的 Agent 技能（`branch-guard`、`code-simplifier`、`software-development-full`、
> `contract-first`、`release`、`security-audit`、`architecture-boundary-guard`、`accessibility-engine-guard`、
> `android-compose-design`、`android-vpn-dns-filter`、`vendor-rom-keepalive`、`network-security-guard`、
> `server-bun-typescript`、`planning-docs-guard` 等）。
> 挂载方式：`kilo.json` 的 `skills.paths: ["./skills"]`；技能加载与权限见 [kilo.json](../kilo.json)。

本目录是**随仓库版本控制的 Agent 技能**的唯一存放处。技能不是「个人配置」：放进 `.kilo/` 等被
`.gitignore` 忽略的目录的技能无法入库，等于每个 Agent 各有一套技能集——那正是「第二份真相」的另一种
形态。因此所有技能放在 `skills/`，由 `kilo.json` 显式挂载，并由本文登记开发约定。

## 目录结构

```text
skills/
├── accessibility-engine-guard/   自研技能：无障碍引擎硬护栏（选择器子集 / 黑名单 / 点击校验）
│   └── SKILL.md
├── android-compose-design/       自研技能：Compose UI 设计系统（theme token / 组件 / 动效）
│   └── SKILL.md
├── android-vpn-dns-filter/       自研技能：L2 网络过滤层（Phase 0 未获批不驱动实现）
│   └── SKILL.md
├── architecture-boundary-guard/  自研技能：边界契约测试地图（跨包 / 跨端 / 根目录）
│   └── SKILL.md
├── branch-guard/                 自研技能：分支治理（规则指针 + 自检动作，不复述规则）
│   └── SKILL.md
├── code-simplifier/              第三方上游技能：代码简化（Apache-2.0，随包携带 LICENSE / NOTICE）
│   ├── SKILL.md
│   ├── README.md
│   ├── LICENSE / NOTICE.md
│   ├── docs/specs/               引入 Spec 与决策记录
│   ├── scripts/                  校验脚本（validate.py）
│   └── tests/                    决策场景集
├── contract-first/               自研技能：契约先行（先红后绿的边界开发流程）
│   └── SKILL.md
├── network-security-guard/       自研技能：网络层安全护栏（TLS pinning / 签名 / HTTPS）
│   └── SKILL.md
├── planning-docs-guard/          自研技能：规划文档登记与版本序列守卫（ROADMAP → 台账）
│   └── SKILL.md
├── release/                      自研技能：发布与回滚（SemVer / tag / 发布链路自检）
│   └── SKILL.md
├── scripts/                      技能开发工具：new_skill.py（脚手架，生成合规骨架）
├── security-audit/               自研技能：安全审计（证据分级 / 残留风险，只报告不改码）
│   └── SKILL.md
├── server-bun-typescript/        自研技能：Bun+TS 服务端（零依赖 / validate.ts / 双端夹具）
│   └── SKILL.md
├── software-development-full/    通用完整版软件开发规则（全场景开发协议；来源与许可状态见 NOTICE.md，
│   │                             上游 LICENSE 未随附，对外分发前须补齐）
│   ├── SKILL.md
│   └── NOTICE.md
└── vendor-rom-keepalive/         自研技能：厂商保活与系统导航（只引导不代管）
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

## 技能生命周期

技能与 ROADMAP / ROADMAP-ADS 的能力域一一对应，随规划演进维护：

- **新建**：按下方「开发一个新技能」流程；新技能须有明确触发边界，不与既有技能重叠职责（重叠时互相引用指针而非复制清单）；
- **活跃**：指向的规范文件或守护测试变化时，同步更新 SKILL.md 指针；规则正文变更不改技能、只改规范文件；
- **休眠 / 移除**：对应规划域取消、暂停或已实现下线时，从本目录结构与 [docs/README.md](../docs/README.md) 文档地图同步移除，
  在 PR 中说明理由；已移除技能不进回收站保留（历史在 git 中可追溯）。


## 开发一个新技能

1. 从最新 `main` 创建 `feature/<id>-<slug>` 分支（遵循 [CONTRIBUTING.md](../CONTRIBUTING.md) 分支策略）；
2. 新建 `skills/<skill-name>/SKILL.md`，按上文格式编写 frontmatter 与正文（可先用 `python skills/scripts/new_skill.py <skill-name> "<触发描述>"` 生成合规骨架）；
3. 若技能需要额外材料（校验脚本、测试场景、spec），随技能目录一并提供，不预建无用的通用框架；
4. 第三方技能必须随包携带 `LICENSE` 与 `NOTICE.md`（来源与署名），遵守上游许可；
5. 提交前跑通相关门禁（`ktlintCheck` / `assembleDebug` / `testDebugUnitTest`，技能若带 Python 校验脚本则一并运行）；
6. 在 [docs/README.md](../docs/README.md) 的文档地图登记本目录与技能文档（目录结构 + 阅读顺序 + 事实源表）。

## 校验

- **`SkillContractTest`（`testDebugUnitTest` 门禁，自动运行）**：机器校验技能格式与登记——frontmatter 含 name/description（description ≥ 30 字符）、name 与目录名一致且为小写连字符、技能已登记进本目录结构、LICENSE 必配 NOTICE；违反即门禁判红；
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

