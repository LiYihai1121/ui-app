# AdSkip 后续计划

> 本文档记录项目短板修复、安全加固与技术补充的后续计划。
> 优先级按「安全/稳定性 → 架构/设计 → 工程化/可观测」排列。

## 已修复（2026-10-08）

| # | 问题 | 修复内容 | 文件 |
|---|------|----------|------|
| 1 | `reportSkip` 单参重载硬编码 `deviceId="pending"` | 改为直接走 v0 单条上报，避免伪造 deviceId | `SyncClient.kt` |
| 2 | `usesCleartextTraffic="true"` 默认明文传输 | 明文策略显式收口到 `network_security_config.xml`；NSC `<domain>` 不支持私有网段前缀（10./172.16./192.168. 无效），base-config 放行明文（行为与旧版等价），HTTPS 迁移后收紧 | `AndroidManifest.xml`, `res/xml/network_security_config.xml` |
| 3 | 网络请求裸创建 `Thread` | 统一改用 `AppExecutors.io.execute` | `SyncClient.kt` |
| 4 | `syncRules` 与 `syncRulesSilently` JSON 解析重复 ~70 行 | 提取 `parseRulesResponse()` 统一入口 | `SyncClient.kt` |
| 5 | 全部 SharedPreferences 明文存储 | 迁移至 `EncryptedSharedPreferences`，首次启动自动迁移旧数据 | `Prefs.kt` |
| 6 | 统计分片无备份机制 | 新增 `rotateStatsBackup()`，每次 `flushDay` 前自动备份，保留 5 份 | `store.ts` |
| 7 | `SettingsViewModel` 缺少 SSRF 防护 | 保留 URL 合法性校验；私有 IP 段不阻断（避免破坏局域网服务定位） | `SettingsViewModel.kt` |
| 8 | `LanguagePreferences` 明文存储，且与 `Prefs` 的迁移源 `adskip_prefs` 同名，语言设置会被首次业务访问整体清空 | 迁移至与 `Prefs` 共用的加密存储 `adskip_prefs_enc`（经 `core/SecurePreferences` 单例打开，单一写入口）；明文仅作一次性迁移源，只搬 `language` 键 | `LanguagePreferences.kt`、`SecurePreferences.kt`、`Prefs.kt` |
| 9 | `Prefs` 明文迁移依赖进程内布尔，跨进程重启可能重复迁移覆盖已加密数据 | 迁移成功即删除明文文件，幂等判定改为「明文文件是否存在」，天然跨进程幂等 | `Prefs.kt` |
| 10 | 落地页/管理后台静态页缺安全响应头 | `X-Content-Type-Options` / `Referrer-Policy` / `X-Frame-Options`；`/admin` 追加 CSP | `httpUtil.ts`、`server.ts`、`smoke.test.ts` |
| 11 | 服务端无 HTTPS 支持（P0） | `Bun.serve({ tls })` 启用 TLS，`TLS_CERT_FILE` / `TLS_KEY_FILE` 配置；缺少证书文件则启动报错；自签名证书冒烟测试 | `server.ts`、`config.ts`、`smoke.test.ts` |
| 12 | `SyncClient` HTTP 无证书锁定（P0） | 引入 `OkHttp`（连接池 / Keep-Alive / 指数退避 GET 重试 / `CertificatePinner` 证书锁定）；`HttpStatusException` 故意不继承 `IOException` 使 HTTP 状态错误不重试 | `libs.versions.toml`、`build.gradle.kts`、`net/HttpTransport.kt`（新增）、`net/SyncClient.kt` |
| 13 | 服务端 `console.log` 无结构化输出 | 新增零依赖结构化 JSON-lines 日志器（`src/utils/logger.ts`），`_isStructuredLine` 测试钩子 | `server/src/utils/logger.ts`（新增）、`server.ts`、`smoke.test.ts` |
| 14 | `rateLimit.ts` / `store.ts` 使用 `setInterval` 做 GC/清理 | 改为递归 `setTimeout` + `stopBackgroundTimers()`，接入优雅停机 | `rateLimit.ts`、`store.ts`、`server.ts` |
| 15 | `StatsRepository` 合批写 SP 无错误处理（P2） | 快照后立即清零待落盘增量（修复 SP 已写仍残留导致重复计数）；`commit()` 感知写失败 + 捕获异常记录 `LogRing` | `StatsRepository.kt` |
| 16 | `SkipAdService` 接收器注册/注销模板重复（P1） | 提取 `ReceiverHandle` 抽象，收敛注册/注销公共路径 | `service/ReceiverHandle.kt`（新增）、`service/SkipAdService.kt` |
| 17 | `Prefs.DEFAULT_SERVER` 硬编码本地 IP（P2） | 默认值改为空串，强制用户在 UI 显式输入；非法/空 URL 由 `saveServerUrl` 校验并提示 | `Prefs.kt`、`ui/settings/SettingsViewModel.kt` |
| 18 | `store.ts` 无存储层单测（P1） | 新增 `store.test.ts`：规则写入/备份轮转/损坏回退/不可写兜底/过期分片清理；期间发现并修复 `flushDay` 写失败异常外抛问题（改为吞异常+告警日志，下次 flush 重试） | `server/test/store.test.ts`（新增）、`store.ts` |
| 19 | `SyncClient` 无网络层单测（P1） | 新增 `HttpTransportTest`：MockWebServer 覆盖 304/超时/重试/POST、TLS 证书锁定正确与错误 pin | `client/app/src/test/java/com/ldp/adskip/net/HttpTransportTest.kt`（新增） |

---

## 待修复（按优先级）

### 🔴 高危（安全/稳定性）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P0 | 客户端 release 仍可能连明文 HTTP | 客户端默认空 server URL 已强制用户输入；进一步可对 release 构建强制 HTTPS scheme | 0.5 天 |

### 🟡 中危（架构/设计）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `store.ts` 规则备份仅全量拷贝，无增量/压缩 | 长期运行可改用 WAL 或按天快照；当前 JSON 全量备份对小项目可接受 | 待评估 |

### 🟢 低危（测试/可观测/文档）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P2 | 无端到端集成测试 | 补 `androidx.test` instrumentation 或 `AppTest`，覆盖 Service → Engine → 点击 → 上报全链路 | 2-3 天 |
| P2 | 服务端结构化日志覆盖不足 | 当前日志器仅覆盖启动/存储/限流关键路径；可扩展请求日志中间件 | 1 天 |
| P3 | `SettingsScreen.kt` 快照 UI 集成未完成 | 联调节点快照导出按钮与 `SkipAdService` 接收器 | 0.5 天 |
| P3 | 未提交构建产物残留 | 清理 `client/build-logic/convention/bin/` 或确认已 `.gitignore` | 0.5 天 |

---

## 技术栈补充建议

| 技术 | 用途 | 优先级 | 备注 |
|------|------|--------|------|
| `androidx.security:security-crypto` | EncryptedSharedPreferences | ✅ 已引入 | 版本 1.1.0-alpha07 |
| `OkHttp` | HTTP 客户端（连接池/证书锁定/重试） | ✅ 已引入 | 4.12.0，替代 `HttpURLConnection` |
| `MockWebServer` | 网络层测试 | ✅ 已引入 | 与 OkHttp 配套，`okhttp-tls` 生成测试证书 |
| 结构化 JSON-lines 日志器 | 服务端结构化日志 | ✅ 已引入 | `server/src/utils/logger.ts`，零运行时依赖 |
| Bun 原生 TLS | 传输层安全 | ✅ 已引入 | `Bun.serve({ tls })`，自签名证书冒烟测试 |
| `Prometheus` / OpenTelemetry | 指标导出 | P3 | 长期可观测需求 |

---

## 里程碑建议

### v3.3.0（安全加固版）
- [x] 明文流量策略显式收口（NSC base-config 放行，行为等价；HTTPS 迁移后收紧）
- [x] EncryptedSharedPreferences 全量迁移
- [x] `reportSkip` deviceId bug 修复
- [x] 服务端启用 HTTPS（`Bun.serve({ tls })`，局域网自签名证书可用）
- [x] `SyncClient` 引入 OkHttp + 证书锁定
- [x] `MockWebServer` 网络层单测
- [x] `store.ts` 存储层单测

### v3.4.0（工程化版）
- [ ] 端到端集成测试
- [x] 服务端结构化日志
- [x] `SkipAdService` 接收器抽象
- [ ] `store.ts` 备份压缩/增量
- [x] `rateLimit.ts` / `store.ts` 计时器显式清理

### v4.0.0（可观测版）
- [ ] 指标导出（Prometheus/OTel）
- [ ] 客户端崩溃上报（非强制，可选）
- [ ] 服务端健康检查增强（/metrics）

---

## 风险与回滚

- **加密迁移风险**：`Prefs.kt` 首次启动时从明文 SP 迁移，若 `MasterKey` 生成失败会导致应用无法读取数据。回滚方案：降级为明文存储（删除 `EncryptedSharedPreferences` 相关代码，恢复 `context.getSharedPreferences`）。
- **HTTPS 启用风险**：局域网用户若使用自签名证书，需额外配置 `network_security_config` 信任用户证书。当前服务端 `Bun.serve({ tls })` 就绪，客户端默认 `DEFAULT_SERVER=""` 由用户显式输入；`HttpTransport` 支持 `CertificatePinner`。回滚方案：服务端以 `TLS_CERT_FILE`/`TLS_KEY_FILE` 未配置即停用 TLS 的旧逻辑启动。
- **OkHttp 引入风险**：增加 APK 体积约 200-300 KB（4.12.0）。`HttpTransport` 是唯一网络入口，若需回退，替换 `HttpTransport.create` 为 `HttpURLConnection` 封装即可。
- **`DEFAULT_SERVER` 置空风险**：存量用户本地持久化了旧 IP，首次升级会继续连旧服务器直至在设置页重新输入。行为符合「强制用户显式输入」目标。

---

## 相关文档

- [架构文档](../architecture/ARCHITECTURE.md)
- [API 参考](../api/API.md)
- [开发环境](../development/DEV-ENVIRONMENT.md)
- [发布历史](../planning/RELEASE-HISTORY.md)
- [路线图](../planning/ROADMAP.md)
