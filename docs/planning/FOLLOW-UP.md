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
| 14 | v1 批量上报事件缺 `label`（服务端已接受但客户端从未发送） | 服务端 `cleanReportEvent` 接受可选 label（`2532046`）；客户端 v1 事件补 `label` 字段（`30d73d1`）；API 文档同步更新（`a5c8b63`） | `rulesApi.ts`, `SyncClient.kt`, `API.md` |
| 15 | `reportSkip` 上报通道无证书锁定（`httpPost` 用 `plainClient`，与同步通道 `clientForPins` 不一致） | `httpPost` 增 pins 参数改用 `clientForPins`；未配 pins 时 fail-closed 拒发；移除死代码 `plainClient`/`client()`（`30d73d1`）；单测验证（`749a4d0`） | `SyncClient.kt`, `SkipAdService.kt`, `SyncClientTest.kt` |
| 16 | 限流键默认信任可伪造的 `X-Forwarded-For`（换头即绕过全部限频）；鉴权失败不消耗令牌桶（token 可无限暴力猜测）；令牌桶无上限 | 限流默认只认 socket IP（对端 ∈ `TRUSTED_PROXIES` 才采信 XFF）、限流先于鉴权、令牌桶封顶 50000 淘汰最久未用 | `rateLimit.ts`, `rulesApi.ts`, `index.ts` |
| 17 | `STATS_READ_AUTH` 是死配置，统计端点实际匿名可读 | 统计汇总真正消费该开关（`STATS_READ_AUTH=1` 即挡匿名读） | `statsApi.ts`, `config.ts` |
| 18 | 未认证上报可伪造任意包名，无限扩张当日 byApp 结构 | 当日条目封顶 2000（超限聚合 `_other`）+ label 去控制字符后入库 | `store.ts`, `validate.ts` |
| 19 | 错误响应回显内部细节；缺安全响应头；CORS 默认全开 | 5xx 归一化 `internal error`（细节只进日志）+ 统一安全头/HTML CSP/HSTS + CORS 未配置默认拒绝跨域 | `httpUtil.ts`, `server.ts` |
| 20 | TLS 半配置静默回退明文 | 半配置启动即抛错；明文 + 非回环监听输出告警 | `server.ts` |
| 21 | Android 8–12 动态注册接收器默认可被任意应用投递（远程关停服务 / 诱导导出前台界面文本） | signature 级 `INTERNAL` 权限收口全部 API 级别 + 快照导出补 `FLAG_ACTIVITY_NEW_TASK` + 契约测试 | `AndroidManifest.xml`, `SkipAdService.kt`, `ManifestContractTest.kt` |
| 22 | `SafetyGuard` 可绕过：中文-only 黑名单、只看目标自身文案、不过滤敏感系统界面 | 补英文/中文敏感词、敏感系统包整包拒绝、父链 3 层文案检查 | `SafetyGuard.kt` |
| 23 | 云端规则载荷无条目上限 + 响应体整包读内存 | 响应体 2MB 封顶 + 载荷条目/长度封顶 + 包名键合法性校验 | `SyncClient.kt` |
| 24 | release 未配签名时静默回退 debug 签名（公开密钥可伪造升级） | fail-closed：未配签名且未显式 `adskip.unsignedRelease=true` 时 release 任务直接失败并禁用 release 变体，绝不回退 debug 签名 | `app/build.gradle.kts` |
| 25 | `LanguagePreferences` 实际仍在明文 SP（#9 未落地），且与 Prefs 旧存储同名，迁移删源文件会丢语言设置 | 偏好统一走 `core/SecureStore` 加密存储（含明文历史全量迁移） | `LanguagePreferences.kt`, `SecureStore.kt`, `Prefs.kt` |

---

## 待修复（按优先级）

### 🔴 高危（安全/稳定性）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P0 | 服务端 HTTPS 支持（TLS 配置已就绪，待部署证书） | 设置 `TLS_CERT`/`TLS_KEY` 环境变量即可启用 HTTPS；客户端 release 强制 HTTPS | 部署时配置 |
| P0 | `SyncClient` HTTP 无证书锁定 | ✅ 已修复（v3.4.0 批次）：迁移 OkHttp 后接入 `CertificatePinner`，指纹经设置下发（必须配置）；S1 补全：上报通道 `httpPost` 同步接入证书锁定 + fail-closed（`30d73d1`） | — |
| P1 | `Prefs` 迁移逻辑幂等性不足 | ✅ 已修复：改用 `deleteSharedPreferences` 删除明文源文件 | — |
| P1 | `LanguagePreferences` 未使用加密存储 | ✅ 已修复：同步迁移至 `EncryptedSharedPreferences` | — |
| P2 | `StatsRepository` 合批写 SP 无错误处理 | ✅ 已修复：`flush()` 添加 `try-catch` + `LogRing.w` | — |
| P1 | **发布版自检必然超时**（2026-10-11 模拟器矩阵实测，见 STABILITY-TEST-PLAN §9）：`SafetyGuard.SELF_PKG="com.qingqi.adskip"` 与发布包名相同，`canClick` 对自身包一律拒绝点击（防自触发死循环），自检浮层「跳过」永远点不了；#145 复测因使用 `.debug` 后缀并行包（包名不同）绕过护栏而误判「已修复」 | ✅ 已修复（分支 `fix/self-check-own-package`）：`canClick` 增加 `allowSelfPackage: Boolean = false`，仅自测态由 `SkipAdService` 传 `AppEvents.testActive` 放行，默认仍拦自身包；豁免参数留在 `engine` 内，不引入 `core.AppEvents` 依赖（`ArchitectureBoundaryTest` 约束）。补 3 例 JVM 单测：用**发布包名**断言默认拦截、显式放行可通过、放行不削弱其它底线 | — |
| P1 | **跳过计数双重累加**（2026-10-11 实测：单次跳过首页显示 2、5 秒后 3，记录恒为 1）：`Prefs.recordSkip` 立即写 SP total+1，`StatsRepository.recordSkip` 又 `pendingTotal++`，`total()=SP+pending` 双算；且 `flush()` 落盘后不重置 `pendingTotal`/`pendingByPkg`，计数无界膨胀（结构自 v3.4.0 #78 引入） | ✅ 已修复（分支 `fix/skip-count-double`）：`Prefs.recordSkip` 拆为 `appendSkipLog`（只写日志与最近应用）+ `addSkipCounts`（唯一计数写入路径）；新增 `data/StatsStore` 端口使 `StatsRepository` 不再持有 `Context`；`flush()` 捕获增量后立即清空 `pending*`。新增 `StatsRepositoryTest` 7 例断言「N 次 → `total()==N`、落盘后仍为 N、跨多轮 flush 不膨胀」，并经变异验证可捕获该缺陷 | — |

### 🟡 中危（架构/设计）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SkipAdService` 接收器注册/注销模板重复 | ✅ 已修复：提取 `ReceiverHandle` 抽象 | — |
| P1 | `store.ts` 规则备份仅全量拷贝，无增量/压缩 | 长期运行可改用 WAL 或按天快照；当前 JSON 全量备份对小项目可接受 | 待评估 |
| P2 | `Prefs.DEFAULT_SERVER` 硬编码本地 IP | ✅ 已修复：清空默认地址（`DEFAULT_SERVER=""`），未配置时不预填任何值，强制用户显式输入有效 HTTPS 地址 | — |
| P2 | `rateLimit.ts` / `store.ts` 使用 `setInterval` 做 GC/清理 | Bun 优雅停机可能截断；改用 `setTimeout` 递归或显式清理钩子 | 0.5 天 |
| P2 | **引擎误跳系统界面文本**（2026-10-11 模拟器矩阵实测）：`SafetyGuard.canClick` 不校验节点 `clickable`，任何含关键词的可见文本都会被点击——API 33 设置页中本应用服务行「轻启 · Skip Splash Ads」被点并落库 `com.android.settings` 跳过；API 35 出现 2 条系统包误跳计数 | **部分修复**：包级豁免分支已落地（`SafetyGuard.DENY_SYSTEM_PACKAGES` 覆盖 `com.android.settings` / `com.android.systemui` / 安装器 / 权限控制器，实测的两个误跳包名均已覆盖，代码基线核查于 2026-10-16）。**仍缺**：① `canClick` 的 clickable / 可聚焦校验（未做，当前仍可能点击无 `clickable` 的纯文本节点）；② launcher 类包未进豁免清单；③ 真实窗口回归用例（无自动化守卫） | 1 天（剩余部分） |
| P3 | `SyncClient` 无连接池/Keep-Alive/重试 | ✅ 已修复（v3.3.0）：迁移 OkHttp 4.12，连接池/超时/重试齐备 | — |

### 🟢 低危（测试/可观测/文档）

| 优先级 | 问题 | 建议修复方案 | 预计工作量 |
|--------|------|--------------|------------|
| P1 | `SyncClient` 无网络层单测 | ✅ 已修复（v3.3.0）：OkHttp 迁移同步引入 MockWebServer 回归（`SyncClientTest`） | — |
| P1 | `store.ts` 无存储层单测 | ✅ 已修复：新增 `rotateStatsBackup` 测试用例，验证备份创建与轮转上限 | — |
| P2 | 无端到端集成测试 | 补 `androidx.test` instrumentation 或 `AppTest`，覆盖 Service → Engine → 点击 → 上报全链路 | 2-3 天 |
| P2 | 无 Compose UI 语义回归测试 | 2026-10-11 真机实测：模拟广告浮层的「跳过」按钮曾被 `clearAndSetSemantics` 误清出语义树导致自检必败（#145 已修逻辑，无自动化守卫防复发）。引入 `ui-test-junit4`（Robolectric）写断言：浮层可见时「跳过」在语义树可及、首页内容不可及 | 0.5 天 |
| P2 | 无结构化日志/指标导出 | ✅ 已修复：新增 `src/utils/logger.ts`（JSONL 格式，零运行时依赖），替换 `server.ts` 中的 `console.*` | — |
| P3 | `SettingsScreen.kt` 快照 UI 集成未完成 | ✅ 已完成（v3.2.0）：快照工具与设置页入口随 M1c 发布 | — |
| P3 | 未提交构建产物残留 | ✅ 已确认：`client/build-logic/convention/bin/` 已被 `.gitignore`，不入库 | — |

---

## 后续功能建议

> 未排期功能方向（区别于「待修复」：非缺陷/加固，而是新能力）。正式排期立项时按
> 「ROADMAP → ROADMAP-ADS / DESIGN → CHANGELOG / RELEASE-HISTORY」顺序登记。

- [ ] **识别关键词自动识别**（P2，2026-10-11 确认方向）：优先走「云端规则库扩充 + 自动同步」——客户端
  `SyncClient` / `applyCloudRules` 已支持，仅需运营持续扩充 `server/seed/rules.json`，零客户端改动；
  再预留「误点/漏点反馈上报 + 服务端聚合学习」闭环（涉及协议变更，契约先行）；设备端启发式识别
  与 `SafetyGuard` 的 fail-closed 设计冲突最大，放到最后评估。

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

- [API 参考](../api/API.md)
- [开发环境](../development/DEV-ENVIRONMENT.md)
- [发布历史](../planning/RELEASE-HISTORY.md)
- [路线图](../planning/ROADMAP.md)
