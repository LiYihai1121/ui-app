# 净启动 AdSkip

一个自动跳过 Android 应用开屏广告的**全栈**工具：Android 客户端（无障碍服务）+ 自建后端（规则中心 / 统计 / 管理后台）。

> 与开源项目 GKD / 李跳跳 属同类技术方案。

## 功能（v2.0）

**Android 客户端**
- ✅ 自动点击开屏广告「跳过」按钮，双通道识别：**文本关键词** + **控件 ViewID**（支持纯图片按钮）
- ✅ 应用管理：逐项开启/关闭跳过，各应用跳过次数统计
- ✅ 跳过日志：最近 200 条记录，可清空
- ✅ 云端规则同步：一键拉取关键词 / ViewID / 应用专属规则 / 禁用列表
- ✅ 内置模拟开屏广告测试
- ✅ 跳过上报服务端（服务端不在线时静默跳过，纯本地照常工作）

**后端服务（`server/`，Node.js 零依赖）**
- ✅ 规则中心：全局关键词、ViewID 规则、应用专属规则、禁用列表
- ✅ 统计 API：累计 / 今日 / 14 天趋势 / 按应用排行 / 最近记录
- ✅ 管理后台网页：规则编辑发布 + 统计看板，浏览器即可操作

## 快速开始

### 1. 启动后端（本机）

```bash
cd server
node server.js
# 管理后台: http://localhost:3210/admin
# 局域网:    http://<本机IP>:3210
```

### 2. 安装客户端

把 `AdSkip-v2.0.apk` 传到手机安装（Android 8.0+），打开后：

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
父链找可点击节点执行 ACTION_CLICK；否则按坐标模拟手势
   ↓
本地记录 + 上报服务端
```

防误触：同应用 1.2s 去抖、150ms 全局节流、单次遍历 ≤500 节点、忽略系统 UI、按应用禁用。

## 工程结构

```
app/src/main/java/com/ldp/adskip/   # Android 客户端（Kotlin，零第三方依赖）
├── ui/                             # 界面层
│   ├── MainActivity.kt             #   主页（状态/统计/关键词/测试/导航）
│   ├── AppListActivity.kt          #   应用管理
│   ├── LogsActivity.kt             #   跳过日志
│   └── SettingsActivity.kt         #   云同步设置
├── service/
│   └── SkipAdService.kt            # 服务层（薄编排：事件/节流/点击/广播）
├── engine/
│   ├── SkipRuleEngine.kt           # 引擎层（纯匹配逻辑，文本+ViewID 双通道）
│   └── RuleSet.kt                  #   规则集模型
├── data/
│   ├── Prefs.kt                    # 存储原语（SharedPreferences）
│   ├── RulesRepository.kt          # 规则仓库（全局+专属合并、开关、云端落地）
│   └── StatsRepository.kt          # 统计仓库（计数、日志）
└── net/
    └── SyncClient.kt               # 网络层（规则同步 + 跳过上报）

server/                             # 后端（Node.js 原生 http，零依赖）
├── server.js                       # 进程引导与路由分发
├── src/
│   ├── api.js                      # /api/* 处理器
│   ├── store.js                    # 存储层（JSON 文件原子读写）
│   ├── httpUtil.js                 # CORS / JSON / body 工具
│   └── config.js                   # 集中配置
├── public/
│   ├── index.html                  # 产品落地页
│   └── admin.html                  # 管理后台
├── test/smoke.js                   # 冒烟测试（npm test）
└── data/                           # rules.json / stats.json
```

详细设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 构建

```bash
gradle assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17+、Android SDK（compileSdk 35）。或直接用 Android Studio 打开。

## 兼容性

- Android 8.0 (API 26) 及以上；iOS 不支持（无开放的无障碍自动化接口）
- 服务端可跑在任何有 Node.js 18+ 的机器上

## 合规提示

本工具仅通过系统无障碍能力，**点击广告界面本身已展示的「跳过」按钮**，不拦截、修改或破解任何网络请求与广告内容。请仅用于个人设备，勿用于商业用途。
