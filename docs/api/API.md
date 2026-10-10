# AdSkip 服务端 API 参考（API）

基础地址：`http://<本机IP>:3210`（局域网）或 `http://localhost:3210`（本机，注意 IPv6 解析回退建议用 `127.0.0.1`）。

## 通用约定

- **请求体**：所有带请求体的端点必须显式声明 `Content-Type: application/json`，否则返回 `415`（阻断跨站表单伪造）
- **请求体上限**：1 MiB（`MAX_BODY`，env 可覆盖）。超限在协议层被拒绝——keep-alive 连接返回 `413`，冷连接直接断开 socket；超限正文不会进入应用内存
- **JSON 限制**：键数 ≤ 100、嵌套深度 ≤ 5，越界返回 `400`
- **鉴权**：管理端点需要 `Authorization: Bearer <ADMIN_TOKEN>`；未配置 `ADMIN_TOKEN` 时管理端点返回 `503`；令牌比较为常数时间实现
- **限流**（内存令牌桶，空闲桶 5 分钟回收）：读 120 次/分/IP、写 10 次/分/IP、上报 30 次/分/deviceId（deviceId 缺失时按 IP）；超限 `429`；批量上报在读体之前先做 IP 级预检
- **CORS**：配置 `CORS_ORIGINS` 白名单时，命中回显 Origin、不命中不发送该头；未配置时默认 `*`
- **错误格式**：`{"error": "<信息>"}`
- **状态码**：`400` 载荷非法 / `401` 未鉴权 / `404` 不存在 / `413` 请求体超限 / `415` 类型错误 / `429` 限流 / `503` 未配置令牌

## v1 协议（当前）

### GET /api/v1/rules/latest

下发规则包。带 `If-None-Match: <hash>` 命中时返回 `304`（零正文，省流量）。

```json
{
  "schemaVersion": 2, "version": 3, "hash": "sha256:...", "updatedAt": "...",
  "rules": { "globalKeywords": ["跳过"], "globalViewIds": ["skip"], "globalSelectors": ["[desc*=\"跳过\"] [click=\"true\"]"], "apps": {}, "disabled": [] }
}
```

`selectors` 为**纯增量字段**（schema 2 引入）：`MIN_SCHEMA_VERSION` 保持 1，不识别的旧客户端会照常加载并忽略它。客户端对每条选择器做一次编译，编译失败的条目静默丢弃（见下方「选择器校验的两端分工」）。

响应头：`ETag: <hash>`

### PUT /api/v1/rules  `[admin]`

发布规则包（旧包自动备份轮转，保留 5 份）。

```json
{ "keywords": ["跳过"], "viewIds": ["skip"], "selectors": ["[vid$=\":id/skip_view\"]"], "packages": { "com.x": { "keywords": ["..."], "viewIds": ["..."], "selectors": ["..."], "disabled": false } } }
```

校验：包名须匹配 `^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$`、关键词 ≤12 字、总条目 ≤2000、应用数 ≤2000。响应 `{"ok":true,"version":N,"hash":"sha256:..."}`

### 选择器校验的两端分工

| 环节 | 服务端（`isValidSelector`） | 客户端（`SelectorParser`） |
| --- | --- | --- |
| 长度 | 整条 ≤ `MAX_SELECTOR_LEN = 256` | 整条 ≤256、单个 value ≤64 |
| 字符 | 白名单 `[\p{L}\p{N}_\s.^$+*>\[\]"'=:\/-]`（放行 Unicode 字母数字，否则中文关键词场景会被整条丢弃） | 不做字符白名单，只认文法 token |
| 结构 | 引号个数、方括号个数配平（快检，不校验顺序） | 完整文法：key ∈ `text/desc/vid/click`、运算符 ∈ `= * ^ $`、组合符、最多 4 段 compound |
| 不过时 | 丢弃该条 | 丢弃该条（静默） |

**服务端是必要不充分的快检，语法权威在客户端。** 两端判定相同的向量固化在 `server/test/fixtures/selectors.contract.json`，由 `server/test/selectors.contract.test.ts`（服务端一侧）与 `client/app/src/test/java/com/qingqi/adskip/engine/SelectorContractTest.kt`（客户端一侧）共同消费；有意判定不同的向量记在夹具的 `divergences` 段并写明原因，防止「有意的差异」被后续改动悄悄抹平。

### POST /api/v1/rules/test  `[admin]`

规则模拟器——与客户端 `RulesRepository.ruleSetFor(pkg)` 同源：全局 + 应用专属关键词/ViewID/选择器合并匹配，禁用开关置 `hit=false`。

```json
// 请求 { "pkg": "com.x", "text": "跳过广告", "viewId": "com.x:id/skip" }
// 响应 { "hits": [{"match":"keyword","keyword":"跳过","field":"text"}], "hit": true, "disabled": false, "selectorNotes": [] }
```

选择器通道是**近似判定**：服务端没有节点树，凡是需要上下文关系的表达式（组合符）、或依赖 `desc` / `click` 属性的规则，服务端判不了，会原样列入 `selectorNotes`（`{rule, reason}`）而不是伪装成「未命中」。只有单个 compound、只用 `text`/`vid` 且样本齐备时才会给出 `match: "selector"` 命中；比较语义与客户端 `SelectorMatcher` 对齐（trim + 小写，`=` 全等 / `*` 包含 / `^` 前缀 / `$` 后缀，`text` 样本超 64 字符直接不匹配）。

### POST /api/v1/reports/batch

批量补报（读体前先按 IP 预检限流）。`deviceId` 由客户端首次运行时生成并持久化，至少 8 个字符；`ts` 使用 Unix 毫秒时间戳。

```json
{ "deviceId": "≥8字符", "events": [ { "pkg": "com.x", "channel": "text|viewId", "ts": 123 } ] }
```

最多 50 条，非法包名事件被静默跳过。响应 `{"ok":true,"accepted":N}`

### GET /api/v1/stats/summary

公开读取接口，不需要管理令牌。

```json
{ "total": 0, "today": 0, "byDay": [{"day":"2026-08-30","count":0}], "byApp": [{"pkg":"com.x","label":"X","count":0}], "recent": [] }
```

`byDay` 最近 14 天；`recent` 跨天取最近 50 条（服务端缓存汇总，上报后自动失效）。

### GET /api/v1/health

`{"status":"ok","timestamp":"..."}`

### GET /api/v1/admin/logs  `[admin]`

内存访问日志环形缓冲（最近 200 条，含方法/路径/状态码/IP/耗时）：

```json
{ "entries": [ { "ts": "...", "method": "GET", "path": "/api/v1/rules/latest", "status": 200, "ip": "192.168.1.5", "ms": 1 } ] }
```

## 协议演进

| `schemaVersion` | 状态 | 变更 |
| --- | --- | --- |
| `1` | 历史 | v1 载荷（`globalKeywords` / `globalViewIds` / `apps` / `disabled`） |
| `2` | **当前** | 新增 `selectors` 字段（`rules.globalSelectors` + `apps.*.selectors`）；`MIN_SCHEMA_VERSION` 保持 1 |

兼容约定：服务端按 `MIN` 校验、客户端按 `schemaVersion` 决定是否解析新字段；选择器是纯增量字段，旧客户端照常加载并忽略，停发即回退 schema 1 行为。存量 `rules.json` 的 `schemaVersion` 低于当前值时由兼容层单向补齐（`hash` 的计算输入含 `schemaVersion`，客户端会因此多同步一次，属预期）。语法、校验上限与契约夹具见 [DESIGN-PHASE1-SELECTOR.md](../planning/DESIGN-PHASE1-SELECTOR.md)（步骤 C）。

## v0 兼容协议（旧客户端，形状不变）

| 路由 | 说明 |
| --- | --- |
| `GET /api/rules/latest` | 旧形状规则包（无 hash/ETag）；平铺字段含 `selectors`，管理后台按此形状编辑 |
| `PUT /api/rules` `[admin]` | 发布（与 v1 同一校验管线） |
| `POST /api/skip` | 单条上报——已与 v1 同源校验：包名须符合 PKG_RE（非法 `400`）、支持 channel 字段 |
| `GET /api/stats/summary` | 同 v1 响应 |

## 静态资源

| 路由 | 说明 |
| --- | --- |
| `GET /` | 产品落地页（服务端把 `{{APP_VERSION}}` 占位符替换为 `server/package.json` 的版本号，避免文案与发布版本漂移） |
| `GET /admin` | 管理后台（登录 + diff 预览 + 规则模拟器 + 统计看板） |
| `GET /download` | 下载 APK（`APK_FILE`，默认仓库根 `AdSkip-latest.apk`） |

`/download` 找不到 APK 时返回 `404`。可通过 `APK_FILE` 环境变量指定绝对路径或相对服务端工作目录的文件路径。
