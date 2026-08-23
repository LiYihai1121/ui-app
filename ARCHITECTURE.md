# 净启动 AdSkip 架构文档

## 1. 系统全景

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android 客户端                            │
│                                                                  │
│  ui/                 service/           engine/                  │
│  ├ MainActivity      SkipAdService      SkipRuleEngine           │
│  ├ AppListActivity   （薄编排层）──────▶（纯匹配逻辑）             │
│  ├ LogsActivity           │                                      │
│  └ SettingsActivity       ▼                                      │
│        │                 data/                                   │
│        │                ├ Prefs（SharedPreferences 原语）         │
│        │                ├ RulesRepository（规则合并/开关）        │
│        │                └ StatsRepository（计数/日志）            │
│        │                                                         │
│        └──────────────▶ net/SyncClient（HttpURLConnection）      │
└──────────────────────────────┬───────────────────────────────────┘
                               │ HTTP（局域网）
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      后端服务（Node.js 零依赖）                   │
│                                                                  │
│  server.js（路由引导）                                            │
│     ├─ src/api.js        /api/* 处理器                           │
│     │     └─ src/store.js    JSON 文件原子读写（tmp+rename）      │
│     ├─ src/httpUtil.js   CORS / JSON / body                      │
│     └─ src/config.js     端口 / 路径 / 限额                      │
│                                                                  │
│  public/index.html   产品落地页                                  │
│  public/admin.html   管理后台（规则编辑 + 统计看板）              │
│  data/rules.json     规则包      data/stats.json  统计           │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 客户端分层职责

| 层 | 模块 | 职责 | 不做的事 |
|---|---|---|---|
| **ui/** | 4 个 Activity | 展示与用户交互 | 不直接读 SharedPreferences、不碰网络 |
| **service/** | SkipAdService | 事件接收、节流去抖、点击执行、广播通知 | 不含匹配规则逻辑 |
| **engine/** | SkipRuleEngine + RuleSet | 纯匹配：文本/ViewID 双通道，返回目标节点 | 不执行点击、不读存储 |
| **data/** | Prefs / RulesRepository / StatsRepository | 存储原语 + 领域仓库（规则合并、开关、计数、日志） | 不感知 UI 与网络格式 |
| **net/** | SyncClient | HTTP 传输与 JSON 解析，落地委托 RulesRepository | 不直接改存储键值 |

**关键设计**：`RulesRepository.ruleSetFor(pkg)` 是规则的唯一组装点——全局关键词 + 应用专属关键词 + 全局 ViewID + 应用专属 ViewID + 禁用开关，合并为一个不可变的 `RuleSet` 交给引擎。新增规则来源（如坐标规则）只需扩展 RuleSet 与引擎，不触碰服务层。

## 3. 数据流

**跳过一次广告：**
```
窗口事件 → SkipAdService（150ms 节流 / 1.2s 去抖）
        → RulesRepository.ruleSetFor(pkg) 取规则
        → SkipRuleEngine.findTarget(root, ruleSet) 找目标
        → clickNode：父链 ACTION_CLICK，兜底坐标手势
        → StatsRepository.recordSkip（本地计数+日志）
        → SyncClient.reportSkip（静默上报，失败不影响本地）
        → 广播 ACTION_SKIPPED → 主页刷新统计
```

**云端规则同步：**
```
设置页触发 → SyncClient GET /api/rules/latest
          → 解析 JSON → RulesRepository.applyCloudRules 落地
          → 记录同步时间 → 主页回调提示
```

**自动规则同步：**
```
设置页开启 → SyncScheduler 注册 AlarmManager（每 12 小时）
          → SyncAlarmReceiver 调用 SyncClient 静默同步
设备开机 → BootReceiver 检查开关并恢复定时任务
```

## 4. 服务端分层职责

| 模块 | 职责 |
|---|---|
| `server.js` | 进程引导、路由分发（静态页 / 下载 / API）、启动信息 |
| `src/api.js` | 4 个 API 的请求校验与响应，不含存储细节 |
| `src/store.js` | 规则/统计的读写与聚合，写操作原子化 |
| `src/httpUtil.js` | CORS、JSON/HTML 响应、请求体上限保护 |
| `src/config.js` | 全部可调参数集中（端口、路径、限额） |

## 5. 设计决策

- **零第三方依赖**：客户端仅 Kotlin 标准库 + 框架 API，服务端仅 Node 原生模块。安装包 858KB，服务端无需 npm install，任意 Node 18+ 可跑。
- **JSON 文件存储**：单进程本地服务，数据量小；tmp+rename 原子写避免损坏。若未来多实例/高并发，再换 SQLite（存储层已隔离，替换不影响 API 层）。
- **离线优先**：客户端一切功能本地可用；服务端不可达时上报静默失败。
- **节流三重保护**：同应用 1.2s 去抖、全局 150ms 扫描节流、单次遍历 500 节点上限——无障碍事件频率极高，必须防过度扫描耗电。

## 6. 扩展点

| 需求 | 改动位置 |
|---|---|
| 新匹配通道（坐标/图像规则） | `engine/RuleSet` 加字段 + `SkipRuleEngine.matches` 加分支 |
| 定时自动同步 | `sync/SyncScheduler` + `sync/SyncAlarmReceiver`，复用 `net/SyncClient` |
| 服务端换数据库 | 只改 `src/store.js` |
| 新增 API | `src/api.js` 加分支；客户端 `SyncClient` 加方法 |
| 客户端换 Room/网络库 | 只改 `data/Prefs` 与 `net/SyncClient` 内部实现 |

## 7. 测试

- 服务端冒烟测试：`cd server && npm test`（8 项：落地页 / 后台 / 规则 / 上报 / 统计 / 下载 / 404 / 非法规则）
- 客户端：App 内「测试：模拟开屏广告」端到端验证无障碍链路
