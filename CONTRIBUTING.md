# 贡献与版本管理规范

本项目采用 **GitHub Flow 为主、叠加 `release/*` 与 `hotfix/*` 的 GitHub Flow**：只有 `main` 一条受保护的集成主干，所有变更必须通过 Pull Request（PR）进入；发布使用短生命周期的 `release/*` 分支和不可移动的版本标签。规范的目标是让每次变更**可审计、可复现、可回滚**。

本文是**流程规范**（该做什么、什么条件下允许合并与发版）。命令清单不复述在此——命令会随工具链版本漂移，复制出去的清单会静默变成第二份真相；各命令的唯一事实源见文末[「命令与清单的唯一事实源」](#命令与清单的唯一事实源)。

## 目录

- [版本控制最佳实践](#版本控制最佳实践)
  - [1. 提交信息规范](#1-提交信息规范)
  - [2. 分支策略](#2-分支策略)
  - [3. 原子提交：禁止大型单体变更](#3-原子提交禁止大型单体变更)
  - [4. 合并冲突处理：rebase 还是 merge](#4-合并冲突处理rebase-还是-merge)
  - [5. 合并前的提交压缩](#5-合并前的提交压缩)
  - [6. 禁止入库的内容](#6-禁止入库的内容)
- [Pull Request 门禁](#pull-request-门禁)
- [多 Agent 并行开发](#多-agent-并行开发)
- [版本策略与发布](#版本策略与发布)
  - [版本号语义（Semantic Versioning 2.0.0）](#版本号语义semantic-versioning-200)
  - [兼容性：客户端、服务端、协议](#兼容性客户端服务端协议)
  - [发布通道与晋级门槛](#发布通道与晋级门槛)
  - [发布分支纪律](#发布分支纪律)
  - [维护窗口与生命周期（EOL）](#维护窗口与生命周期eol)
  - [弃用策略（Deprecation）](#弃用策略deprecation)
  - [发布节奏与冻结期](#发布节奏与冻结期)
  - [供应链与可追溯（SLSA 对齐）](#供应链与可追溯slsa-对齐)
  - [发布顺序](#发布顺序)
  - [发布验收](#发布验收)
- [纯工程变更不需要版本号](#纯工程变更不需要版本号)
- [每次发布完成后的最小核对清单](#每次发布完成后的最小核对清单)
- [紧急变更与回滚](#紧急变更与回滚)
- [仓库管理员配置](#仓库管理员配置)
- [命令与清单的唯一事实源](#命令与清单的唯一事实源)

---

## 版本控制最佳实践

Git 历史的成本是**长期**的：`git log`、`git blame`、事故复盘、二分定位全部读它。历史质量的三个判据：

1. **可追溯**——任一提交能回答「为什么改」「谁改的」「对应哪个需求」；
2. **可二分**——任意两个相邻提交之间，`git bisect` 能收敛到一个可复现的好坏分界；
3. **可回滚**——出问题时能定位到**最小**的回滚单元，而不是被迫整体回退。

下面六项规则都是为这三条服务的。

### 1. 提交信息规范

#### 格式

采用 [Conventional Commits](https://www.conventionalcommits.org/)，标题不超过 **72 个字符**：

```text
<type>(<scope>): <简短描述>
```

- **type**：`feat`、`fix`、`refactor`、`perf`、`test`、`docs`、`build`、`ci`、`chore`、`revert`
- **scope**：模块或领域名（`client`、`server`、`ui`、`engine`、`rules`、`protocol`、`contract`、`roadmap`……）。**禁止用人的名字或工号**——人员会流动，模块名不会。
- **描述**：祈使句写「做了什么」，不写「改成了什么」；同一 type 下用一致的动词。
- **机器校验**：格式与长度由 `githooks/commit-msg` 拦截（启用方式见「一人一工作区」的 hooksPath 说明）；历史违规样例与豁免线见 [FOLLOW-UP-PROCESS-AUDIT.md](docs/planning/FOLLOW-UP-PROCESS-AUDIT.md)。

破坏性变更必须在标题追加 `!`（`feat(protocol)!:`）**或**正文使用 `BREAKING CHANGE:` 段落，并同步更新协议/API 文档。

#### 正文：必须回答三个问题

除纯文档、纯格式的提交外，**正文不能为空**，且必须写清：

| 问题 | 内容 | 为什么必须 |
| --- | --- | --- |
| **为什么** | 触发变更的原因、故障现象或被否决的方案 | 没有原因的提交，半年后没人敢动它 |
| **影响什么** | 变更范围、行为差异、**向后兼容性** | 兼容性是发布决策的输入，必须显式声明 |
| **怎么验证** | 实际跑过的命令与数字（`82 pass / 0 fail`、179 例 0 失败） | 「应该没问题」不是验证；数字可被复核 |

建议在正文末尾附「风险与回滚」：这条改动最坏会怎样、`git revert` 是否足够、是否需要配套的向后兼容方案。

#### 引用与回滚

- 关联需求用页脚：`Refs #<id>`；破坏性或需迁移的用 `BREAKING CHANGE:`。
- 回滚用 `git revert` 生成新提交（`revert: <被回滚的标题>`），**禁止** `reset` 或改写已推送的历史——被回滚的那次变更可能已被他人拉取或已发布。

#### 反例与正例

```text
✗ update
✗ fix bug
✗ WIP
✗ feat: 改了一些东西
✓ fix(validate): 修复选择器白名单丢弃中文规则
    正文：\w 在 JS 中仅匹配 ASCII，导致 [text*="跳过"] 被整条丢弃……
```

| 反例 | 问题 | 正例 |
| --- | --- | --- |
| `update` / `tmp` / `WIP` | 无法检索、无法二分 | `ci: 引入 ktlint 静态检查门禁` |
| `feat: 重构规则模块` | 范围与影响不明，无法判断可否回滚 | `refactor(rules): 抽出选择器文法解析，与匹配器解耦` |
| 只有标题、无正文 | 丢失原因与兼容性信息 | 补「为什么/影响/验证」三段 |
| `feat(server): 发布 v3.1.0` | 版本号变更不得与功能混提 | 版本号变更单独走 `release/*` 或独立 PR |

### 2. 分支策略

#### 选型与理由

本仓库**不是**完整 Git Flow。选型对照：

| 模型 | 结构 | 适用前提 | 本仓库为何不选 / 部分沿用 |
| --- | --- | --- | --- |
| **GitHub Flow**（采用） | `main` + 短生命周期特性分支 | 持续交付、主干始终可发布 | 作为主干模型采用 |
| **Git Flow**（部分沿用） | 额外引入长期 `develop` + `release/*` + `hotfix/*` | 多版本并行发布、强发布流程 | **不引入 `develop`**：长期分支会让「主干随时可发布」这一保证失效，且对单人/小团队是额外状态而非额外安全。仅沿用其 `release/*` 与 `hotfix/*` |
| **Trunk-based** | 所有人直接向 `main` 提交、短生命周期开关 | 强测试覆盖 + 功能开关成熟 | 待条件具备后再评估；本项目触及无障碍、磁贴、厂商跳转等系统级行为，**开关无法兜底的部分需要隔离分支** |

#### 分支类型

从最新 `main` 创建，分支名 `<type>/<id>-<简述>`：

| 分支 | 用途 | 生命周期 | 规则 |
| --- | --- | --- | --- |
| `main` | 可发布集成主干 | 长期 | 禁止直接 push、强制推送和本地提交 |
| `feature/<id>-<简述>` | 新功能 | 短期 | 从最新 `main` 创建，一个分支只对应一个需求 |
| `fix/<id>-<简述>` | 缺陷修复 | 短期 | 必须关联 Issue 或事故记录 |
| `docs/<id>-<简述>` | 纯文档 | 短期 | 不改变运行时行为 |
| `test/<id>-<简述>` | 测试/门禁增强 | 短期 | 只增测试或守护规则，不改实现 |
| `refactor/<id>-<简述>` | 内部重构 | 短期 | 必须行为不变（可用全量单测与制品对比证明） |
| `ci/<id>-<简述>` | 构建与流水线 | 短期 | 不改应用运行时逻辑；依赖与构建配置升级同样走此类 |
| `release/vX.Y.Z` | 发布冻结与验收 | 发布期间 | 只允许版本、文档、阻断性缺陷修复 |
| `hotfix/<id>-<简述>` | 生产紧急修复 | 尽快合并 | 发布后必须回合并 `main` 和维护中的 release 分支 |

命名要求：小写、短横线、**可追踪的 `<id>`**（Issue、事故单或任务号），简述用动宾结构。

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/123-rule-simulator
```

**机器强制**：`RepoHygieneTest` 校验当前分支名匹配 `main|master` 或 `<上述 type>/<slug>`（slug 为小写字母数字、`.`、`_`、`-`），不合规则测试失败。

> **门禁只覆盖格式，不覆盖可追踪性**：`RepoHygieneTest` 仅校验分支名**形状**，**不校验 `<id>` 是否对应真实 Issue**。因此「是否关联了可追踪的 Issue」仍是评审的人工责任，不能因为 CI 变绿就认为分支名已合规。

#### 分支生命周期

- 合并后删除本地与远端分支；**不得复用已删除的分支名**（同名会让旧提交在追溯时产生歧义）。
- 长期存在的主题分支会让「主干始终可发布」失效。若一个主题分支存活超过一个发布周期，要么缩小范围尽快合并，要么明确记录为技术债并登记到 [ROADMAP.md](docs/planning/ROADMAP.md)「候选池」。
- **允许堆叠分支（stacked branches）**：一个变更必须建立在另一个尚未合并的变更之上时（例如依赖对方的协议字段），允许在 PR 描述中显式声明依赖顺序与「需先合入 #N」，并在合并时按依赖顺序合入。**不允许**的是「分支从旧 `main` 拉出且双方都改了同一批文件」——那是冲突的来源，不是堆叠的正当形态。

### 3. 原子提交：禁止大型单体变更

#### 定义

一个提交是**原子的**，当且仅当同时满足：

1. **单一意图**——只做一件事，不夹带顺手修改；
2. **可独立回滚**——`git revert` 该提交不会让主干处于「功能缺失」的中间态；
3. **可独立验证**——即使主干没有它，其余代码仍能构建、测试通过。

#### 拆分维度

**按「可回滚单元」拆，不按文件拆、不按工时拆。** 常见的正确拆法：

| 场景 | 正确拆法 |
| --- | --- |
| 新增功能 + 修复顺带发现的缺陷 | 两个提交：功能、缺陷（各关联各自 Issue） |
| 依赖升级 + 代码适配 | 先升级并让代码仍可编译，再单独做适配改动 |
| 重构 + 行为变更 | 先纯重构（行为不变、有测试兜底），再改行为 |
| 跨端协议变更 | 契约测试（先失败）→ 协议字段与校验 → 客户端消费 |
| 引入格式化/静态检查工具 | 工具接入本身一个提交；被工具改写的存量文件单独一个**纯机械**提交 |

#### 大小参考

- 单个 PR 的**语义 diff**（排除纯机械的格式重排、锁文件、生成代码）以**可在一次评审中读完**为准；经验上限是「一个屏幕内能列完文件清单与每处改动意图」。
- 超限时先问三个问题：能否拆成可独立回滚的两步？其中是否有独立的可验证产物（如失败测试、契约夹具）？是否有纯机械部分可以单独剥离？
- **纯机械改动必须独立成提交**：格式化、批量重命名、依赖锁文件更新——混进功能提交会让功能 diff 被噪音淹没，也让 `git revert` 无法只回滚功能。

#### 本仓库的既有实践

步骤 C（协议 v2 选择器通道）在落地时被拆为五个单意图提交：`feat(rules)` 打通链路 → `feat(protocol)` 升 schema → `feat(server)` 后台与模拟器 → `test(contract)` 双端夹具 → `docs` 同步文档。每一项都能独立回滚，且在 PR 中分别说明了原因、影响与验证数字。

引入静态检查门禁的变更同样拆为两个提交：`ci`（工具接入 + 机械格式改动 + CI 流水线）与 `docs`（门禁清单与踩坑记录）。

#### 与「契约先行」的关系

跨包、跨端的改动要求**先写失败测试再写实现**。这与原子性天然契合：失败测试本身就是「可独立验证、可独立回滚」的提交单元，且它在 `git log` 里留下「先有约束、后有实现」的证据。

### 4. 合并冲突处理：rebase 还是 merge

#### 决策规则

| 场景 | 做法 | 原因 |
| --- | --- | --- |
| 同步**自己**的分支到最新主干 | `git fetch && git rebase origin/main` | 线性历史，冲突只需解决一次 |
| **已推送且他人可能已拉取**的分支 | `git merge origin/main` | rebase 会改写历史，可能破坏他人工作副本 |
| 他人的分支 | 只 merge，不 rebase | 禁止对他人分支做 rebase / reset / amend / force-push |
| `main` / `release/*` | 一律 merge，禁止 rebase 与 force-push | 受保护分支的历史必须稳定 |

**核心原则：rebase 只用于「还没人依赖的历史」；一旦推送且可能共享，就只用 merge。**

#### 标准流程（自己的分支）

```bash
git fetch origin
git rebase origin/main
# 逐个解决冲突 → 跑全部门禁
git push --force-with-lease
```

- 推送改写历史**必须用 `--force-with-lease`**，禁止 `--force`：前者会在远端有他人新提交时拒绝推送，后者会直接覆盖别人的工作。
- 合并到 `main` 始终走 PR + Squash（见 [§5](#5-合并前的提交压缩)），**不**在本地把特性分支 merge 进 `main`。

#### 解决冲突的原则

1. **先理解双方意图，再动手**。冲突是「两处演进撞在一起」的信号，直接选一侧通常会丢掉另一侧的意图。
2. **不要整文件接受一侧**。`git checkout --ours/--theirs <file>` 只适用于该文件确实是机械生成的场景（例如构建产物、锁文件）——这类文件的正确做法是**重新生成**，而不是合并文本。
3. **冲突解决是「未经测试的改动」**。解决完必须重跑全部门禁，尤其是单测与契约测试；冲突标记本身不会破坏编译，但极易破坏语义。
4. **语义冲突优先回到 [§3](#3-原子提交禁止大型单体变更) 重新拆分**。判据：冲突文件超过 3 个、或冲突发生在**双方都改过的同一行逻辑**上、或者需要人工拼出「两边都要」的新逻辑——这说明变更粒度太大，硬解只会掩盖问题。
5. **不要用 `git stash` 搬运多 Agent 的工作**：`stash` 在多个 worktree 之间是共享的，会造成「成果被另一个 worktree 取走」。多 Agent 场景请用独立的 `git worktree`（见[「多 Agent 并行开发」](#多-agent-并行开发)）。
6. **`MERGE_HEAD` / `REBASE_HEAD` 残留时不要切分支**：先把当前操作结束或 `git rebase --abort` / `git merge --abort` 回到干净状态，否则后续操作会在错误的上下文里进行。

#### 减少冲突的做法

- 小步提交、及时同步主干（落后一个提交批次就同步），不要在分支末尾一次性合并几十个提交；
- 保持目录结构稳定（这是本仓库把它写成 `ProjectStructureTest` 门禁的原因之一）；
- 格式化类改动单独成提交，且尽量与逻辑改动错开合入。

### 5. 合并前的提交压缩

#### 规则

- **默认 Squash Merge**：一个 PR 进入 `main` 时压成一个规范提交。
- **`hotfix/*` 允许 Rebase Merge**：事故处理需要保留逐步骤可追溯性。
- **禁止无意义的 merge commit**：仅为「让历史看起来有分支」而产生的合并提交一律禁止。
- 压缩在 GitHub PR 界面完成（合并下拉框选择），不要在本地把特性分支 merge 进 `main`。

#### 为什么默认 squash

1. **主干历史保持「一个变更 = 一个提交」**，读者不必先读十个中间态才能看到最终状态；
2. **避免半成品进入主干**：`fixup!` / `WIP` / 中途失败的尝试不会污染 `main`；
3. **二分定位更准**：提交粒度与「一次可独立验证的变更」对齐，缩小二分搜索的收敛区间。

#### PR 描述承载完整信息

**这是压缩最重要的后果**：中间提交的标题与正文会丢失，最终只留下 PR 标题与描述。因此 PR 描述**必须自带完整信息**——变更范围、兼容性、风险、验证结果、回滚方案（[PR 门禁](#pull-request-门禁)已列为必填）。把信息只写在中间提交里，等于把它扔掉。

#### 本地整理（仅限自己的未推送或独占分支）

用交互式 rebase 把修补类提交并入目标提交，`--autosquash` 可自动识别 `fixup!` / `squash!` 前缀：

```bash
git rebase -i --autosquash origin/main
```

**不要为了「看起来干净」把不相关的提交压成一个**：压缩的正当理由是「它们本来就是同一件事的碎片」，不是「PR 的提交数看起来太多」。

### 6. 禁止入库的内容

三类：**凭据与密钥**、**大二进制**、**构建产物与运行时数据**。

#### 本仓库的机器强制

`ProjectStructureTest` 随 `testDebugUnitTest` 递归扫描整个工作区（跳过 `PRUNED_DIRS` 列出的构建/工具目录与 `.git`）并判失败：

| 类别 | 规则 |
| --- | --- |
| 禁止扩展名 | `apk` `aab` `apks` `aar` / `log` `tmp` `bak` `orig` `rej` `swp` `hprof` / `zip` `tar` `gz` `7z` `rar` `jar` / `keystore` `jks` `p12` `kdb` `pem` / `iml` `exe` `dll` `so` `dylib` |
| 唯一例外 | `client/gradle/wrapper/gradle-wrapper.jar`——**必须入库**，否则任何人克隆后都无法构建 |
| 系统/编辑器残留 | `.DS_Store`、`Thumbs.db`、`desktop.ini` |
| 忽略规则完整性 | 根 `.gitignore` 必须覆盖 `.mimosa`、`.workbuddy`、`.kilo`、`.kilocode`、`.worktrees`、`.agents`、`.cursor`（由 `RepoHygieneTest` 校验） |

**为什么忽略规则必须写在共享 `.gitignore` 而不是本机 `.git/info/exclude`**：只写本机忽略会造成「`git status` 干净、但文件检索与读取仍能命中」的双重真相——协作方与工具会读到看不见的第二份源码，进而改错副本。本仓库已实际发生过该故障。

#### 凭据

- 签名密钥（`signing/`、`*.keystore`/`*.jks`/`*.p12`）、证书口令、CI Secret 令牌**一律不入库**；发布凭据通过 CI Secret 注入，密钥文件在工作区临时物化且目录已被忽略。
- 本机环境配置（`client/local.properties`、`.vscode/`、IDE 的 `*.iml`）不入库。
- 提交前自查：`git diff --cached` 逐行扫一遍，特别留意 **base64 / hex 长串**、`BEGIN PRIVATE KEY`、`Authorization:`、口令赋值。

#### 构建产物与运行时数据

- 客户端产物：APK 走 GitHub Releases + `SHA256SUMS`，**不进 Git**；`build/`、`.gradle/`、`.kotlin/` 均忽略。
- 服务端运行时数据：`server/data/`（统计与规则文件）忽略；种子规则固化在 `server/seed/` 后入库。
- 工具状态目录：`.kilo/`、`.worktrees/`、`.agents/` 等忽略，且**只允许用真正的 `git worktree`**，禁止在仓库内复制整仓（否则会留下无 `.git` 元数据的副本，文件检索命中两份结果）。

#### 行尾、编码与供应链

- 行尾与编码由 `.gitattributes`（`text=auto eol=lf`）与根 `.editorconfig` 共同固定，避免同一文件在不同机器上产生整文件 diff。
- Gradle wrapper 固定 `distributionSha256Sum`：发行版被替换或篡改时 Gradle 拒绝启动，而不是静默换源。
- 依赖升级走 PR 并保留 lockfile / version catalog 变更，不手工改解析结果。

#### 已经误提交了怎么办

按**危害**分三种处理，顺序不能反：

1. **只是产物、临时文件或 IDE 残留**
   从索引移除并补进 `.gitignore`：`git rm --cached <path>`。历史里体积不大时可以保留——它污染历史但不构成安全风险。

2. **体积很大的二进制已进入历史**
   用 `git filter-repo` 重写历史并强制推送。这会改变所有提交哈希，**必须提前通知全部协作者并要求重新克隆或硬重置本地分支**；成本高，因此预防优于补救。

3. **密钥、令牌、口令泄漏**
   **第一步是吊销与轮换，不是改历史。** 历史改写不能让已泄漏的凭据变安全，顺序反了等于什么都没做。凭据轮换生效后，再重写历史以清除对象，并检查 CI 日志与构建缓存中是否留有副本。

---

## Pull Request 门禁

创建 PR 前，提交者必须：

1. 从最新 `main` 创建分支并关联 Issue；
2. 完成自审，确认无敏感信息、无无关改动和无新增诊断（调试日志、临时代码、被注释掉的大段逻辑都不应进入 PR——**调试日志写到仓库之外**）；
3. 在本地**自行执行** [AGENTS.md](AGENTS.md)「验证与合并」列出的全部门禁命令（格式与静态检查、Android 构建、JVM 单测、服务端测试、类型检查）——不把验证推给 CI；本机环境见 [docs/development/DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md)；
4. 填写变更范围、兼容性、数据影响、风险、验证结果和回滚方案（**这些信息是 squash 后的唯一留存内容**，见 [§5](#5-合并前的提交压缩)）；
5. 涉及协议、数据、权限、安全或发布配置时，明确请求对应领域负责人审查；
6. 完成项目更迭时，同步更新 `CHANGELOG.md` 和 [Release list](docs/planning/RELEASE-HISTORY.md#release-list)；发布版本还必须核对 annotated tag、合并提交、GitHub Release 和制品校验和（详见 [每次发布完成后的最小核对清单](#每次发布完成后的最小核对清单)）；涉及规划或文档改动时，按 [docs/README.md](docs/README.md) 声明的事实源顺序同步。

合并规则：

- `main` 和 `release/*` 禁止直接 push、强制推送和删除；
- 必须通过 CI：`Structure Contract`、`Android Build & Test`（含 `ktlintCheck`、`assembleDebug`、JVM 单测、Release 签名校验）、`Server Tests`（含类型检查）；
- 至少 1 名维护者批准；安全、协议或数据变更至少 2 名审批者，其中包含领域负责人；
- 所有评论解决，旧审批在新增提交后失效；
- 分支必须基于最新目标分支，合并使用 Squash（`hotfix/*` 例外，见 [§5](#5-合并前的提交压缩)）；合并后自动删除源分支；
- CI 使用固定 Action 主版本，依赖和权限按最小权限配置。

## 多 Agent 并行开发

单人开发时按上面的分支模型即可。当**多个 Agent（或多名协作者）同时在一个仓库工作**时，额外遵守 [docs/development/AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md)，核心是四点：

1. **隔离**：每个 Agent 一个真正的 `git worktree`（`.worktrees/<agent>-<slug>/`），禁止在仓库内复制整仓；Agent 产物目录必须写进共享 `.gitignore`，不得只写本机 `.git/info/exclude`。
2. **所有权**：同一文件同一时间只有一个写入者（开工前认领），`CHANGELOG.md`、`docs/planning/ROADMAP*.md`、`docs/architecture/ARCHITECTURE.md`、`docs/README.md` 为单写者事实源。
3. **契约**：跨包/跨端边界的改动必须附带可执行契约测试（客户端由 `ArchitectureBoundaryTest`、`ManifestContractTest`、`ProjectStructureTest`、`RepoHygieneTest` 守护），先写失败测试再写实现；目录布局本身也是契约，新增根级文件/目录需先更新 `ProjectStructureTest` 白名单并在 PR 说明理由（细则见 [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) 第 2.2 节）。
4. **交接与合并**：分支与 Agent 一一绑定，禁止对他人分支 rebase/force-push（见 [§4](#4-合并冲突处理rebase-还是-merge)）；交接给出「改了什么 / 边界变化 / 验证结果 / 遗留风险 / 下一步」五项；合并仍走 PR + Squash，顺序上先合依赖方（见[堆叠分支](#2-分支策略)）。

违反上述约定时，仓库卫生类问题会由 `RepoHygieneTest` 直接判失败（如仓库内出现无 `.git` 元数据的整仓副本、忽略规则缺失、协作文档未登记、分支名不合规）。

## 版本策略与发布

本章是版本更迭与发布的流程规范（该做什么、什么条件下允许晋级与发版）；命令清单不复述，见[「命令与清单的唯一事实源」](#命令与清单的唯一事实源)。

### 版本号语义（Semantic Versioning 2.0.0）

版本号 `MAJOR.MINOR.PATCH` 遵循 [SemVer 2.0.0](https://semver.org/)：

- `MAJOR`：不兼容的「公开契约」变更；
- `MINOR`：向后兼容的新增能力；
- `PATCH`：向后兼容的缺陷、安全或性能修复；
- 预发布 `X.Y.Z-rc.N`：`N` 从 `1` 起严格递增、不复用；每个 `rc.N` 必须可独立追溯到一个 annotated tag 与 GitHub Release。

「公开契约」在本项目特指下列任一对外稳定面——触及即按其破坏性判定 `MAJOR`/`MINOR`，而非按代码改动量：

| 公开契约 | 事实源 | 破坏性示例 |
| --- | --- | --- |
| 同步协议 | [docs/api/API.md](docs/api/API.md) + `schemaVersion` | 删字段、收紧校验、改变版本协商语义 |
| 服务端 HTTP API | [docs/api/API.md](docs/api/API.md) | 改端点路径/方法、状态码或鉴权语义 |
| 持久化数据形态 | `Prefs` / 服务端存储 JSON | 旧版本写入的数据不可读取 |
| 用户可见行为 | 选择器语法、设置项、磁贴、跳转 | 改变已公布选择器语义、移除已发布设置项 |

仅内部实现重排、不改上述任一契约的，按[纯工程变更](#纯工程变更不需要版本号)处理，不升版本号。

### 兼容性：客户端、服务端、协议

- 客户端必须容忍服务端 `schemaVersion` 比自身**高或低一档**（±1）：低档忽略未知字段，高档触发兼容降级而非崩溃；
- 服务端读取旧 `schemaVersion` 写入的存量数据时单向补齐，禁止要求客户端先升级（见 [RELEASE-HISTORY.md](docs/planning/RELEASE-HISTORY.md) 对 v3.1.0 协议 v2 的处理）；
- 提升协议 `schemaVersion` 必须配套兼容降级路径与两端契约测试（`SelectorContractTest` / `selectors.contract.test.ts`），否则不发正式版。

### 发布通道与晋级门槛

本项目只设**预发布 `rc`** 与**正式 `stable`** 两个通道，不引入 Canary/Dev——系统级行为无法靠功能开关兜底，需隔离分支（见[§2 分支策略](#2-分支策略)）。

`rc → stable` 必须满足**发布就绪定义（Definition of Done）**：

- [ ] 该版本最后一个 `rc.N` 的三项必需 CI（Structure Contract / Android Build & Test / Server Tests）全绿；
- [ ] `CHANGELOG.md` 该版本条目已冻结，无 `TODO`/占位；
- [ ] 触及系统级范围（无障碍/磁贴/跳转/调度/权限）时，真机验收矩阵已通过（见[发布验收](#发布验收)）；未通过只能维持 `rc`；
- [ ] 无未关闭的 P0/P1 阻断项；
- [ ] `versionName` / `versionCode` / `server/package.json` / tag 四处版本一致，且 `versionCode` 全局单调递增、不复用。

**覆盖真机验收先行发布的例外**：仅当触及系统级范围且真机矩阵确未完成时，可由维护者显式决定覆盖。该决定必须**三处同时声明**——`CHANGELOG.md` 发布验收覆盖声明、[RELEASE-HISTORY.md](docs/planning/RELEASE-HISTORY.md)「发布基线说明」、tag `vX.Y.Z` 的 annotated message——并默认转入 hotfix 候补（出问题走[紧急变更与回滚](#紧急变更与回滚)）。`v3.1.0` 即按此例外发布。

晋级失败（`rc` 发现阻断）时发下一个 `rc.N+1`，**不得**把 `rc` 标签改名 stable；`rc` 标签同样不可移动或删除。

### 发布分支纪律

`release/vX.Y.Z` 从 `main` 创建后进入冻结期，仅接受 **cherry-pick 的修复**，不接受新功能：

- 修复必须先在 `main` 或 `fix/*` 验证，再 cherry-pick 进 `release/*`；禁止在 `release/*` 上直接开发；
- 每个进入 `release/*` 的修复必须**双向落地**：同时回合 `main`，避免主干丢失修复（hotfix 双回合见[紧急变更与回滚](#紧急变更与回滚)）；
- `release/*` 在 tag 创建且 hotfix 窗口关闭（默认 14 天或下一个 stable 发布，以先到者为准）后删除源分支，tag 与提交保持可达。

本仓库**不引入长期 `develop`**（理由见[§2 分支策略](#2-分支策略)），`release/*` 是唯一的发布隔离分支。

### 维护窗口与生命周期（EOL）

| 版本 | 状态 | 接受变更 |
| --- | --- | --- |
| 最新 stable | active | 全部修复 |
| 上一 stable | maintenance | 仅安全与阻断性修复 |
| 更早 stable | EOL | 不再修复；tag 与提交保持可达，承载分支不得删除 |

- 承载已发布 tag 的分支**不得删除**：tag 必须保持可达。历史先例见 [RELEASE-HISTORY.md](docs/planning/RELEASE-HISTORY.md)「发布基线说明」对 `v3.0.0`/`v3.0.1`（提交 `b7ebabb`）的处置——tag 保留不动、承载分支不删、版本号不再复用。
- 指向非 `main` 提交的 tag 视为历史标签：不补建 Release、不复用版本号，其承载分支不得删除。
- Android 最低支持机型随 `minSdk` 列明（当前 minSdk 26，即 Android 8 下界）；提升 `minSdk` 视为破坏兼容性，走 `MAJOR` 或显式弃用流程。

### 弃用策略（Deprecation）

协议、API 或用户可见行为的弃用必须**先标记、后移除**，给予至少一个 `MINOR` 周期的兼容期：

1. **标记**：在 `CHANGELOG.md` 与 [docs/api/API.md](docs/api/API.md) 标注 `@deprecated`，写明替代方案与拟移除版本；
2. **共存**：新版本与被弃用项同时可用，客户端按[兼容性矩阵](#兼容性客户端服务端协议)容忍；
3. **移除**：到 sunset 版本移除；移除即 `MAJOR`。

禁止「标记与移除同版本」——会破坏已发布的客户端。

### 发布节奏与冻结期

- 采用**时间为主、功能为辅**的节奏：`MINOR` 按固定周期切片（目标见 [ROADMAP.md](docs/planning/ROADMAP.md)），切片内未完成的能力顺延，而非阻塞整次发版；
- 发布前进入**代码冻结**：冻结期内仅接受 release-blocking 修复，新功能改入下一切片；
- 法定节假日/重大发布设立**发布冻结**，冻结期内仅安全与阻断性修复。

### 供应链与可追溯（SLSA 对齐）

发布制品按供应链成熟度分阶，**已落地项不得回退**：

| 维度 | 当前基线（已落地） | 目标 |
| --- | --- | --- |
| Git tag | annotated tag（`git tag -a`） | 带签名 tag（GPG / sigstore） |
| 制品签名 | `apksigner verify` 强制，缺正式密钥回退 debug 签名 | 正式密钥入 Secrets，CI 全量正式签名 |
| 制品校验和 | `SHA256SUMS` | + SBOM（CycloneDX/SPDX）+ provenance attestation |
| 构建环境 | CI 构建，[release.yml](.github/workflows/release.yml) 校验三处版本一致 | SLSA Build L3（可重现、有出处证明） |
| 制品不可变 | Release 制品不替换，有问题发新 tag | 同左，强约束 |

- `versionCode` 全局单调递增，禁止复用已发布编号；同一设备长期升级须固定签名来源（正式签名或已配置 Secrets 的 Release 制品），混用须卸载重装（见 [RELEASE-HISTORY.md](docs/planning/RELEASE-HISTORY.md) 制品勘误）。
- 版本号变更**单独成提交**（`chore(release): vX.Y.Z`），不与功能混在同一 PR——便于二分与回滚。

发布使用带注释的 Git tag：

```bash
git tag -a vX.Y.Z -m "release: vX.Y.Z"
git push origin vX.Y.Z
```

版本标签一经推送不得删除或移动；指向非 `main` 提交的 tag 不得补建 Release、不得复用版本号。
- **标签唯一性**：一个提交不得承载多个版本标签（历史反例：v3.0.0 与 v3.0.1 曾指向同一提交，见 [FOLLOW-UP-PROCESS-AUDIT.md](docs/planning/FOLLOW-UP-PROCESS-AUDIT.md) 第 2 节豁免线）。
- **发布提交同样走 PR**：`chore(release): vX.Y.Z` 的版本 bump 在 `release/vX.Y.Z` 分支提交并经 PR 合入，禁止在主检出直推。
- **main 历史线性**：合并一律 Squash（每个 main 提交携带 `(#N)`）；本地不得产生进入 main 的 merge commit。

### 发布顺序

1. 从 `main` 创建 `release/vX.Y.Z`，冻结功能并更新 [CHANGELOG.md](CHANGELOG.md)；
2. 以发布标签 `vX.Y.Z` 为规范版本；Android `versionName` 用 `X.Y` 展示值，单调递增 `versionCode`，服务端 `package.json` 用完整 `X.Y.Z`；
3. 通过完整 CI、发布验收与安全检查；
4. 合并到 `main` 后创建带注释、不可移动的 `vX.Y.Z` 标签；
5. 由 [release.yml](.github/workflows/release.yml) 根据标签构建制品与 Release，记录 `SHA256SUMS` 与 `apksigner` 证书；
6. 发布后观察关键指标，出现问题优先回滚制品；修复代码再通过 hotfix 发布。

### 发布验收

CI 只能证明「编译通过、逻辑符合契约」，**不能证明「在真实厂商 ROM 上可用」**。触及下列范围的变更，必须在真机完成验收后才能发正式版；否则只允许发 `X.Y.Z-rc.N` 预发布版本：

- 触及范围：无障碍服务、快捷磁贴、厂商保活跳转、`Intent` 跳转、后台任务调度、系统权限；
- 最低机型覆盖：Android 8（minSdk 26 下界）与当前最高 targetSdk 机型；
- 厂商覆盖：MIUI、HarmonyOS/EMUI、ColorOS、OriginOS（保活入口表覆盖的厂商逐一验证跳转与降级）；
- 验收项：磁贴添加与移除、冷进程下启用/禁用状态显示、磁贴启停服务、跳转系统设置、各厂商入口可用性与通用降级路径。

## 纯工程变更不需要版本号

只改动构建配置、测试、文档、CI 或内部重构（不产生用户可见能力）时，**不升版本号、不占用 `X.Y.Z`**，在 [docs/planning/ROADMAP.md](docs/planning/ROADMAP.md)「候选池」登记即可。这与「按是否产生用户可见能力切分小版本」的版本原则一致，避免工程支出稀释版本序列。

## 每次发布完成后的最小核对清单

- [ ] `CHANGELOG.md` 已新增版本条目；
- [ ] [Release list](docs/planning/RELEASE-HISTORY.md#release-list) 已记录版本、tag、合并提交和 Release 状态；
- [ ] tag 为 annotated tag，且指向合并后的 `main` 提交；
- [ ] GitHub Release 已创建并上传制品与 `SHA256SUMS`；
- [ ] **制品已用 `apksigner verify` 校验签名并确认可安装**——未签名包在手机上必然报「解析软件包时出现问题」（Issue #23）；命令见 [DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md#apk-安装排障)；
- [ ] 触及系统集成范围（无障碍/磁贴/跳转/调度/权限）时，真机验收已完成并记录机型与 ROM 版本；
- [ ] 已确认没有复用或移动历史 tag；
- [ ] 触及协议或弃用项时，[docs/api/API.md](docs/api/API.md) 已标注 `@deprecated` 或 `schemaVersion` 变更，并保留至少一个 `MINOR` 兼容期；
- [ ] 若为覆盖验收的例外发布或 P0 修复，无指责复盘（postmortem）已补并链接至 [RELEASE-HISTORY.md](docs/planning/RELEASE-HISTORY.md)。

## 紧急变更与回滚

生产故障从最新生产标签创建 `hotfix/<id>-<简述>`，PR 描述必须包含事故编号、影响范围、缓解措施和回滚点。紧急变更仍需至少一名维护者批准并通过最小 CI；发布后必须补齐完整测试、变更记录和复盘，并**双向回合**——修复同时合入 `release/*`（发修复版本）与 `main`（避免主干再丢失），见[发布分支纪律](#发布分支纪律)。

回滚优先选择已验证的上一版本制品（回到最近一个 green tag）或 `git revert`（生成新提交），禁止在受保护分支上 reset、force-push 或删除历史标签。涉及数据库或协议的回滚必须提供向后兼容方案。**任何 P0 事故或覆盖验收的例外发布都必须补一份无指责复盘（blameless postmortem）**：时间线、根因、行动项，链接记入 [RELEASE-HISTORY.md](docs/planning/RELEASE-HISTORY.md) 对应版本行。

## 仓库管理员配置

远端仓库应对 `main` 启用以下保护：

- 配置 `main`、`release/*` 分支保护和 CODEOWNERS；
- 配置必需状态检查、审批人数、旧审批失效和合并后删分支；将 `ktlintCheck` 纳入必需状态检查；
- 禁止管理员绕过（bypass）——留后门等于没有门禁；
- 开启 Dependabot/Renovate，依赖升级走 PR 并保留 lockfile；
- 发布凭据使用 CI Secret/OIDC，禁止写入仓库和日志；
- 版本 tag 目标为带签名（GPG / sigstore），CI 制品目标生成 SBOM 与 provenance attestation（见[供应链与可追溯](#供应链与可追溯slsa-对齐)）；
- 制品保留周期与回滚窗口明确：至少保留最近两个 stable 与全部 rc，便于回到最近 green tag；
- Release、APK、日志和测试报告保留周期明确，生产制品可追溯到 commit/tag。

## 命令与清单的唯一事实源

本文件是**流程规范**（该做什么、什么条件下允许合并与发版），不复述命令清单——命令会随工具链版本漂移，复制出去的清单会静默变成第二份真相。各命令的唯一事实源：

| 内容 | 唯一事实源 |
| --- | --- |
| 门禁命令（提交前必须自跑的五条） | [AGENTS.md](AGENTS.md)「验证与合并」 |
| 目录/产物/分支名的机器判定规则 | `ProjectStructureTest` / `RepoHygieneTest`（代码即事实源） |
| 本机工具链、SDK、JDK 与排障步骤 | [docs/development/DEV-ENVIRONMENT.md](docs/development/DEV-ENVIRONMENT.md) |
| 目录/架构契约的判定细则 | [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) 第 2.1/2.2 节 |
| 并行协作的隔离与交接细则 | [docs/development/AGENT-WORKFLOW.md](docs/development/AGENT-WORKFLOW.md) |

> 新增命令一律加到上表对应的事实源，本文件只保留指针。
