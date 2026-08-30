# AdSkip 服务端 API 参考

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
// 200
{
  "schemaVersion": 1, "version": 3, "hash": "sha256:...", "updatedAt": "...",
  "rules": { "globalKeywords": ["跳过"], "globalViewIds": ["skip"], "apps": {}, "disabled": [] }
}
```

响应头：`ETag: <hash>`

### PUT /api/v1/rules  `[admin]`

发布规则包（旧包自动备份轮转，保留 5 份）。

```json
{ "keywords": ["跳过"], "viewIds": ["skip"], "packages": { "com.x": { "keywords": ["..."], "viewIds": ["..."], "disabled": false } } }
```

校验：包名须匹配 `^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$`、关键词 ≤12 字、总条目 ≤2000、应用数 ≤2000。响应 `{"ok":true,"version":N,"hash":"sha256:..."}`

### POST /api/v1/rules/test  `[admin]`

规则模拟器——与客户端 `RulesRepository.ruleSetFor(pkg)` 同源：全局 + 应用专属关键词/ViewID 合并匹配，禁用开关置 `hit=false`。

```json
// 请求 { "pkg": "com.x", "text": "跳过广告", "viewId": "com.x:id/skip" }
// 响应 { "hits": [{"match":"keyword","keyword":"跳过","field":"text"}], "hit": true, "disabled": false }
```

### POST /api/v1/reports/batch

批量补报（读体前先按 IP 预检限流）。

```json
{ "deviceId": "≥8字符", "events": [ { "pkg": "com.x", "channel": "text|viewId", "ts": 123 } ] }
```

最多 50 条，非法包名事件被静默跳过。响应 `{"ok":true,"accepted":N}`

### GET /api/v1/stats/summary

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

## v0 兼容协议（旧客户端，形状不变）

| 路由 | 说明 |
|---|---|
| `GET /api/rules/latest` | 旧形状规则包（无 hash/ETag） |
| `PUT /api/rules` `[admin]` | 发布（与 v1 同一校验管线） |
| `POST /api/skip` | 单条上报——已与 v1 同源校验：包名须符合 PKG_RE（非法 `400`）、支持 channel 字段 |
| `GET /api/stats/summary` | 同 v1 响应 |

## 静态资源

| 路由 | 说明 |
|---|---|
| `GET /` | 产品落地页 |
| `GET /admin` | 管理后台（登录 + diff 预览 + 规则模拟器 + 统计看板） |
| `GET /download` | 下载 APK（`APK_FILE`，默认仓库根 `AdSkip-latest.apk`） |
