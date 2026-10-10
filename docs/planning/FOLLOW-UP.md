# AdSkip 后续计划（FOLLOW-UP）

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

---

## 待修复（按优先级）

### 🔴 高危（安全/稳定性）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P0 | 服务端 HTTPS 支持（TLS 配置已就绪，待部署证书） | 设置 `TLS_CERT`/`TLS_KEY` 环境变量即可启用 HTTPS；客户端 release 强制 HTTPS | 部署时配置 |
| P0 | `SyncClient` HTTP 无证书锁定 | ✅ 已修复（v3.4.0 批次）：迁移 OkHttp 后接入 `CertificatePinner`，指纹经设置下发（必须配置） | — |
| P1 | `Prefs` 迁移逻辑幂等性不足 | ✅ 已修复：改用 `deleteSharedPreferences` 删除明文源文件 | — |
| P1 | `LanguagePreferences` 未使用加密存储 | ✅ 已修复：同步迁移至 `EncryptedSharedPreferences` | — |
| P2 | `StatsRepository` 合批写 SP 无错误处理 | ✅ 已修复：`flush()` 添加 `try-catch` + `LogRing.w` | — |

### 🟡 中危（架构/设计）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SkipAdService` 接收器注册/注销模板重复 | ✅ 已修复：提取 `ReceiverHandle` 抽象 | — |
| P1 | `store.ts` 规则备份仅全量拷贝，无增量/压缩 | 长期运行可改用 WAL 或按天快照；当前 JSON 全量备份对小项目可接受 | 待评估 |
| P2 | `Prefs.DEFAULT_SERVER` 硬编码本地 IP | ✅ 已修复：清空默认地址（`DEFAULT_SERVER=""`），未配置时不预填任何值，强制用户显式输入有效 HTTPS 地址 | — |
| P2 | `rateLimit.ts` / `store.ts` 使用 `setInterval` 做 GC/清理 | Bun 优雅停机可能截断；改用 `setTimeout` 递归或显式清理钩子 | 0.5 天 |
| P3 | `SyncClient` 无连接池/Keep-Alive/重试 | ✅ 已修复（v3.3.0）：迁移 OkHttp 4.12，连接池/超时/重试齐备 | — |

### 🟢 低危（测试/可观测/文档）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SyncClient` 无网络层单测 | ✅ 已修复（v3.3.0）：OkHttp 迁移同步引入 MockWebServer 回归（`SyncClientTest`） | — |
| P1 | `store.ts` 无存储层单测 | ✅ 已修复：新增 `rotateStatsBackup` 测试用例，验证备份创建与轮转上限 | — |
| P2 | 无端到端集成测试 | 补 `androidx.test` instrumentation 或 `AppTest`，覆盖 Service → Engine → 点击 → 上报全链路 | 2-3 天 |
| P2 | 无结构化日志/指标导出 | ✅ 已修复：新增 `src/utils/logger.ts`（JSONL 格式，零运行时依赖），替换 `server.ts` 中的 `console.*` | — |
| P3 | `SettingsScreen.kt` 快照 UI 集成未完成 | ✅ 已完成（v3.2.0）：快照工具与设置页入口随 M1c 发布 | — |
| P3 | 未提交构建产物残留 | ✅ 已确认：`client/build-logic/convention/bin/` 已被 `.gitignore`，不入库 | — |

---

## 技术栈补充建议

| 技术 | 用途 | 优先级 | 备注 |
|------|------|--------|------|
| `androidx.security:security-crypto` | EncryptedSharedPreferences | ✅ 已引入 | 版本 1.1.0-alpha07 |
| 服务端结构化日志 | JSONL 输出 | ✅ 已实现 | `src/utils/logger.ts`，零运行时依赖 |
| 服务端 TLS | HTTPS 支持 | ✅ 配置就绪 | 通过 `TLS_CERT`/`TLS_KEY` 环境变量启用 |
| `OkHttp` | HTTP 客户端（连接池/证书锁定/重试） | ✅ 已引入（v3.3.0） | 4.12；证书锁定经 `CertificatePinner` 于 v3.4.0 批次落地 |
| `MockWebServer` | 网络层测试 | ✅ 已引入（v3.3.0） | `SyncClientTest` 回归使用 |
| 规则 HMAC 签名 | 规则防篡改下发 | ✅ 已实现（v3.4.0 批次） | server `utils/sign.ts` + client `SyncClient` 本地校验 |
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
- [x] `SyncClient` 引入 OkHttp（v3.3.0）+ 证书锁定（v3.4.0 批次）
- [x] `MockWebServer` 网络层单测（v3.3.0）

### v3.4.0（工程化版）
- [ ] 端到端集成测试
- [x] `SyncClient` 连接池/Keep-Alive/重试（随 OkHttp 迁移完成，v3.3.0）
- [ ] `store.ts` 备份压缩/增量

### v4.0.0（可观测版）
- [ ] 指标导出（Prometheus/OTel）
- [ ] 客户端崩溃上报（非强制，可选）
- [ ] 服务端健康检查增强（/metrics）

---

## 风险与回滚

- **加密迁移风险**：`Prefs.kt` 首次启动时从明文 SP 迁移，若 `MasterKey` 生成失败会导致应用无法读取数据。回滚方案：降级为明文存储（删除 `EncryptedSharedPreferences` 相关代码，恢复 `context.getSharedPreferences`）。
- **HTTPS 启用风险**：局域网用户若使用自签名证书，需额外配置 `network_security_config` 信任用户证书。回滚方案：release 保持 HTTP（不推荐），或提供 debug 配置允许用户自签证书。
- **OkHttp 引入风险**：已引入（v3.3.0），APK 体积约增加 200-300 KB，且打破了原「零第三方依赖」承诺（已由项目决策接受）。如需回滚可退回 `HttpURLConnection`，但会同时失去证书锁定与连接池能力，不建议。
- **TLS 配置风险**：当前仅添加配置基础设施，未实际启用。部署时需正确配置证书路径，否则服务启动失败。回滚方案：不设置 `TLS_CERT`/`TLS_KEY` 环境变量即可回退到 HTTP。

---

## 相关文档

- [架构文档](../architecture/ARCHITECTURE.md)
- [API 参考](../api/API.md)
- [开发环境](../development/DEV-ENVIRONMENT.md)
- [发布历史](../planning/RELEASE-HISTORY.md)
- [路线图](../planning/ROADMAP.md)
