# 净启动 AdSkip

一个自动跳过 Android 应用开屏广告的**全栈**工具：Android 客户端（无障碍服务）+ 自建后端（规则中心 / 统计 / 管理后台）。

> 与开源项目 GKD / 李跳跳 属同类技术方案。

## 功能（v3.0）

**Android 客户端（Kotlin + Jetpack Compose，MVVM）**
- ✅ 自动点击开屏广告「跳过」按钮，双通道识别：**文本关键词** + **控件 ViewID**（支持纯图片按钮）
- ✅ **安全护栏 SafetyGuard**：硬编码黑名单防误触敏感按钮（支付/授权/登录等），云规则不可覆盖
- ✅ 应用管理：逐项开启/关闭跳过，各应用跳过次数统计
- ✅ 跳过日志：最近 200 条记录，可清空
- ✅ 云端规则同步（v1 协议：ETag/304 省流量、deviceId 限频、批量补报）
- ✅ 内置模拟开屏广告测试
- ✅ 跳过上报服务端（服务端不在线时静默跳过，纯本地照常工作）
- ✅ 自动同步规则（JobScheduler 每 12 小时，跨重启持久化，无需 BootReceiver）
- ✅ 免打扰时段与电池优化白名单引导
- ✅ 环形日志（内存 500 条，设置页可导出分享）
- ✅ 中英双语资源（values / values-en）

**后端服务（`server/`，Bun + TypeScript，零运行时依赖）**
- ✅ 规则中心：全局关键词、ViewID 规则、应用专属规则、禁用列表
- ✅ **鉴权**（ADMIN_TOKEN）、**载荷校验**（与客户端同源约束）、**限频**（令牌桶 per-IP/per-deviceId）
- ✅ 统计 API：累计 / 今日 / 14 天趋势 / 按应用排行 / 最近记录（按天分片存储）
- ✅ 管理后台网页：登录 + **diff 预览** + **规则模拟器** + 统计看板
- ✅ 协议 v1：ETag/304、批量上报、健康检查（旧 v0 路由兼容保留）
- ✅ 优雅停机（SIGTERM/SIGINT → 落盘再退出）、CORS 白名单、规则备份轮转

## 快速开始

### 1. 启动后端（本机）

```bash
cd server
bun install
# 生产环境建议设置 ADMIN_TOKEN（不设则写接口返回 503）
ADMIN_TOKEN=your-secret-token bun run server.ts
# 落地页:   http://localhost:3210/
# 管理后台: http://localhost:3210/admin
# 局域网:   http://<本机IP>:3210
```

### 2. 安装客户端

构建 APK 传到手机安装（Android 8.0+），打开后：

1. 点「打开无障碍设置」→ 开启「净启动 AdSkip」服务
2. （建议）将应用加入电池优化白名单，防止后台被清理
3. 点「云端规则同步」→ 服务器地址填 `http://<本机IP>:3210` → 「立即同步云端规则」
4. 打开任意带开屏广告的 App 即可自动跳过；可先用「测试：模拟开屏广告」验证

## 技术原理

```
应用启动 → 开屏广告出现
   ↓ 无障碍事件（窗口状态/内容变化）
遍历节点树 → ① 文本/描述命中关键词（≤12字、可见、非输入框）
            ② 控件 ID 命中 ViewID 规则（如 com.x:id/skip_view）
   ↓ 命中
SafetyGuard 安全护栏复核（黑名单/可见性/面积）
   ↓ 通过
父链找可点击节点执行 ACTION_CLICK；否则按坐标模拟手势
   ↓
本地记录 + 上报服务端
```

防误触：同应用 1.2s 去抖、150ms 全局节流、单次遍历 ≤500 节点、忽略系统 UI、按应用禁用、SafetyGuard 硬编码黑名单。

## 工程结构

```
app/src/main/java/com/ldp/adskip/   # Android 客户端（Kotlin，零第三方依赖）
├── AdskipApp.kt                    # Application + AppContainer（手动 DI）
├── core/                           # Clock / AppExecutors / LogRing / AppEvents（状态总线）
├── ui/                             # 界面层（Compose 单 Activity + Navigation）
│   ├── MainActivity.kt            #   唯一 Activity
│   ├── Routes.kt                  #   导航路由
│   ├── theme/Theme.kt             #   主题
│   ├── home/                      #   主页 Screen + ViewModel
│   ├── apps/                      #   应用管理 Screen + ViewModel
│   ├── logs/                      #   跳过日志 Screen + ViewModel
│   └── settings/                  #   云同步设置 Screen + ViewModel
├── service/
│   └── SkipAdService.kt            # 服务层（薄编排：事件/节流/点击/SafetyGuard）
├── engine/
│   ├── AdNode.kt                   # 节点抽象接口（引擎不依赖框架类）
│   ├── FrameworkAdNode.kt          # 框架适配（包装 AccessibilityNodeInfo）
│   ├── SafetyGuard.kt              # 安全护栏（黑名单/合法性）
│   ├── SkipRuleEngine.kt           # 引擎层（纯匹配逻辑，文本+ViewID 双通道）
│   └── RuleSet.kt                  # 规则集模型（+ schemaVersion）
├── data/
│   ├── Prefs.kt                    # 存储原语（SharedPreferences + deviceId + rulesHash）
│   ├── RulesRepository.kt          # 规则仓库（LruCache 缓存/版本失效/schemaVersion 校验）
│   └── StatsRepository.kt          # 统计仓库（合批落盘）
├── net/
│   └── SyncClient.kt               # 网络层（v1: ETag/304/deviceId/批量补报）
└── sync/
    └── SyncJobService.kt           # JobScheduler 定时同步（三合一，跨重启持久化）

app/src/test/java/com/ldp/adskip/   # JVM 单测（FakeAdNode + 引擎/护栏测试，37 项）

server/                             # 后端（Bun + TypeScript，零运行时依赖）
├── server.ts                       # Bun.serve 入口、路由分发、优雅停机
├── src/
│   ├── api/                        # 路由拆分（v0+v1）
│   │   ├── index.ts                #   路由分发
│   │   ├── rulesApi.ts             #   规则下发/发布/模拟器
│   │   ├── statsApi.ts             #   统计汇总
│   │   └── healthApi.ts            #   健康检查
│   ├── middleware/
│   │   ├── auth.ts                 #   Bearer token 鉴权
│   │   └── rateLimit.ts            #   内存令牌桶限流
│   ├── storage/
│   │   └── store.ts                #   规则（缓存+备份轮转）/ 统计（分日分片+延迟刷盘）
│   ├── utils/
│   │   ├── httpUtil.ts             #   CORS 白名单 / 安全 JSON 解析 / Handler 类型
│   │   └── validate.ts             #   载荷校验（与客户端同源约束）
│   └── config.ts                   # 集中配置（env 覆盖）
├── public/
│   ├── index.html                  # 产品落地页
│   └── admin.html                  # 管理后台（登录 + diff 预览 + 规则模拟器）
├── test/
│   ├── unit.test.ts                # 单元测试（validate/auth/rateLimit）
│   └── smoke.test.ts               # 冒烟测试（in-process，全部路由 v0+v1）
└── data/                           # rules.json（规则包）/ stats/（分日统计）/ backups/
```

详细设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 构建

```bash
# Android 客户端
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk（发布时重命名版本号）

# Release 封装（R8 混淆 + 签名）
# 签名参数写在 local.properties（不入库）：
#   adskip.storeFile=<keystore 路径>  adskip.storePassword=***
#   adskip.keyAlias=<别名>           adskip.keyPassword=***
# 未配置签名时 assembleRelease 产出未签名包
./gradlew assembleRelease

# 客户端 JVM 单测
./gradlew testDebugUnitTest

# 服务端测试与类型检查
cd server
bun install
bun test              # 单元 + 冒烟（40 项）
bun run typecheck     # tsc --noEmit
```

要求：JDK 17+、Android SDK（compileSdk 35）、Bun 1.1+（服务端）。Android 部分也可直接用 Android Studio / IntelliJ 打开。

## 分支与版本

- 工作流：branch-guard（`.opencode/skills/branch-guard/SKILL.md`）——所有改动在功能分支上进行，测试通过后 `--no-ff` 合并回 `main`。
- 版本规则：Android 使用 `versionCode` 递增、`versionName` 对外展示；服务端版本号见 `server/package.json`。
- 功能路线以 [ROADMAP.md](ROADMAP.md) 为准，架构与模块职责以 [ARCHITECTURE.md](ARCHITECTURE.md) 为准。

## 合规提示

本工具仅通过系统无障碍能力，**点击广告界面本身已展示的「跳过」按钮**，不拦截、修改或破解任何网络请求与广告内容。请仅用于个人设备，勿用于商业用途。

> 1. 此类工具不符合 Google Play 对 AccessibilityService 的审核口径，分发以 APK 自建渠道为准，不上架应用商店。
> 2. **隐私声明**：数据默认不出本机。上报为可选且仅含包名、时间戳和匹配通道（text/viewId），不含任何用户隐私数据。服务端无账号体系，统计仅存自建服务端。
