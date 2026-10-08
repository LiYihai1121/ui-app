# AdSkip 后续计划

> 本文档记录项目短板修复、安全加固与技术补充的后续计划。
> 优先级按「安全/稳定性 → 架构/设计 → 工程化/可观测」排列。

## 已修复（2026-10-06 ~ 2026-10-07）

| # | 问题 | 修复内容 | 文件 |
|---|------|----------|------|
| 1 | `reportSkip` 单参重载硬编码 `deviceId="pending"` | 改为直接走 v0 单条上报，避免伪造 deviceId | `SyncClient.kt` |
| 2 | `usesCleartextTraffic="true"` 默认明文传输 | 明文策略显式收口到 `network_security_config.xml`；NSC `<domain>` 不支持私有网段前缀（10./172.16./192.168. 无效），base-config 放行明文（行为与旧版等价），HTTPS 迁移后收紧 | `AndroidManifest.xml`, `res/xml/network_security_config.xml` |
| 3 | 网络请求裸创建 `Thread` | 统一改用 `AppExecutors.io.execute` | `SyncClient.kt` |
| 4 | `syncRules` 与 `syncRulesSilently` JSON 解析重复 ~70 行 | 提取 `parseRulesResponse()` 统一入口 | `SyncClient.kt` |
| 5 | 全部 SharedPreferences 明文存储 | 迁移至 `EncryptedSharedPreferences`，首次启动自动迁移旧数据 | `Prefs.kt` |
| 6 | 统计分片无备份机制 | 新增 `rotateStatsBackup()`，每次 `flushDay` 前自动备份，保留 5 份 | `store.ts` |
| 7 | `SettingsViewModel` 缺少 SSRF 防护 | 保留 URL 合法性校验；私有 IP 段不阻断（避免破坏局域网服务定位） | `SettingsViewModel.kt` |
| 8 | `Prefs` 迁移逻辑幂等性不足 | 删除进程内 `migrated` 标志，改用 `deleteSharedPreferences(LEGACY_SP_NAME)` 删除明文源文件，跨进程重启不再重复迁移 | `Prefs.kt` |
| 9 | `LanguagePreferences` 未使用加密存储 | 与 `Prefs` 同步改用 `EncryptedSharedPreferences` | `LanguagePreferences.kt` |
| 10 | `StatsRepository` 合批写 SP 无错误处理 | `flush()` 添加 `try-catch` + `LogRing.w`，避免统计丢失静默失败 | `StatsRepository.kt` |
| 11 | `SkipAdService` 接收器注册/注销模板重复 | 提取 `ReceiverHandle` 内部类，收敛注册/注销公共路径，消除 4 个重复方法 | `SkipAdService.kt` |
| 12 | 服务端无结构化日志 | 新增 `src/utils/logger.ts`（JSONL 格式，零运行时依赖），替换 `server.ts` 中的 `console.*` | `server.ts`, `logger.ts` |
| 13 | 服务端无 TLS 配置 | 新增 `TLS_CERT`/`TLS_KEY` 环境变量支持，`Bun.serve` 条件启用 TLS | `config.ts`, `server.ts` |
| 14 | 限流键默认信任可伪造的 `X-Forwarded-For`（换头即绕过全部限频）；鉴权失败不消耗令牌桶（token 可无限暴力猜测）；令牌桶无上限 | 限流默认只认 socket IP（`TRUST_PROXY=1` 才信 XFF）、限流先于鉴权、令牌桶封顶 10000 淘汰最久未用 | `rateLimit.ts`, `rulesApi.ts`, `index.ts` |
| 15 | `STATS_READ_AUTH` 是死配置，统计端点实际匿名可读 | 统计汇总真正消费该开关（`STATS_READ_AUTH=1` 即挡匿名读） | `statsApi.ts`, `config.ts` |
| 16 | 未认证上报可伪造任意包名，无限扩张当日 byApp 结构 | 当日条目封顶 2000（超限聚合 `_other`）+ label 去控制字符后入库 | `store.ts`, `validate.ts` |
| 17 | 错误响应回显内部细节；缺安全响应头；CORS 默认全开 | 5xx 归一化 `internal error`（细节只进日志）+ 统一安全头/HTML CSP/HSTS + CORS 未配置默认拒绝跨域 | `httpUtil.ts`, `server.ts` |
| 18 | TLS 半配置静默回退明文 | 半配置启动即抛错；明文 + 非回环监听输出告警 | `server.ts` |
| 19 | Android 8–12 动态注册接收器默认可被任意应用投递（远程关停服务 / 诱导导出前台界面文本） | signature 级 `INTERNAL` 权限收口全部 API 级别 + 快照导出补 `FLAG_ACTIVITY_NEW_TASK` + 契约测试 | `AndroidManifest.xml`, `SkipAdService.kt`, `ManifestContractTest.kt` |
| 20 | `SafetyGuard` 可绕过：中文-only 黑名单、只看目标自身文案、不过滤敏感系统界面 | 补英文/中文敏感词、敏感系统包整包拒绝、父链 3 层文案检查 | `SafetyGuard.kt` |
| 21 | 云端规则载荷无条目上限 + 响应体整包读内存 | 响应体 2MB 封顶 + 载荷条目/长度封顶 + 包名键合法性校验 | `SyncClient.kt` |
| 22 | release 未配签名时静默回退 debug 签名（公开密钥可伪造升级） | 默认产出未签名包；回退 debug 签名需显式 `adskip.allowDebugSigning=true` | `app/build.gradle.kts` |
| 23 | `LanguagePreferences` 实际仍在明文 SP（#9 未落地），且与 Prefs 旧存储同名，迁移删源文件会丢语言设置 | 偏好统一走 `core/SecureStore` 加密存储（含明文历史全量迁移） | `LanguagePreferences.kt`, `SecureStore.kt`, `Prefs.kt` |
| 24 | 管理端单因子认证：ADMIN_TOKEN 泄露即全失守（可下发任意规则驱动客户端点击） | 启用 TOTP 双因素（RFC 6238，零依赖实现）：`ADMIN_TOTP_SECRET` 配置后管理端点须带 `X-2FA-Code`；单 IP 连错 5 次锁 15 分钟；`bun run totp:gen` 生成密钥，管理后台带动态码输入框 | `totp.ts`, `auth.ts`, `admin.html` |

---

## 待修复（按优先级）

### 🔴 高危（安全/稳定性）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P0 | 服务端 HTTPS 支持（TLS 配置已就绪，待部署证书） | 设置 `TLS_CERT`/`TLS_KEY` 环境变量即可启用 HTTPS；客户端 release 强制 HTTPS | 部署时配置 |
| P0 | 规则链路无签名：明文 HTTP 下 MITM 可注入规则驱动无障碍自动点击（泄露面：deviceId/使用画像） | 规则响应加签名（客户端内置公钥验签后才落地，跨端协议变更需同步 API.md + 契约测试）；或部署 HTTPS + 真实证书锁定后收紧 NSC | 待评估 |
| P0 | `SyncClient` 证书锁定是空壳（`CertificatePinner` 无 pin 值，等于未启用） | OkHttp 已就位；部署证书后配置 `CertificatePinner.add(host, "sha256/…")` 并补 pin 覆盖测试 | 部署时配置 |
| P1 | `Prefs` 迁移逻辑幂等性不足 | ✅ 已修复：改用 `deleteSharedPreferences` 删除明文源文件 | — |
| P1 | `LanguagePreferences` 未使用加密存储 | ✅ 已修复：同步迁移至 `EncryptedSharedPreferences` | — |
| P2 | `StatsRepository` 合批写 SP 无错误处理 | ✅ 已修复：`flush()` 添加 `try-catch` + `LogRing.w` | — |

### 🟡 中危（架构/设计）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SkipAdService` 接收器注册/注销模板重复 | ✅ 已修复：提取 `ReceiverHandle` 抽象 | — |
| P1 | `store.ts` 规则备份仅全量拷贝，无增量/压缩 | 长期运行可改用 WAL 或按天快照；当前 JSON 全量备份对小项目可接受 | 待评估 |
| P2 | `Prefs.DEFAULT_SERVER` 硬编码本地 IP | 增加 URL 合法性校验（禁止私有地址回环）；或在 UI 隐藏默认值，强制用户输入 | 0.5 天 |
| P2 | `rateLimit.ts` / `store.ts` 使用 `setInterval` 做 GC/清理 | Bun 优雅停机可能截断；改用 `setTimeout` 递归或显式清理钩子 | 0.5 天 |
| P3 | `SyncClient` 无连接池/Keep-Alive/重试 | 引入 `OkHttp`（若允许）或手写连接池 + 指数退避；受「零第三方依赖」约束，待项目决策后实施 | 待评估 |

### 🟢 低危（测试/可观测/文档）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SyncClient` 无网络层单测 | 引入 `MockWebServer` 或 OkHttp Mock；受「零第三方依赖」约束，待项目决策后实施 | 待评估 |
| P1 | `store.ts` 无存储层单测 | ✅ 已修复：新增 `rotateStatsBackup` 测试用例，验证备份创建与轮转上限 | — |
| P2 | 无端到端集成测试 | 补 `androidx.test` instrumentation 或 `AppTest`，覆盖 Service → Engine → 点击 → 上报全链路 | 2-3 天 |
| P2 | 无结构化日志/指标导出 | ✅ 已修复：新增 `src/utils/logger.ts`（JSONL 格式，零运行时依赖），替换 `server.ts` 中的 `console.*` | — |
| P3 | `SettingsScreen.kt` 快照 UI 集成未完成 | 联调节点快照导出按钮与 `SkipAdService` 接收器 | 0.5 天 |
| P3 | 未提交构建产物残留 | 清理 `client/build-logic/convention/bin/` 或确认已 `.gitignore` | 0.5 天 |

---

## 技术栈补充建议

| 技术 | 用途 | 优先级 | 备注 |
|------|------|--------|------|
| `androidx.security:security-crypto` | EncryptedSharedPreferences | ✅ 已引入 | 版本 1.1.0-alpha07 |
| 服务端结构化日志 | JSONL 输出 | ✅ 已实现 | `src/utils/logger.ts`，零运行时依赖 |
| 服务端 TLS | HTTPS 支持 | ✅ 配置就绪 | 通过 `TLS_CERT`/`TLS_KEY` 环境变量启用 |
| `OkHttp` | HTTP 客户端（连接池/证书锁定/重试） | 待评估 | 替代 `HttpURLConnection`；打破「零第三方依赖」承诺，待项目决策 |
| `MockWebServer` | 网络层测试 | 待评估 | 与 OkHttp 配套；受 OkHttp 引入决策制约 |
| `pino` / `bun:logger` | 服务端结构化日志 | ✅ 已实现 | 自研轻量实现，无需额外依赖 |
| `Prometheus` / OpenTelemetry | 指标导出 | P3 | 长期可观测需求 |
| `TLS 1.3` + `HSTS` | 传输层安全 | ✅ 基础设施就绪 | 部署时配置证书即可启用 |

---

## 里程碑建议

### v3.3.0（安全加固版）
- [x] 明文流量策略显式收口（NSC base-config 放行，行为等价；HTTPS 迁移后收紧）
- [x] EncryptedSharedPreferences 全量迁移
- [x] `reportSkip` deviceId bug 修复
- [x] `Prefs` 迁移幂等性修复
- [x] `LanguagePreferences` 加密迁移
- [x] `StatsRepository` 写错误处理
- [x] TLS 配置基础设施（`TLS_CERT`/`TLS_KEY` 环境变量）
- [x] 服务端结构化日志
- [x] `SkipAdService` 接收器抽象
- [x] `store.ts` 统计备份轮转
- [ ] `SyncClient` 引入 OkHttp + 证书锁定（待项目决策）
- [ ] `MockWebServer` 网络层单测（待 OkHttp 引入）

### v3.4.0（工程化版）
- [ ] 端到端集成测试
- [ ] `SyncClient` 连接池/Keep-Alive/重试（待 OkHttp 引入）
- [ ] `store.ts` 备份压缩/增量

### v4.0.0（可观测版）
- [ ] 指标导出（Prometheus/OTel）
- [ ] 客户端崩溃上报（非强制，可选）
- [ ] 服务端健康检查增强（/metrics）

---

## 风险与回滚

- **加密迁移风险**：`Prefs.kt` 首次启动时从明文 SP 迁移，若 `MasterKey` 生成失败会导致应用无法读取数据。回滚方案：降级为明文存储（删除 `EncryptedSharedPreferences` 相关代码，恢复 `context.getSharedPreferences`）。
- **HTTPS 启用风险**：局域网用户若使用自签名证书，需额外配置 `network_security_config` 信任用户证书。回滚方案：release 保持 HTTP（不推荐），或提供 debug 配置允许用户自签证书。
- **OkHttp 引入风险**：增加 APK 体积约 200-300 KB，且打破「零第三方依赖」承诺。回滚方案：保持 `HttpURLConnection`，仅做证书锁定；或接受风险后引入。
- **TLS 配置风险**：当前仅添加配置基础设施，未实际启用。部署时需正确配置证书路径，否则服务启动失败。回滚方案：不设置 `TLS_CERT`/`TLS_KEY` 环境变量即可回退到 HTTP。

---

## 相关文档

- [架构文档](../architecture/ARCHITECTURE.md)
- [API 参考](../api/API.md)
- [开发环境](../development/DEV-ENVIRONMENT.md)
- [发布历史](../planning/RELEASE-HISTORY.md)
- [路线图](../planning/ROADMAP.md)
