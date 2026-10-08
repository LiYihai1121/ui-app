# 净启动 AdSkip 产品路线图（ROADMAP）

> 分层广告治理专项计划（覆盖 60+ 种广告类型的能力矩阵、分阶段计划与合规边界）见 [ROADMAP-ADS.md](ROADMAP-ADS.md)。
>
> 本页是**版本序列与能力意图的唯一事实源**：里程碑、周次与出口条件见 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 6 节，技术步骤见 [DESIGN-PHASE1-SELECTOR.md](DESIGN-PHASE1-SELECTOR.md) 第 10 节，文档入口见 [../README.md](../README.md)（文档地图）。
>
> 状态：活跃；最后更新：2026-10-07（v3.2.0 已发布，节点快照工具与「UI 重设计 + 权限体系重构」随本版落版；Phase 1 仅剩步骤 F 真机验收，见 [ROADMAP-ADS.md](ROADMAP-ADS.md)）。

## ✅ v1.0 — 核心可用（已完成）

- [x] 无障碍服务自动点击「跳过」按钮（文本关键词匹配）
- [x] 自定义关键词管理
- [x] 跳过次数统计
- [x] 模拟开屏广告自测
- [x] 防误触机制（去抖 / 节流 / 节点上限 / 排除输入框）

## ✅ v2.0 — 全栈版（已完成）

### Android 客户端

- [x] **ViewID 规则引擎**：匹配控件资源 ID（如 `com.x:id/skip_view`），支持纯图片、无文字的跳过按钮
- [x] **应用管理页**：列出全部应用，逐项开启/关闭跳过，显示各应用跳过次数
- [x] **跳过日志页**：最近 200 条记录（时间、应用、包名），支持清空
- [x] **云端规则同步**：从服务端一键拉取全局关键词 / ViewID / 应用专属规则 / 禁用列表
- [x] **跳过上报**：每次跳过静默上报服务端（服务端不在线不影响本地）
- [x] 应用专属规则（关键词 + ViewID，云端下发）

### 后端服务（`server/`，Bun + TypeScript，零运行时依赖）

- [x] `GET /api/rules/latest` — 规则包下发
- [x] `PUT /api/rules` — 规则发布
- [x] `POST /api/skip` — 跳过上报
- [x] `GET /api/stats/summary` — 统计汇总
- [x] **管理后台网页**：规则编辑（关键词 / ViewID / 应用规则 / 禁用开关）、统计看板（总量 / 今日 / 14 天趋势 / 按应用排行 / 最近记录）

## ✅ v2.1 — 体验优化（已完成）

- [x] 开机自启与保活引导（电池优化白名单一键跳转）
- [x] 免打扰时段设置
- [x] 规则同步自动定时（JobScheduler，开机后恢复）
- [x] 日志导出（分享为文本）

## ✅ v2.2 — 架构增强（已完成）

- [x] **安全加固**：服务端鉴权（ADMIN_TOKEN）+ 载荷校验 + 限频 + CORS 白名单 + 备份轮转
- [x] **客户端 SafetyGuard**：硬编码黑名单防误触敏感按钮，云规则不可覆盖
- [x] **可测性改造**：AdNode 节点抽象 + Clock 注入，引擎可跑纯 JVM 单测（≥20 例）
- [x] **手动 DI**：AppContainer 收口依赖，不引入第三方框架
- [x] **规则缓存**：LruCache 按 (pkg → version) 缓存，事件高频路径只查表
- [x] **合批落盘**：StatsRepository 计数先进内存，5s 合批写 SP
- [x] **JobScheduler 三合一**：取代 AlarmManager + AlarmReceiver + BootReceiver
- [x] **协议 v1**：ETag/304 省流量、deviceId 限频、批量上报、健康检查（v0 兼容保留）
- [x] **分日统计**：按天分片存储，14 天趋势读 14 个小文件
- [x] **管理后台增强**：登录 + diff 预览 + 规则模拟器
- [x] **工程化**：CI（GitHub Actions）、R8 keep 无障碍类、values-en 中英双语
- [x] **环形日志 LogRing**：内存 500 条，设置页可导出

## ✅ v3.0 — 新架构重构（已完成）

- [x] **客户端 Compose + MVVM**：单 Activity + Navigation Compose，四屏（主页/应用/日志/设置）各自 Screen + ViewModel，StateFlow 驱动 UI
- [x] **AppEvents 状态总线**：Service → UI 通过 StateFlow/SharedFlow 桥接，取代 BroadcastReceiver 注册
- [x] **服务端迁移 Bun + TypeScript**：`server.js` 拆分为 `server.ts` + `src/{api,middleware,storage,utils}` 分层
- [x] **测试体系**：服务端 `bun:test` 单元 + 进程内冒烟；客户端引擎/护栏 JVM 单测
- [x] **CI**：GitHub Actions 双 job（Android 构建+单测 / 服务端测试）

## 🔜 分层广告治理（增量发版，自 v3.0.3 起；已发布至 v3.2.0）

版本序列以 [ROADMAP-ADS.md](ROADMAP-ADS.md) 的里程碑为准（避免两份文档各自承诺同一版本号）。**每个版本独立走 `release/vX.Y.Z` → 全门禁 → Squash Merge → 签名 Release**；本表表达的是「发布意图」，版本号在发版冻结时按 SemVer 最终确认。

- [x] **v3.0.3（M1a，已发布）**：L1 引擎内核——选择器第三通道（`engine/selector/` AST / 解析器 / 匹配器 + `AdNode.parent` / `previousSibling()`）。纯内核增量、无用户可见行为变化；技术方案见 [DESIGN-PHASE1-SELECTOR.md](DESIGN-PHASE1-SELECTOR.md) 步骤 A/B
- [x] **v3.0.4（M1a 补丁，已发布）**：安装可用性修复——`assembleRelease` 缺签名配置时回退 debug 签名、发布流水线强制 `apksigner verify` 并支持 Secrets 注入正式密钥，修复发布制品未签名导致手机报「解析软件包时出现问题」（[Issue #23](https://github.com/LiYihai1121/ui-app/issues/23)）；无功能行为变化
- [x] **v3.1.0（M1b，已发布）**：协议 v2——服务端 `selectors` 字段与校验 + `SyncClient` 解析 + 点击结果校验与本地规则黑名单（步骤 C/D）；并随本版本一并交付品牌更名「轻启」、启动器/磁贴图标重设计、首页与「我的」页 UI 重设计、ktlint 静态门禁与 Compose BOM/compileSdk 升级（用户可见变更详见 [CHANGELOG.md](../../CHANGELOG.md) 3.1.0）。**真机矩阵（Android 8/13/14/15 × MIUI/HarmonyOS/ColorOS/OriginOS）尚未完成**，维护者显式决定先行发布正式版（见 CHANGELOG 3.1.0 发布验收覆盖声明）；Git tag `v3.1.0` 已创建于 `main` 提交 `ad188b0`（PR #42 经 Squash 合入），制品由正式密钥签名并经 `apksigner verify` 复核（见 [RELEASE-HISTORY.md](RELEASE-HISTORY.md)）。
- [x] **v3.2.0（M1c，已发布）**：节点快照工具——App 内导出当前界面节点树 JSON，规则编写不再靠猜（步骤 E）。交付：`service/NodeSnapshot` 捕获与序列化 + 二分查找 96 KB 截断（`truncated=true` 标记）+ 设置页调试入口（服务未运行时显式提示）+ `ServiceIntentContractTest` 守护广播字面量与 `service/` 常量的单一真值源。**随本版本一并落版「UI 重新设计与权限体系重构」全部未发版内容**（语言选择、权限清单三态化等，详见 [CHANGELOG.md](../../CHANGELOG.md) 3.2.0）。已随 PR #46 Squash 合入 `main`（提交 `f5127dd`）并打 annotated tag `v3.2.0`（2026-10-06）发布，Release 资产 `AdSkip-v3.2.0.apk` 1,923,024 bytes + `SHA256SUMS`（见 [RELEASE-HISTORY.md](RELEASE-HISTORY.md)）。
- [ ] **v3.3.0（安全加固版，待发布）**：PR #55–#57——23 项安全漏洞修复（限流失效链 / 接收器导出 / 护栏绕过等）+ 管理端 TOTP 双因素认证 + 规则链路签名验证（防 MITM 注入规则）。出口条件：CI 全绿 + 密钥部署说明（`totp:gen` / `keys:gen`）+ 真机冒烟
- [ ] **v3.4.0（交互增强版，待发布）**：PR #58–#60——悬浮窗快捷开关、自定义取点规则（手动标注广告跳过位置）、按联合厂商标准的布局适配（触控下限 / 异形屏 insets）。出口条件：真机矩阵验收（悬浮层 / 前台服务的厂商差异）+ 商店 specialUse 用途说明
- [ ] **v3.5.0（M1d）**：Top 30 App 首批选择器规则入库 + 真机回归与性能采样 + 规则审核通道（步骤 F、L5 基础）
- [ ] **v3.6.0（M2）**：L2 网络过滤层——DNS 过滤 + `filter-rules` 路由
- [ ] **v3.7.0（M3）**：L3 防摇一摇模块（独立可选 APK）
- [ ] **v4.0.0（M4）**：L4 通知过滤 + ROM 指引 + 规则生态完善

> **2026-10-08 版本序列校准**：安全加固（#55–#57）与交互增强（#58–#60）是已交付待发布的两批能力，此前无版本承载；插入为 v3.3.0/v3.4.0 后，M1d/M2/M3 顺延为 v3.5.0/v3.6.0/v3.7.0，M4（major）不变。里程碑表与出口条件以 [ROADMAP-ADS.md](ROADMAP-ADS.md) 为准；发布链路预排期见 [RELEASE-HISTORY.md](RELEASE-HISTORY.md)。

### 🧪 预发布（RC，验收未完成）

- [x] **v3.1.0-rc.1（系统级体验 RC，已发布）**：快捷磁贴 + 厂商保活引导，见下方「已落地」小节。
  **按 [CONTRIBUTING.md](../../CONTRIBUTING.md)「发布验收」规则发预发布而非正式版**：本次触及无障碍服务、快捷磁贴、
  厂商跳转与系统权限面，而发布时尚无可用真机（`adb devices` 为空），厂商入口表与磁贴行为**尚未在
  MIUI / HarmonyOS / ColorOS / OriginOS / Android 13 上实测**。`v3.1.0` 正式版已发布（tag `v3.1.0`）；维护者显式决定覆盖真机验收要求先行发布，详见 [CHANGELOG.md](../../CHANGELOG.md) 3.1.0 发布验收覆盖声明；真机矩阵完成后如发现问题按 [CONTRIBUTING.md](../../CONTRIBUTING.md) 紧急变更与回滚走 hotfix。

  **模拟器部分验证（2026-09-28，Android 15 / API 35，`google_apis;android-34;x86_64`）**：
  ✅ 安装成功且 `versionCode 10 / versionName 3.1` 正确；
  ✅ `MainActivity` 启动并成为前台 Activity，无崩溃、无 error 级日志；
  ✅ **无障碍服务已绑定并启用**——`dumpsys accessibility` 显示
  `Bound services:{Service[label=AdSkip · Skip Splash Ads, feedbackType[FEEDBACK_GENERIC],
  eventTypes=[TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOW_CONTENT_CHANGED], ...]}`，
  说明 `AndroidManifest.xml` 声明与 `res/xml/skip_service_config.xml` 解析均正确；
  ✅ 厂商识别按预期落到 `Vendor.GENERIC`（`manufacturer=unknown` / `brand=Android`）；
  ✅ GENERIC 降级链的 Intent 均可解析：`ACCESSIBILITY_SETTINGS`、`IGNORE_BATTERY_OPTIMIZATION_SETTINGS`、
  `APPLICATION_DETAILS_SETTINGS`（带 `package:` data URI，实测跳转到 `InstalledAppDetails`）。

  > ⚠️ **模拟器不能替代厂商 ROM 验收**：MIUI / HarmonyOS / ColorOS / OriginOS 的自启动入口、
  > 电池管理 Intent、OEM 后台限制策略，以及磁贴在 OEM 快捷设置中的真实行为，**均无法在 AOSP 镜像上验证**。
  > `v3.1.0` 正式版已发布（tag `v3.1.0`）；维护者显式覆盖真机验收要求先行发布，真机矩阵（Android 8/13/14/15 + 上述 4 家厂商）仍需补做，发现问题按 [CONTRIBUTING.md](../../CONTRIBUTING.md) 紧急变更与回滚走 hotfix。
  > 磁贴在模拟器上未验证（需先在快捷设置中手动添加，headless 环境无法完成该交互）。

### 🧩 已落地（随 v3.1.0 交付）

- [x] **系统级体验：快捷磁贴 + 厂商保活引导**（已随 `v3.1.0-rc.1` 预发布、`v3.1.0` 正式版发布（tag `v3.1.0`））：
  下拉通知栏磁贴（`TileService`：一眼看服务状态、一次点击启停，Android 13+ 支持应用主动请求添加）；
  厂商 ROM 识别与自启动/后台管理一键跳转（MIUI / HarmonyOS / MagicOS / ColorOS / OriginOS / Flyme / OxygenOS / One UI），
  含 Android 11+ `<queries>` 包可见性声明与无障碍真实状态查询。
  > 原本属于 M4「ROM 指引」的一部分，鉴于「跳过静默失效」是当前最高频的用户投诉来源（强杀无障碍服务），提前到小版本交付；
  > M4 保留通知过滤与通知类 ROM 指引。详见 [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) 第 2/2.1/3 节。

> **为什么剥离原 `3.1.0`**：原计划把「选择器引擎 + 快照工具 + Top 30 规则 + 真机验收 ≥95%」压在单个 `3.1.0` 里，而引擎内核（步骤 A/B）已按 PR 增量并入 `main` 却无版本承载——继续维持大礼包会让未发布内容堆积，且真机指标反过来阻塞协议与工具。现按「是否产生用户可见能力」切分小版本：每个版本可独立验收、独立回滚（选择器是纯增量字段，服务端停发即回退 v1 行为，无数据迁移）。

### ✅ 已随 v3.2.0 发布（UI 重设计与权限体系重构）

> 本轮「UI 重新设计与权限体系重构」已随 v3.2.0（2026-10-06，tag `v3.2.0`）正式发布，具体用户可见变更见 [CHANGELOG.md](../../CHANGELOG.md) 3.2.0。贯穿三条原则：
> **① 权限与系统开关一律三态**——无法确认 ≠ 未开启；**② 同一事实只有一份真值源**；
> **③ 能机器检查的约定都写成门禁**（契约由 4 条增至 40 余条）。

- [x] **权限与系统开关清单**：四类开关（无障碍 / 电池豁免 / 厂商自启动 / 快捷磁贴）由三处收敛为一张可核对清单，
  置于「我的」页首位。厂商自启动与快捷磁贴**刻意停在「无法自动确认」**——系统无公开查询 API，
  谎报「未开启」会让已经配好的用户在系统页反复来回而状态永不变。
- [x] **无障碍与电池豁免改读系统真值**：此前 UI 只订阅进程信号，用户在系统设置里改完开关而 Service 回调未到时，
  首页会显示过期的「运行中」，与读真值的快捷磁贴说法不一致。现两屏都在 `ON_RESUME` 重查。
- [x] **三态化重构**：`device/AccessibilityStatus` 与 `device/BatteryExemption` 把「真值 / 进程信号 / 查询失败」
  收敛为 `ON`/`OFF`/`UNKNOWN`，判定为纯函数、可 JVM 单测穷举。
- [x] **UI 层交互细节**：补齐错误态（此前只有空态，失败会显示成「暂无内容」）、修复骨架屏永久卡死、
  首页补页面标题、大屏内容封顶抽为 `Modifier.screenContentWidth()`、组件库新增 `ErrorState` / `StatusDot`。
- [x] **无障碍修复**：标题补 `heading()` 语义、消除四处重复播报、全屏浮层做语义隔离（此前被遮住的控件
  仍可被 TalkBack 聚焦激活）、关键词 chip 的删除语义修正。
- [x] **配色补全**：补上一直走 M3 默认值的 `surfaceContainer*` 三个角色（默认值是紫调中性色，
  与品牌蓝不同色相）；新增 `values-night/themes.xml` 消除深色系统下的启动白屏。
- [x] **应用内界面语言选择**：可选跟随系统 / 简体中文 / English（默认跟随系统）。
  **双路径**：API 33+ 用平台 `LocaleManager`（系统级生效，会同步到系统「应用语言」设置），
  API 26–32 无平台 API，靠 `attachBaseContext` 包 Context + 重建 Activity。
  已在模拟器实测可逆（切中文 → 平台返回 `[zh-CN]`；切回跟随系统 → 返回 `[]` 且界面复原）。

> **已发布的遗留项（待后续版本处理）**：字阶 15 个 M3 角色中 7 个零引用、形状槽位 `medium`/`extraLarge` 零引用——
> 这是 M3 要求完整体系所致，已用契约固化「体系完整」，非遗漏；快捷磁贴的「是否已添加」若要可确认，
> 需在磁贴服务里持久化标志。**该存储问题的解法已在语言功能中出现**：把跨层共享的偏好放进
> `core/`（`ui/` 与 `device/` 都允许依赖它），即可绕开「`device/` 不能依赖 `data/`」的限制
> （见 `core/LanguagePreferences`）。API 26–32 的低版本语言路径亦待真机覆盖（与 v3.1.0 真机矩阵欠账一并处理）。

### 候选池（未排期，不承诺版本）

- [x] 自定义取点规则（2026-10-08 交付，`feature/point-rules`）：对无法识别的广告手动标注跳过区域；实现为**透明悬浮层直接在真实界面上取点**（无需截屏/录屏权限，所见即所得），坐标按屏幕比例存本机，节点未命中时兜底点击
- [x] 悬浮窗快捷开关（2026-10-08 交付，`feature/floating-toggle`）：悬浮面板一键暂停/恢复自动跳过、切换免打扰，区别于 ADS Phase 4 的「悬浮窗权限引导」
- [ ] 服务端 Docker 镜像与一键部署脚本
- [ ] 多设备规则共享（局域网规则仓库）
- [ ] 构建框架工程化（**工程基础设施，不产生用户可见能力**，故不占用版本号）：阶段 A（目录结构契约）与**阶段 B（version catalog + `build-logic` 约定插件 + Gradle 硬化）已实施**，阶段 C（`core:common` / `core:engine` 模块拆分）待排期；已知坑位与分步回滚策略见 [DESIGN-BUILD-FRAMEWORK.md](DESIGN-BUILD-FRAMEWORK.md)

> 注：第 3 节「明确不做的」中「不拦截广告内容」与 L2 DNS 过滤的边界张力，已在 [ROADMAP-ADS.md](ROADMAP-ADS.md) 第 3 节列为 Phase 0 合规评审项，L2 启动前必须修订该声明。

## 📌 明确不做的

- ❌ 拦截/破解广告内容本身（只点击界面已有「跳过」按钮）
- ❌ 收集用户隐私数据（无账号体系，统计仅存本地/自建服务端）
- ❌ 上架应用商店的商业化运营
