# 净启动 AdSkip 产品路线图

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

### 后端服务（`server/`，零依赖 Node.js）
- [x] `GET /api/rules/latest` — 规则包下发
- [x] `PUT /api/rules` — 规则发布
- [x] `POST /api/skip` — 跳过上报
- [x] `GET /api/stats/summary` — 统计汇总
- [x] **管理后台网页**：规则编辑（关键词 / ViewID / 应用规则 / 禁用开关）、统计看板（总量 / 今日 / 14 天趋势 / 按应用排行 / 最近记录）

## ✅ v2.1 — 体验优化（已完成）
- [x] 开机自启与保活引导（电池优化白名单一键跳转）
- [x] 免打扰时段设置
- [x] 规则同步自动定时（零依赖 AlarmManager，开机后恢复）
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

## ✅ v3.0 — 新架构重构（当前版本，已完成）
- [x] **客户端 Compose + MVVM**：单 Activity + Navigation Compose，四屏（主页/应用/日志/设置）各自 Screen + ViewModel，StateFlow 驱动 UI
- [x] **AppEvents 状态总线**：Service → UI 通过 StateFlow/SharedFlow 桥接，取代 BroadcastReceiver 注册
- [x] **服务端迁移 Bun + TypeScript**：`server.js` 拆分为 `server.ts` + `src/{api,middleware,storage,utils}` 分层
- [x] **测试体系**：服务端 `bun:test` 单元 + 进程内冒烟（40 项）；客户端引擎/护栏 JVM 单测（37 项）
- [x] **CI**：GitHub Actions 双 job（Android 构建+单测 / 服务端测试）

## 🔜 v3.1 — 能力增强（规划中）
- [ ] 截屏取点自定义规则（对无法识别的广告手动标注跳过区域）
- [ ] 悬浮窗快捷开关
- [ ] 服务端 Docker 镜像与一键部署脚本
- [ ] 多设备规则共享（局域网规则仓库）

## 📌 明确不做的
- ❌ 拦截/破解广告内容本身（只点击界面已有「跳过」按钮）
- ❌ 收集用户隐私数据（无账号体系，统计仅存本地/自建服务端）
- ❌ 上架应用商店的商业化运营
