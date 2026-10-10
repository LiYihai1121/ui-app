# 多 Agent 协作规范（AGENT-WORKFLOW）

> 状态：已生效；最后更新：2026-09-28。
> 适用范围：任何在本仓库并行工作的 AI Agent 或协作者（人类多分支开发同理）。
> 摘要见 [AGENTS.md](../../AGENTS.md)；分支/提交/门禁/发版规范见 [CONTRIBUTING.md](../../CONTRIBUTING.md)。

本仓库已有的「分支—提交—门禁—发版」规范解决的是**单条变更链路的可审计性**，并不约束**多个 Agent 同时干活时的相互干扰**。本文只补这一块，条款均可执行、可检查（第 9 节列出已落到 CI 的部分）。

## 1. 症状与根因

以下症状均在本仓库实测出现过，不是假想：

| 症状 | 真实根因 | 对应条款 |
| --- | --- | --- |
| 检索/读取同一文件出现两份结果（`client/...` 与 `.kilo/worktrees/<name>/client/...`） | 仓库内存在**没有 `.git` 元数据的整仓副本** | 2.1 |
| `git status` 显示干净，工作区里却确有第二份源码 | 忽略规则只写在本机 `.git/info/exclude`，未进共享 `.gitignore` | 2.2 |
| Agent「改了文件」但提交里没有、提交后代码没变 | 改到了副本里的同名文件 | 2.1 |
| 两个 Agent 同时改一个文件，合并时才发现冲突 | 没有文件级唯一写入者 | 3.1 |
| 合并后接口对不上，返工重做 | 边界只靠口头约定，没有可执行契约 | 5 |
| 文档被多方改写，事实源自相矛盾 | 没有文档单写者 | 3.3 |

**一句话根因**：多 Agent 的隔离没有落在「git 看得见」的地方，于是隔离靠自觉、冲突靠运气。

## 2. 隔离：一人（机）一工作区

### 2.1 用真正的 worktree，不要复制整仓

每个 Agent 在**自己的 worktree** 里工作，目录统一放在仓库内的 `.worktrees/`（已被 `.gitignore` 覆盖）：

```bash
# 从最新 main 出发，为自己开一个工作区
git fetch origin
git worktree add .worktrees/<agent>-<slug> -b feature/<id>-<slug> origin/main

# 开工前自检：工作区里必须有 .git 元数据（worktree 内的 .git 是文件，不是目录）
ls .worktrees/<agent>-<slug>/.git

# 收工后清理（分支已合并后）
git worktree remove .worktrees/<agent>-<slug>
git worktree prune
```

硬性要求：

- **禁止**在仓库内复制/解压整仓来当工作区（`Copy-Item`、`robocopy`、压缩包解压、IDE 的 "duplicate project" 均算）；
- 正确 worktree 内的 `.git` 是**文件**；**没有 `.git` 的仓库副本就是垃圾**，必须删除（保留在 `F:\All_Data\Temp\stale-agent-copies\` 一类的仓库外归档位置）；
- `git worktree list` 应能列出所有在用工作区。**出现「目录里有完整源码但 `git worktree list` 没有」的情况即为违规**，第 9 节会检测。

### 2.2 忽略规则必须共享

- Agent 产物与工作区目录（`.kilo/`、`.kilocode/`、`.worktrees/`、`.agents/`、`.mimosa/`、`.workbuddy/`）**必须**写在仓库根 `.gitignore`（已提交、全员可见）；
- **禁止**只写本机 `.git/info/exclude`：该文件不进版本库、换机器就失效，且会制造「git 干净但文件检索仍命中」的双重真相；
- 工具若自动往 `.git/info/exclude` 追加同类规则，**以 `.gitignore` 为准**，重复规则无害但不可作为唯一来源。

### 2.3 开工 30 秒自检

```bash
git worktree list          # 我在哪、工作区是否已登记
git status --short --branch  # 是否干净、是否在合规分支上
git rev-parse --show-toplevel  # 确认工作区根
```

三项都符合预期再开始写代码；发现「第二份源码」先清理再开工。


## 3. 所有权：唯一写入者

### 3.1 文件级认领

- **同一时刻，一个文件只有一个写入者**。开工前在认领板登记「我负责哪些路径」；
- 需要改别人认领的文件时，**先在认领板发起交接请求**，由对方确认或改由自己接手，不得直接改；
- 认领粒度建议到目录：例如 `client/app/src/main/java/com/qingqi/adskip/device/`、`server/src/api/`、`docs/planning/`；
- **不允许两个 Agent 在同一分支上工作**（分支与 Agent 一一对应，见第 4 节）。

### 3.2 认领板

并行任务时把下表维护在本节（单 Agent 开发时可整表删除）：

| Agent | 工作区 | 分支 | 认领路径 | 状态 |
| --- | --- | --- | --- | --- |
| `gzdx` | `.worktrees/gzdx-home-redesign` | `feature/94-home-compose-redesign` | `client/.../ui/home/**`、`ui/components/Common.kt`、`ui/MainActivity.kt`、`res/values*/strings.xml` | 待合并 |
| — | — | — | `docs/architecture/ARCHITECTURE.md`、`CHANGELOG.md` | 预留：仅集成者写 |

状态取值：`进行中` / `待合并` / `阻塞` / `已完成`。任务认领与状态广播优先使用协作工具的任务清单与信箱，其次才用本表（避免两处状态打架）。

> **⚠️ 协作事件记录与硬性要求（2026-10-11）**
>
> 两个会话同时写入**主检出**（`F:/LocaRepository/ui-app`），造成一次实际冲突：一方的未提交改动
> 被另一方 stash（`stashed unrelated feature changes`），且 `a6f8150`（移除 code-simplifier）
> 被提交进了他人分支。所幸改动经 `git stash pop` 全量恢复，未丢失工作。
>
> **硬性要求**：主检出只用于分支整合与查看，**禁止任何会话在主检出直接开发**。
> 每个会话必须先执行 `git worktree add .worktrees/<agent>-<slug> <branch>` 并在其中工作
> （AGENTS.md「多 Agent 协作」一票否决项）。`release/v3.4.1` 的进行中改动（versionCode 15）
> 应迁入 `.worktrees/<agent>-v341/` 后继续，并在此表登记认领。

### 3.3 文档单写者

以下文件是**事实源**，同一时间只允许一个写入者（默认：任务集成者 / PR 作者）：

| 文档 | 角色 |
| --- | --- |
| `CHANGELOG.md` | 单一发布者写；其他 Agent 只在自己的分支改自己的条目块，冲突时由发布者裁定 |
| `docs/planning/ROADMAP.md`、`ROADMAP-ADS.md` | 版本意图的唯一定义处，改动需与实现同一 PR |
| `docs/architecture/ARCHITECTURE.md` | 分层与边界契约；改边界必须同时改 `ArchitectureBoundaryTest` |
| `docs/README.md` | 文档地图；新增/改名/删除文档必须同 PR 登记 |
| `AGENTS.md` / `CONTRIBUTING.md` | 流程规范；由流程所有者维护 |

其他 Agent 改文档前，先在认领板确认该文档未被他人认领。

## 4. 分支：一任务一分支，与 Agent 绑定

- 分支格式 `type/<id>-<slug>`（`type` ∈ `feature|fix|docs|ci|test|refactor|release|hotfix`），与 Agent 一一绑定，禁止两个 Agent 共用分支；
- **禁止**对他人分支执行 `rebase`/`reset --hard`/`force-push`/`commit --amend`；需要整合时由分支属主操作；
- 合并顺序：**先合主干与短分支，再合依赖它的大分支**；跨分支有依赖（如协议 v2 先于客户端解析）时，在认领板标注依赖关系；
- 分支落后主干超过一个提交批次时，先 `git fetch && git rebase origin/main`（仅限自己分支）再继续，避免合并期堆积冲突；
- 合并一律走 PR + Squash（见 [CONTRIBUTING.md](../../CONTRIBUTING.md)），禁止 Agent 自行往 `main` 推送。

## 5. 契约先行：跨边界改动必须可执行

并行开发最贵的返工是「合并后才发现接口不一致」。因此**任何跨包/跨端边界的改动，必须同时给出机器可验证的契约**：

- 客户端分层边界（`engine`/`core`/`ui`/`data`/`net`/`sync`/`service`/`device` 的依赖方向）→ 由 `ArchitectureBoundaryTest` 强制；
- `AndroidManifest.xml` 与代码中的入口表/资源对齐 → 由 `ManifestContractTest` 强制；
- 仓库卫生（忽略规则、仓库副本、文档登记、分支命名）→ 由 `RepoHygieneTest` 强制（见第 9 节）；
- 服务端契约变更（schema 字段、校验规则）→ 同步更新 `docs/api/API.md` 并补 `server/test` 用例。

写法：**先加会失败的契约测试，再写实现**。若某条边界确实要放宽，先改文档与守护测试，再改实现——顺序反了就会出现「文档说一套、测试守另一套」。

## 6. 验证：每个 Agent 自跑门禁

合并前**各自**跑通与变更相关的最小门禁，跨模块跑全门禁——命令清单见 [AGENTS.md](../../AGENTS.md)「验证与合并」，本节不重复，以免两处漂移。

- **不要把验证推给 CI 或集成者**：CI 红了要重跑一轮，代价远高于本地自测；
- 报告验证结果时给出**实际执行的命令与结果数字**（形如「N/N 通过」），不给「应该没问题」；
- 只跑了子集测试时必须显式说明未跑的部分。

## 7. 交接：最小交接信息

Agent 之间移交任务或收工时，输出以下五项，缺一项都会让接手方重新摸索：

1. **改了什么**：文件清单 + 一句话意图；
2. **动了哪些边界**：新增/修改的契约、接口、协议字段；
3. **验证结果**：实际执行的命令与结果数字；
4. **遗留与风险**：已知未覆盖场景、平台差异、需要真机验证的点；
5. **建议下一步**：接手方可以直接执行的第一条命令。

示例（磁贴任务收工）：

```text
1. 新增 client/.../device/{VendorKeepAlive,QuickTileLogic,KeepAliveNavigator,SkipTileService}.kt，
   SkipAdService 增加 isEnabled()/requestShutdown()，AndroidManifest 注册磁贴与 <queries>。
2. 新增 device/ 层边界（ARCHITECTURE.md 2.1），由 ArchitectureBoundaryTest 守护；
   新增 ManifestContractTest 守护磁贴注册与 <queries> 覆盖。
3. assembleDebug 通过；testDebugUnitTest 全绿（新增用例若干）；bun test 全绿；typecheck exit 0。
4. 入口表为社区经验值，ROM 升级可能失效（已做运行时可解析性探测 + 降级）；
   未在 MIUI/HarmonyOS 真机验证跳转，磁贴点按关闭为“一键关核心功能”策略，待产品确认。
5. 在 MIUI 与 HarmonyOS 真机上各跑一次保活跳转，把失败项回报以修表。
```

## 8. 冲突裁决

| 冲突类型 | 裁决方式 |
| --- | --- |
| 同一文件被两个 Agent 修改 | 认领板仲裁：先合入者为准，后合入者在自己的分支 rebase 后**手工**合并语义，不自动取 ours/theirs |
| 事实源文档（CHANGELOG / ROADMAP / ARCHITECTURE）内容矛盾 | 由该文档的单一写入者裁定，其余方改为引用而不复述 |
| 契约测试与实现不一致 | 以**文档 + 守护测试**为准；若边界确需调整，先提 PR 改文档与测试 |
| 分支落后导致大量冲突 | 暂停功能开发，先同步主干（`git fetch && git rebase origin/main`）再继续 |
| 两个 Agent 都需要同一改动 | 合并为一个任务、单一写入者实现，另一方改为消费 |

禁止解法：强推他人分支、用整文件覆盖解决冲突、在文档里写「见另一文档」却不更新事实源。

## 9. 卫生检查（已落到 CI）

以下规则由 `client/app/src/test/java/com/qingqi/adskip/arch/RepoHygieneTest.kt` 随 `testDebugUnitTest` 强制执行，违反即测试失败：

| 检查 | 拦截的混乱 |
| --- | --- |
| 根 `.gitignore` 必须覆盖 `.mimosa/`、`.workbuddy/`、`.kilo/`、`.kilocode/`、`.worktrees/`、`.agents/` | 忽略规则只藏在本机 `exclude` 里，git 与工具「双重真相」 |
| 仓库内不得存在「像仓库但没有 `.git`」的目录（无元数据整仓副本） | 检索结果翻倍、Agent 改错副本、成果丢失 |
| `docs/README.md` 必须登记 `AGENT-WORKFLOW.md` | 新增文档未进文档地图（AGENTS 规则 CI 化） |
| 当前分支名必须符合 `type/<id>-<slug>`（detached HEAD 放行） | 分支不可追踪、多人混用同一分支 |

## 10. 与既有规范的关系

- 本文**不替代** [CONTRIBUTING.md](../../CONTRIBUTING.md)：分支命名、提交规范、PR 门禁、发版流程仍以它为准，本文只增加「并行时」的隔离、所有权、契约与交接要求；
- 本文**不替代** [DEV-ENVIRONMENT.md](DEV-ENVIRONMENT.md)：本机工具链配置仍以它为准；
- 违反本文任一条时，先修工作区状态（清理副本、补忽略规则、认领文件），再继续开发——**先恢复单一真相，再写代码**。


本文不替代流程规范，不制造第二份真相。

