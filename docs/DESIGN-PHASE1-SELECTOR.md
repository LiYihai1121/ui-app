# Phase 1 技术方案：选择器引擎（DESIGN-PHASE1-SELECTOR）

> 实现 [ROADMAP-ADS.md](ROADMAP-ADS.md) Phase 1（L1 无障碍引擎增强）的详细设计。
> 状态：已评审通过（2026-09-24），步骤 A/B 实现中（PR #11）；目标里程碑 M1（v3.1）；最后更新：2026-09-24。

## 1. 目标与非目标

**目标：**

1. 在现有「文本关键词 + ViewID」双通道之上增加**第三通道：选择器规则**（类 CSS 选择器子集），支持节点上下文关系匹配；
2. 规则编写从「靠猜」变为「靠快照」：App 内一键导出当前界面节点树 JSON；
3. 协议扩展到 `schemaVersion = 2`（`MIN` 保持 1，新旧客户端与服务端双向兼容）；
4. 点击结果校验：疑似被广告 SDK 劫持跳转时，本地拉黑该规则并回退。

**非目标（本期不做，防止语法失控）：**

- ❌ 正则表达式匹配、`:not()`/`:has()`/`:nth-child()` 伪类；
- ❌ 通用兄弟选择器 `-`、跨窗口/跨 Activity 匹配；
- ❌ 点击位置偏移、长按/滑动等非 click 动作（沿用现有点击 + 坐标手势兜底）；
- ❌ 完整 CSS 语法兼容（明确只做子集，语法以本文档为准）。

## 2. 现状与差距

| 现状（v3.0） | 差距 |
| --- | --- |
| `SkipRuleEngine`：DFS（≤500 节点）+ 文本/ViewID 子串匹配 | 无上下文关系，无法表达「某容器内的跳过按钮」，误匹配率高 |
| `AdNode` 仅有 `children()` / `clickableParent()` | **无 `parent()` 访问**，无法向上匹配祖先；无法取前序兄弟 |
| `RuleSet.schemaVersion = 1`，`MIN = 1` | 规则结构无选择器字段 |
| 服务端 `validate.ts` 只清洗 keywords/viewIds（≤12/256 字符） | 无选择器校验与长度上限 |
| `SyncClient` 按 v1 JSON 解析，未知字段忽略 | 需解析新字段并编译 |
| 规则编写无工具 | 需快照导出能力 |

## 3. 选择器语法（子集规范）

### 3.1 文法

```text
selector   := compound ( WS combinator WS? compound )*
combinator := '>' | '+'              ; 缺省组合符为后代（空格）
compound   := simple+                ; 相邻 simple 之间为逻辑与（AND）
simple     := '[' attr ']'
attr       := key op '"' value '"' | key  ; 简写 [key] ≡ 存在性判断
key        := 'text' | 'desc' | 'vid' | 'click'
op         := '=' | '*=' | '^=' | '$='
value      := 除 '"' 外的可见字符（长度 ≤ 64，见 3.3 限制）
```

- **属性**：`text`（文本）、`desc`（contentDescription）、`vid`（viewIdResourceName）、`click`（是否可点击，值仅 `"true"`/`"false"`）；
- **运算符**：`=` 全等、`*=` 包含、`^=` 前缀、`$=` 后缀；
- **组合符**：空格 = 后代、`>` = 直接子节点、`+` = 相邻前一个兄弟；
- 所有字符串比较**忽略大小写**（与现有关键词通道行为一致）。

### 3.2 示例

```text
[vid$=":id/skip_view"]                   ; 等价现有 ViewID 规则的选择器表达
[text*="跳过"][click="true"]             ; 含「跳过」且可点击
[text*="跳过"] > [vid$="id/tv_skip"]     ; 跳过文本的直接子节点
[desc^="跳过"] + [vid$="id/iv_close"]    ; 跳过描述右侧相邻的关闭图标
[desc*="跳过"] [click="true"]            ; 跳过容器内任意可点击节点（兜底主力）
```

### 3.3 安全与复杂度硬限制

| 限制 | 值 | 执行位置 |
| --- | --- | --- |
| 单条表达式长度 | ≤ 256 字符 | 服务端 + 客户端 |
| 单应用/全局选择器条数 | 各 ≤ 128 | 服务端清洗 + 客户端兜底 |
| value 匹配时 text/desc 长度 | ≤ 64（超长直接不匹配，防扫大段文本） | 客户端匹配器 |
| 组合符链深度 | ≤ 4 个 compound | 客户端解析器 |
| 编译失败策略 | **丢弃该条 + LogRing 告警，绝不抛异常**（fail-safe） | 客户端解析器 |
| 节点预算 | 与现有通道共享 `maxNodes = 500` | 引擎 |

## 4. 模块设计（客户端）

新增包 `com.ldp.adskip.engine.selector`，保持引擎层纯 JVM 可测、零第三方依赖：

```text
engine/selector/
├── SelectorAst.kt        # 数据结构：CompoundSelector / SimpleSelector / AttrMatcher / Combinator
├── SelectorParser.kt     # 字符串 → AST；失败返回 null（不抛异常）
├── SelectorMatcher.kt    # AST × AdNode 求值（右到左匹配，见第 5 节）
└── (测试) SelectorParserTest / SelectorMatcherTest
```

### 4.1 `AdNode` 接口扩展（唯一接口破坏性改动，同步改 `FrameworkAdNode` + `FakeAdNode`）

```kotlin
interface AdNode {
    // ...现有成员不变...
    val parent: AdNode?            // 新增：父节点（FrameworkAdNode 包装 node.parent）
    fun previousSibling(): AdNode? // 新增：相邻前一个兄弟（见 5.3 实现约束）
}
```

### 4.2 `RuleSet` / `SkipRuleEngine` 改动

```kotlin
data class RuleSet(
    val keywords: List<String>,
    val viewIds: List<String>,
    val selectors: List<SelectorAst> = emptyList(), // 新增：已编译选择器（加载时编译一次）
    val disabled: Boolean = false,
    val schemaVersion: Int = SCHEMA_VERSION          // SCHEMA_VERSION = 2，MIN_SCHEMA_VERSION = 1
)
```

- `SkipRuleEngine.findTarget` 保持**单次 DFS、单节点预算**；每节点按序测试：**① 选择器 → ② 文本 → ③ ViewID**（同一 DFS 序内先命中先返回，顺序确定性写入 KDoc）；
- 选择器通道复用现有前置守卫：`isVisible`、`!isEditable`；
- `matches()` 保留原两通道语义不变（存量单测不破坏）。
- **实现注记（2026-09-24 评审）**：`SCHEMA_VERSION=2` 随步骤 C（协议 v2）落地时再升；步骤 A/B 先引入 `selectors` 字段、版本保持 1——提前声明 2 无服务端配合且会让存量断言失真。另：`RuleSet.isEmpty` 须计入 `selectors`。

## 5. 匹配算法

### 5.1 右到左求值（标准 CSS 方向）

对每个候选节点（作为最右 compound 的匹配点）递归向左验证：

```text
match(node, i):                          # i = 从右往左的 compound 下标
  if not compounds[i].matches(node): return false
  if i == 0: return true
  c = combinators[i-1]                   # compounds[i-1] 与 compounds[i] 之间的组合符
  return when (c):
    DESCENDENT -> 沿 parent 链向上，任一祖先 match(i-1) 即 true
    CHILD      -> node.parent 非空且 match(parent, i-1)
    PREV_SIB   -> node.previousSibling() 非空且 match(sib, i-1)
```

- 任一环节解析不到（parent/sibling 为 null）→ **匹配失败（不点击）**，天然 fail-safe；
- 候选遍历复用现有 DFS：先到的节点先测，预算耗尽即止。

### 5.2 性能预算

- 每节点成本 = 条数 ≤128 × 最右 compound 的属性检查（短路求值，`click`/`vid` 先于 `text*=`）；
- 目标：中端机 128 条规则、500 节点预算下单次 `findTarget` **p99 < 8ms**（现有 150ms 节流窗口内）；
- 编译只发生在 `applyCloudRules`（IO 线程）与本地编辑保存时，事件热路径零解析。

### 5.3 兄弟节点实现约束（已知风险）

`FrameworkAdNode.previousSibling()` 经 `parent` 子列表定位前一个节点，依赖 `AccessibilityNodeInfo.equals`（API 21+ 已实现）。**实现期核实：`indexInParent` / `sourceNodeId` 均非公开 API**，原「按 indexInParent 缓存下标」回退路径不存在——equals 即唯一身份语义（实现即本文档的主路径）。若真机验证 equals 不可靠：直接按预案下线 `+` 组合符（语法保留、匹配恒 false），不影响其余规则。**验收前必须真机抽测 3 个 App**。

## 6. 协议扩展（schemaVersion 2）

### 6.1 载荷形状（v1 路由原地扩展，未知字段对旧客户端天然无害）

```jsonc
// GET /api/v1/rules/latest
{
  "schemaVersion": 2,              // MIN=1 的旧客户端照常接受并忽略 selectors
  "rules": {
    "globalKeywords": [...],
    "globalViewIds":  [...],
    "globalSelectors": ["[desc*=\"跳过\"] [click=\"true\"]"],   // 新增
    "apps": {
      "com.example.app": {
        "keywords": [...], "viewIds": [...], "disabled": false,
        "selectors": ["[vid$=\":id/skip_view\"]"]              // 新增
      }
    },
    "disabled": [...]
  },
  "hash": "...", "version": 0, "updatedAt": "..."
}
```

- 服务端 `types/rules.ts`：`AppRule.selectors: string[]`、`RuleSetV1.globalSelectors: string[]`（发布/清洗全链路）；
- 客户端 `SyncClient`：读取两个新字段 → 存 Prefs → `RulesRepository` 合并时**编译**（编译失败丢弃该条）；
- 兼容矩阵：

| 客户端 \ 服务端 | 发布 schema 1 | 发布 schema 2 |
| --- | --- | --- |
| 旧客户端（MIN=1，不识 selectors） | 正常 | **正常**（org.json 忽略未知字段） |
| 新客户端 | 正常（selectors 为空） | 完整选择器能力 |

### 6.2 服务端校验（`validate.ts` 扩展）

```ts
// config 新增
MAX_SELECTOR_LEN = 256; MAX_SELECTORS_PER_LIST = 128;

// cleanSelectors(value): string[]
// 1) 长度与条数上限  2) 字符白名单 [\w.$:=" *^$>\[\]'+-]
// 3) 括号/引号配平快检  4) 去重（大小写不敏感）
// 注意：服务端不做完整文法解析（避免双实现漂移），深度校验由客户端兜底
```

- **契约测试防漂移**：新增共享夹具 `server/test/fixtures/selectors.contract.json`（合法/非法向量各 ≥12 条），**bun test 与 Gradle JVM 单测共同消费**——同一批向量两端必须得到一致的「接受/拒绝」判定（客户端以「编译成功」对应「接受」）；
- 管理后台 `admin.html`：应用规则编辑区增加「选择器（每行一条）」文本域，随 diff 预览展示。

## 7. 点击结果校验（防劫持回退）

**问题**：个别广告 SDK 检测到点击后直接 `startActivity` 跳转应用市场/浏览器（劫持），此时点击没跳过广告反而帮广告主转化。

**方案**（`SkipAdService` 内新增状态机，纯内存 + 持久黑名单）：

```text
点击成功（任一通道） → 记录 PendingClick(pkg, ruleKey, at)
    ↓ 下一个 TYPE_WINDOW_STATE_CHANGED（≤600ms）
event.pkg ∈ {pkg, systemui, 自身} 或 超时  → 无事（广告页关闭属正常窗口切换）
event.pkg 为其他应用                        → 判定劫持：
      RuleBlocklist.add(ruleKey)            # ruleKey = "sel:<expr>" / "txt:<kw>" / "vid:<id>"
      rulesRepo.invalidate()                # 立即生效
      LogRing.w("Hijack", ...)              # 日志页可观察
```

- `data/RuleBlocklist.kt`：Prefs 持久化 `Set<String>`，**LRU 上限 200**，设置页提供「清除本地规则黑名单」入口；
- 黑名单在 `RulesRepository.ruleSetFor` 合并时过滤（按**值**不按版本，云规则重复下发同值规则仍被拦）；
- 现有 `SafetyGuard`（点击**前**硬拦截）不变，本机制是点击**后**观测兜底，两者互补；误杀可通过清黑名单即时恢复。

## 8. 节点快照工具

- **入口**：设置页新增「导出界面快照（调试）」按钮（无障碍运行中可用）；
- **实现**：`service/NodeSnapshot.kt` —— 从 `rootInActiveWindow` 前序 DFS（沿用 500 节点/30 深度上限）序列化：

```jsonc
{ "pkg": "com.example.app", "ts": 1730000000000, "screen": "SplashActivity",
  "nodes": [ { "d": 0, "cls": "FrameLayout", "text": "", "desc": null,
               "vid": null, "b": [0,0,1080,2400],
               "flags": { "click": false, "vis": true, "edit": false } } ] }
```

- 文本/描述截断 40 字符；总量超 100KB 截断尾部节点并标注 `truncated: true`；
- 复用日志页既有「分享为文本」导出通道（零新增网络面）；**仅用户手动触发、仅当前屏幕**，不自动上传。

## 9. 改动清单

| 层 | 文件 | 改动 |
| --- | --- | --- |
| engine | `selector/{SelectorAst,SelectorParser,SelectorMatcher}.kt` | 新增（纯 JVM） |
| engine | `AdNode.kt` / `FrameworkAdNode.kt` | +`parent` / `previousSibling()` |
| engine | `RuleSet.kt` | +`selectors`、`SCHEMA_VERSION=2` |
| engine | `SkipRuleEngine.kt` | 第三通道接入 + 通道优先级 |
| data | `Prefs.kt` | selectors 存取 + 黑名单键值 |
| data | `RulesRepository.kt` | 编译缓存、黑名单过滤、合并链路 |
| data | `RuleBlocklist.kt` | 新增 |
| net | `SyncClient.kt` | 解析 `globalSelectors` / `apps.*.selectors` |
| service | `SkipAdService.kt` | PendingClick 状态机 |
| service | `NodeSnapshot.kt` | 新增 |
| ui | `settings/SettingsScreen.kt` + VM | 快照导出、清黑名单入口（2 个按钮） |
| server | `types/rules.ts` / `validate.ts` / `config.ts` / `storage/store.ts` | selectors 字段全链路 |
| server | `public/admin.html` | 选择器编辑文本域 |
| server | `test/fixtures/selectors.contract.json` + 单测/冒烟 | 契约夹具 + ≥8 例 |
| docs | `docs/API.md` / `ARCHITECTURE.md` | schema v2 与第三通道说明 |

**不动**：`SafetyGuard`（判定语义只增不改）、v0 路由、上报协议、`SyncJobService`。

## 10. 实施顺序（对应 ROADMAP-ADS 第 3~8 周）

| 步骤 | 周 | 内容 | 出口条件 |
| --- | --- | --- | --- |
| A | W3~W4 | AST + 解析器 + 匹配器 + `AdNode` 扩展（纯 JVM） | 解析/匹配单测全绿 |
| B | W4~W5 | `RuleSet`/引擎/`RulesRepository`/`Prefs` 集成 | 存量单测不回归，新增引擎集成测试绿 |
| C | W5~W6 | 协议 v2：服务端字段 + 校验 + 契约夹具 + 管理后台 | `bun test` + `bun run typecheck` 绿 |
| D | W6 | `SyncClient` 解析 + 点击结果校验状态机 + 黑名单 | 劫持场景单测绿 |
| E | W7 | 快照工具 + 设置页入口 | 真机导出 JSON 可读 |
| F | W7~W8 | Top 30 App 规则编写 + 真机回归 + 性能采样 | 验收指标（下节）全达标 |

## 11. 测试计划与验收

**测试（目标新增 ≥ 60 例 JVM + ≥ 8 例 bun）：**

- 解析器：合法 15 例（含嵌套与/链式组合）+ 非法 15 例（缺括号/超深/超长/未知 key/未知运算符 → null）；
- 匹配器：每运算符 × 每组合符正反例 ≥ 20 例（含 parent 为 null、兄弟越界、超长 text 不匹配）；
- 引擎集成：通道优先级、预算截断、disabled 短路、黑名单过滤 ≥ 8 例；
- 点击校验：正常关页 / 外跳劫持 / 超时 / 黑名单 LRU ≥ 5 例；
- 契约夹具：两端各 24 向量一致判定；
- 服务端：`cleanSelectors` 边界 ≥ 8 例 + 冒烟路由带 selectors 字段。

**验收（与 ROADMAP-ADS Phase 1 一致）：**

1. Top 30 App 实测开屏跳过率 ≥ 95%；
2. 误触率 = 0（SafetyGuard 拦截日志全量复核 + 点击校验无误杀）；
3. `cd client && ./gradlew assembleDebug`、`./gradlew testDebugUnitTest`、`cd server && bun test`、`bun run typecheck` 全绿；
4. 性能：128 条规则下 `findTarget` p99 < 8ms（中端真机 LogRing 采样）；
5. 兼容：旧客户端对 schema 2 载荷行为不变（冒烟用例固化）。

## 12. 风险与回滚

| 风险 | 对策 |
| --- | --- |
| 语法蔓延成「第二个 GKD」 | 非目标白纸黑字（第 1 节）；新增语法必须改本文档 + 新增契约向量 |
| 客户端/服务端校验漂移 | 共享契约夹具双端消费；服务端只做轻量校验，文法权威在客户端 |
| `AccessibilityNodeInfo.equals` 兄弟定位不可靠 | 5.3 双保险 + 真机抽测；失败仅下线 `+` 组合符 |
| 选择器误匹配导致误触 | 前置守卫复用 + SafetyGuard 终审 + 点击校验兜底（三层防线） |
| 性能劣化 | 单遍 DFS 共享预算；编译期求值；p99 指标纳入验收 |
| 回滚 | 选择器为纯增量字段：服务端停发 `selectors` 即回退 v1 行为；客户端可整体 revert，无数据迁移 |
