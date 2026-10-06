# AdSkip 后续计划

> 本文档记录项目短板修复、安全加固与技术补充的后续计划。
> 优先级按「安全/稳定性 → 架构/设计 → 工程化/可观测」排列。

## 已修复（2026-10-06）

| # | 问题 | 修复内容 | 文件 |
|---|------|----------|------|
| 1 | `reportSkip` 单参重载硬编码 `deviceId="pending"` | 改为直接走 v0 单条上报，避免伪造 deviceId | `SyncClient.kt` |
| 2 | `usesCleartextTraffic="true"` 默认明文传输 | 明文策略显式收口到 `network_security_config.xml`；NSC `<domain>` 不支持私有网段前缀（10./172.16./192.168. 无效），base-config 放行明文（行为与旧版等价），HTTPS 迁移后收紧 | `AndroidManifest.xml`, `res/xml/network_security_config.xml` |
| 3 | 网络请求裸创建 `Thread` | 统一改用 `AppExecutors.io.execute` | `SyncClient.kt` |
| 4 | `syncRules` 与 `syncRulesSilently` JSON 解析重复 ~70 行 | 提取 `parseRulesResponse()` 统一入口 | `SyncClient.kt` |
| 5 | 全部 SharedPreferences 明文存储 | 迁移至 `EncryptedSharedPreferences`，首次启动自动迁移旧数据 | `Prefs.kt` |
| 6 | 统计分片无备份机制 | 新增 `rotateStatsBackup()`，每次 `flushDay` 前自动备份，保留 5 份 | `store.ts` |
| 7 | `SettingsViewModel` 缺少 SSRF 防护 | 保留 URL 合法性校验；私有 IP 段不阻断（避免破坏局域网服务定位） | `SettingsViewModel.kt` |

---

## 待修复（按优先级）

### 🔴 高危（安全/稳定性）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P0 | 服务端无 HTTPS 支持 | 启用 `Bun.serve({ tls: ... })` 或反向代理 Nginx/Caddy；客户端 release 强制 HTTPS | 1-2 天 |
| P0 | `SyncClient` HTTP 无证书锁定 | 引入 `OkHttp` 或自写 `HostnameVerifier` + `CertificatePinner` | 2-3 天 |
| P1 | `Prefs` 迁移逻辑幂等性不足 | `migrated` 标志在进程内仅一次，跨进程重启会重复迁移；改用 `SharedPreferences` 文件存在性判断 | 0.5 天 |
| P1 | `LanguagePreferences` 未使用加密存储 | 与 `Prefs` 同步迁移至 `EncryptedSharedPreferences` | 0.5 天 |
| P2 | `StatsRepository` 合批写 SP 无错误处理 | 捕获 `IOException` 并记录 `LogRing`，避免统计丢失静默失败 | 0.5 天 |

### 🟡 中危（架构/设计）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SkipAdService` 接收器注册/注销模板重复 | 提取 `ReceiverHandle` 抽象，收敛注册/注销公共路径 | 1 天 |
| P1 | `store.ts` 规则备份仅全量拷贝，无增量/压缩 | 长期运行可改用 WAL 或按天快照；当前 JSON 全量备份对小项目可接受 | 待评估 |
| P2 | `Prefs.DEFAULT_SERVER` 硬编码本地 IP | 增加 URL 合法性校验（禁止私有地址回环）；或在 UI 隐藏默认值，强制用户输入 | 0.5 天 |
| P2 | `rateLimit.ts` / `store.ts` 使用 `setInterval` 做 GC/清理 | Bun 优雅停机可能截断；改用 `setTimeout` 递归或显式清理钩子 | 0.5 天 |
| P3 | `SyncClient` 无连接池/Keep-Alive/重试 | 引入 `OkHttp`（若允许）或手写连接池 + 指数退避 | 2-3 天 |

### 🟢 低危（测试/可观测/文档）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SyncClient` 无网络层单测 | 引入 `MockWebServer` 或 OkHttp Mock，覆盖 304/超时/异常路径 | 1 天 |
| P1 | `store.ts` 无存储层单测 | 补 `store.test.ts`，覆盖写入中断、磁盘满、权限错误 | 1 天 |
| P2 | 无端到端集成测试 | 补 `androidx.test` instrumentation 或 `AppTest`，覆盖 Service → Engine → 点击 → 上报全链路 | 2-3 天 |
| P2 | 无结构化日志/指标导出 | 服务端补 `pino` 或 Bun 原生 structured log；客户端关键路径打点 | 1-2 天 |
| P3 | `SettingsScreen.kt` 快照 UI 集成未完成 | 联调节点快照导出按钮与 `SkipAdService` 接收器 | 0.5 天 |
| P3 | 未提交构建产物残留 | 清理 `client/build-logic/convention/bin/` 或确认已 `.gitignore` | 0.5 天 |

---

## 技术栈补充建议

| 技术 | 用途 | 优先级 | 备注 |
|------|------|--------|------|
| `androidx.security:security-crypto` | EncryptedSharedPreferences | ✅ 已引入 | 版本 1.1.0-alpha07 |
| `OkHttp` | HTTP 客户端（连接池/证书锁定/重试） | P1 | 替代 `HttpURLConnection` |
| `MockWebServer` | 网络层测试 | P1 | 与 OkHttp 配套 |
| `pino` / `bun:logger` | 服务端结构化日志 | P2 | 替代 `console.log` |
| `Prometheus` / OpenTelemetry | 指标导出 | P3 | 长期可观测需求 |
| `TLS 1.3` + `HSTS` | 传输层安全 | P0 | 服务端配置 |

---

## 里程碑建议

### v3.3.0（安全加固版）
- [x] 明文流量策略显式收口（NSC base-config 放行，行为等价；HTTPS 迁移后收紧）
- [x] EncryptedSharedPreferences 全量迁移
- [x] `reportSkip` deviceId bug 修复
- [ ] 服务端启用 HTTPS（或明确标注「仅局域网使用」）
- [ ] `SyncClient` 引入 OkHttp + 证书锁定
- [ ] `MockWebServer` 网络层单测

### v3.4.0（工程化版）
- [ ] 端到端集成测试
- [ ] 服务端结构化日志
- [ ] `SkipAdService` 接收器抽象
- [ ] `store.ts` 备份压缩/增量

### v4.0.0（可观测版）
- [ ] 指标导出（Prometheus/OTel）
- [ ] 客户端崩溃上报（非强制，可选）
- [ ] 服务端健康检查增强（/metrics）

---

## 风险与回滚

- **加密迁移风险**：`Prefs.kt` 首次启动时从明文 SP 迁移，若 `MasterKey` 生成失败会导致应用无法读取数据。回滚方案：降级为明文存储（删除 `EncryptedSharedPreferences` 相关代码，恢复 `context.getSharedPreferences`）。
- **HTTPS 启用风险**：局域网用户若使用自签名证书，需额外配置 `network_security_config` 信任用户证书。回滚方案：release 保持 HTTP（不推荐），或提供 debug 配置允许用户自签证书。
- **OkHttp 引入风险**：增加 APK 体积约 200-300 KB。回滚方案：保持 `HttpURLConnection`，仅做证书锁定。

---

## 相关文档

- [架构文档](../architecture/ARCHITECTURE.md)
- [API 参考](../api/API.md)
- [开发环境](../development/DEV-ENVIRONMENT.md)
- [发布历史](../planning/RELEASE-HISTORY.md)
- [路线图](../planning/ROADMAP.md)
