# 文档地图（docs/README.md）

本页是**文档的唯一入口**：说明每份文档的职责、阅读顺序，以及**规划类信息的唯一事实源**。原则：同一事实只在一处维护，其他文档只引用、不重述；本页登记全部文档，新增/删除/改名必须同步本页。

## 目录结构

```text
docs/
├── README.md          本页：文档地图与事实源声明（唯一入口）
├── api/               对外接口与协议契约
│   └── API.md
├── architecture/      系统架构与模块职责
│   └── ARCHITECTURE.md
├── development/       开发环境与工具链
│   ├── DEV-ENVIRONMENT.md
│   └── AGENT-WORKFLOW.md         多 Agent 协作：隔离 / 所有权 / 契约 / 交接
├── planning/          规划与发布记录
│   ├── ROADMAP.md                 版本序列（唯一事实源）
│   ├── ROADMAP-ADS.md             里程碑 / 周次 / 出口条件
│   ├── DESIGN-PHASE1-SELECTOR.md  L1 技术设计与步骤 A–F
│   ├── DESIGN-ADS-FILTERING.md    L2/L3/L4 分层治理技术决策与边界（评审稿）
│   ├── DESIGN-BUILD-FRAMEWORK.md  构建框架工程化（version catalog / build-logic / 模块拆分）
│   ├── FOLLOW-UP.md               安全加固与技术补充计划（已修复清单 / 待修复 / 里程碑）
│   └── RELEASE-HISTORY.md         发布链路（tag / 提交 / Release / 制品）
└── diagrams/          架构图（adskip-architecture.json 源 + .html 渲染）
```

> 根级不在 `docs/` 下的文档：`skills/README.md` 是**随仓库版本控制的 Agent 技能**的开发规范（`skills/` 目录挂载于根 `kilo.json`），见 [../skills/README.md](../skills/README.md)。

## 阅读顺序

| 我想… | 读 |
| --- | --- |
| 了解这是什么、怎么用 | [../README.md](../README.md) |
| 了解架构、模块职责、协议与安全模型 | [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) |
| 对接服务端接口 | [api/API.md](api/API.md) |
| 知道接下来做什么、按什么顺序做 | [planning/ROADMAP.md](planning/ROADMAP.md) → [planning/ROADMAP-ADS.md](planning/ROADMAP-ADS.md) |
| 看某项能力的技术设计与实施步骤 | [planning/DESIGN-PHASE1-SELECTOR.md](planning/DESIGN-PHASE1-SELECTOR.md) |
| 了解广告域名过滤、Root/非 Root、通知治理的边界与决策 | [planning/DESIGN-ADS-FILTERING.md](planning/DESIGN-ADS-FILTERING.md)（评审稿，L2/L3 未获批准前不启用） |
| 了解构建框架演进方案（version catalog / 约定插件 / 模块拆分） | [planning/DESIGN-BUILD-FRAMEWORK.md](planning/DESIGN-BUILD-FRAMEWORK.md) |
| 配置本机开发环境 | [development/DEV-ENVIRONMENT.md](development/DEV-ENVIRONMENT.md) |
| 查某版本发布到哪个提交、哪个 tag | [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) |
| 查安全加固/技术短板跟进计划（已修复清单 / 待修复 / 里程碑） | [planning/FOLLOW-UP.md](planning/FOLLOW-UP.md) |
| 了解提交/分支/发布/回滚规范 | [CONTRIBUTING.md](../CONTRIBUTING.md)（执行摘要见 [AGENTS.md](../AGENTS.md)） |
| 多个 Agent / 多分支并行开发时的隔离与协作 | [development/AGENT-WORKFLOW.md](development/AGENT-WORKFLOW.md) |
| 开发或维护 Agent 技能（SKILL.md 格式 / 校验 / 登记） | [../skills/README.md](../skills/README.md) |
| 查用户可见变更 | [CHANGELOG.md](../CHANGELOG.md) |

## 规划事实源（Single Source of Truth）

| 主题 | 唯一事实源 | 其他文档的角色 |
| --- | --- | --- |
| 版本序列与能力意图（`3.0.3` → `3.1.0` → …） | [planning/ROADMAP.md](planning/ROADMAP.md) | 只引用版本号，不另立序列 |
| 专项里程碑、周次、出口条件 | [planning/ROADMAP-ADS.md](planning/ROADMAP-ADS.md)（第 5~6 节） | 引用 ROADMAP 的版本号 |
| L1 选择器技术方案与步骤 A–F | [planning/DESIGN-PHASE1-SELECTOR.md](planning/DESIGN-PHASE1-SELECTOR.md) | 引用版本号与里程碑编号 |
| L2 DNS 过滤、L3 Root 扩展、L4 通知治理的技术边界与候选方案 | [planning/DESIGN-ADS-FILTERING.md](planning/DESIGN-ADS-FILTERING.md)（评审稿） | Phase 0 评审通过前不视为批准实现 |
| 构建框架演进方案（version catalog / build-logic / 模块拆分） | [planning/DESIGN-BUILD-FRAMEWORK.md](planning/DESIGN-BUILD-FRAMEWORK.md) | 本页不复制其坑位清单；目录结构的**已生效**规则见 [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) 第 2.2 节 |
| 已发布版本链路（tag / 提交 / Release / 制品） | [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) | CHANGELOG 只记用户可见变更，不复述链路 |
| 协议与接口 | [api/API.md](api/API.md) + [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md)（第 7 节） | DESIGN 只描述增量字段 |
| 客户端/服务端分层与模块职责 | [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) | README 只给目录树摘要 |
| 流程规范（分支/提交/门禁/发版） | [CONTRIBUTING.md](../CONTRIBUTING.md) | [AGENTS.md](../AGENTS.md) 为执行摘要 |
| 多 Agent 并行协作（隔离/所有权/契约/交接） | [development/AGENT-WORKFLOW.md](development/AGENT-WORKFLOW.md) | 本页不复制其条款，仅登记入口；其卫生检查由 `RepoHygieneTest` 强制 |
| Agent 技能开发（SKILL.md 格式 / 校验 / 挂载） | [../skills/README.md](../skills/README.md) | 技能只保留指针与自检动作，不复制规则；分支/提交/门禁仍以 [CONTRIBUTING.md](../CONTRIBUTING.md) 为准 |

> **改规划的顺序**：先改 `docs/planning/ROADMAP.md`（版本意图）→ 再改同目录的 `ROADMAP-ADS.md` 与 `DESIGN-PHASE1-SELECTOR.md` 的版本归属 → 最后同步 `CHANGELOG.md` 与 `RELEASE-HISTORY.md` 的对应行。`docs/api/API.md`、`docs/architecture/ARCHITECTURE.md` 涉及协议字段时一并更新。

## 当前规划全景（更新于 2026-10-10）

| 版本 | 状态 | 内容 | 详见 |
| --- | --- | --- | --- |
| `3.0.2` | 已发布（tag 已建，制品待补传） | v3.0 系列补丁 | [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) |
| `3.0.3` | 已发布（tag 已建，CI 制品已归档；制品未签名不可安装，见 [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) 制品勘误） | 选择器第三通道内核（步骤 A/B，无用户可见行为变化） | [planning/DESIGN-PHASE1-SELECTOR.md](planning/DESIGN-PHASE1-SELECTOR.md) 第 10 节 |
| `3.0.4` | 已发布（tag 已建，制品已签名可直接安装） | 安装可用性修复：release 签名回退 + 发布强制签名校验 + Secrets 注入正式密钥（Issue #23） | [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) |
| `3.1.0` | 已发布（tag `v3.1.0` @ `main` `ad188b0`；制品 `CN=AdSkip Release` 签名，`apksigner verify` 通过） | 协议 v2（`selectors` 字段）+ 点击结果校验 + 品牌更名「轻启」+ 图标/UI 重设计 + 快捷磁贴 + 厂商保活 + ktlint 门禁（用户可见变更见 [CHANGELOG.md](../CHANGELOG.md) 3.1.0） | [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) |
| `3.2.0` | 已发布（tag `v3.2.0` @ `main` `f5127dd`；Release 资产 `AdSkip-v3.2.0.apk` 1,923,024 bytes + `SHA256SUMS`） | 节点快照工具 + 设置页入口（步骤 E）+ UI 重设计与权限体系重构落版 | [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) |
| `3.3.0` | 已发布（tag `v3.3.0` @ `1c296c0`；Release 资产 `AdSkip-v3.3.0.apk` 2,047,331 bytes + `SHA256SUMS`） | OkHttp 网络层迁移 + 服务端请求体限制/结构化日志 + 统计备份唯一名；**无**规则 HMAC、证书 pin 与强制 HTTPS | [planning/RELEASE-HISTORY.md](planning/RELEASE-HISTORY.md) |
| M1d / 步骤 F | **未发布**（版本号待定，`3.3.0` 已被占用） | Top 30 规则入库 + 真机回归 + 规则审核通道 | [planning/ROADMAP-ADS.md](planning/ROADMAP-ADS.md) 第 6 节 |
| `3.4.0` / `3.5.0` / `4.0.0` | 规划 | L2 DNS 过滤 / L3 防摇一摇 / L4 通知与系统层 | [planning/ROADMAP-ADS.md](planning/ROADMAP-ADS.md) 第 5~6 节 |

未排期能力见 [planning/ROADMAP.md](planning/ROADMAP.md) 的「候选池」；L2/L3 启动前必须先完成 Phase 0 合规评审（见 [planning/ROADMAP-ADS.md](planning/ROADMAP-ADS.md) 第 3 节）。

> **已落地**：系统级体验——下拉快捷磁贴（`TileService`）+ 厂商保活/自启动引导（`device/` 层，含 `<queries>` 可见性声明与无障碍真实状态查询）已随 `v3.1.0-rc.1` 预发布、`v3.1.0` 正式版发布（tag `v3.1.0`），见 [planning/ROADMAP.md](planning/ROADMAP.md)。

## 规划中尚未创建的文档

| 文档 | 归属版本 | 说明 |
| --- | --- | --- |
| `docs/guide/RULE-AUTHORING.md` | M1d（版本号待定） | 规则编写指南：选择器语法、快照 → 规则流程、审核提交流程（届时新建 `guide/` 目录） |
| 覆盖度矩阵公示页 | M1d（版本号待定） | 对用户公示各广告类型的覆盖口径（当前为 [planning/ROADMAP-ADS.md](planning/ROADMAP-ADS.md) 第 4/9 节的内部口径） |

## 文档命名规范

文档命名是**机器可读的约定**：新文件名必须能被规则解释，改名必须走同步流程。规则解释以本页为准。

| 文档模块 | 目录用途 | 文件名格式 | 示例 |
| --- | --- | --- | --- |
| `api/` | 对外接口、数据格式与协议契约 | `API[-<TOPIC>].md` | `API.md` |
| `architecture/` | 系统架构、模块职责与边界 | `ARCHITECTURE[-<TOPIC>].md` | `ARCHITECTURE.md` |
| `development/` | 开发环境和协作流程；环境类用 `DEV`，协作类用 `AGENT` | `<DEV\|AGENT>-<TOPIC>.md` | `DEV-ENVIRONMENT.md`、`AGENT-WORKFLOW.md` |
| `planning/` | 路线图、技术设计、跟进事项和发布记录 | `ROADMAP[-<TOPIC>].md`、`DESIGN-<TOPIC>.md`、`FOLLOW-UP[-<TOPIC>].md`、`RELEASE[-<TOPIC>].md` | `ROADMAP-ADS.md`、`DESIGN-PHASE1-SELECTOR.md`、`FOLLOW-UP.md`、`RELEASE-HISTORY.md` |
| `guide/`（按需创建） | 面向用户或贡献者的操作指南 | `GUIDE-<TOPIC>.md` | `GUIDE-RULE-AUTHORING.md` |

通用规则：

- 文档分类目录使用**小写 kebab-case**；按内容归类到上表模块，新增目录前先登记文档地图和结构守护测试。`diagrams/` 专门存放图表等非 Markdown 资产，不用作文档模块。
- `.md` 文件名使用**全大写 ASCII 字母、数字与短横线**，扩展名固定为 `.md`。`<TOPIC>` 和可选部分用短横线分词；`PHASE1` 等阶段编号中的数字紧跟词干，不插入空格或下划线。
- 模块前缀按上表固定，不得跨目录复用其他模块的前缀；一个模块内同一主题的多份文档用额外主题词区分，禁止用 `NEW`、`FINAL`、日期或版本号代替主题。
- 目录入口 `README.md` 是固定例外，不参与模块前缀规则；当前 `docs/README.md` 是唯一的文档地图入口。
- 非文档资产采用 `lower-kebab-case`，与可阅读文档区分，例如 `diagrams/adskip-architecture.json` / `.html`。
- H1 使用 `<中文主题>（<文件基名>）`，基名不带 `.md`；入口文档可用其入口主题，不强制重复 `README`。例如 `# 多 Agent 协作规范（AGENT-WORKFLOW）`。

- **改名流程**：`git mv` 改文件名 → `git grep` 全仓同步所有引用（含本文档地图）→ 守护测试若硬编码文档名（如 `RepoHygieneTest.WORKFLOW_DOC`）一并更新 → 跑相关门禁后提交。

## 维护规则

- 本页登记全部文档；文档按模块归入对应子目录（`api/` `architecture/` `development/` `planning/`，新建模块目录同样登记到「目录结构」），命名遵守上节「文档命名规范」；文档改名或移动后，用 `git grep` 检索全仓引用并同步。
- 规划类文档（ROADMAP / ROADMAP-ADS / DESIGN）首部必须保留「状态 + 最后更新」行。
- 版本号、里程碑编号、测试计数等事实只在一处维护，其余位置只引用。
- 发布核对清单见 [CONTRIBUTING.md](../CONTRIBUTING.md) 的「每次发布完成后的最小核对清单」。
