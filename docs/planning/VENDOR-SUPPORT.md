# 全厂商适配计划（VENDOR-SUPPORT）

> 本页是「手机厂商 ROM 适配」专项的**计划事实源**：厂商分层、每家统一工作项、阶段划分与验收口径。
> 版本号以 [ROADMAP.md](ROADMAP.md) 为准（本页不另立版本序列），文档入口见 [../README.md](../README.md)（文档地图）。
> 状态：规划（R0 未启动）；最后更新：2026-09-29。

## 1. 背景与「适配」的定义

国内深度定制 ROM（MIUI/HyperOS、HarmonyOS、MagicOS、ColorOS、OriginOS、Flyme、One UI）会在息屏或切后台一段时间后强杀无障碍服务，「跳过」随之静默失效——这是当前最高频的用户投诉来源，也是 [ROADMAP.md](ROADMAP.md) 提前把「快捷磁贴 + 厂商保活引导」并入 `v3.1.0-rc.1` 的原因。

本计划中，对一个厂商的「适配」= 五件事：

1. **ROM 识别**：`device/VendorKeepAlive.kt` 的 `ALIASES` 覆盖 manufacturer/brand 变体与子品牌；
2. **保活入口表**：`candidates()` 提供「自启动 / 后台管理」候选链 + 手动路径兜底文案；
3. **强杀对抗与状态自愈**：被杀可被察觉、可提示恢复（通用能力优先于逐家特化）；
4. **广告识别规则**：该 ROM 系统应用开屏的关键词 / ViewID / 选择器样本入库；
5. **系统集成面验证**：快捷磁贴、电池优化白名单、权限引导在该 ROM 的真实行为。

实现落点与分层边界见 [../architecture/ARCHITECTURE.md](../architecture/ARCHITECTURE.md) 第 2.1 节（`device/` 层）；入口表变更必须同步 `AndroidManifest.xml` `<queries>`，由 `ManifestContractTest` 强制。

## 2. 现状盘点（截至本页创建）

| 能力 | 现状 |
| --- | --- |
| ROM 识别 | ✅ 9 个枚举：小米（含 Redmi/POCO）、华为、荣耀、OPPO（含 realme）、vivo（含 iQOO）、魅族、一加、三星、GENERIC |
| 保活入口表 | ✅ 每家 1~3 个候选组件 + 通用兜底链；跳转前 `PackageManager` 复核可解析，失败逐级降级 |
| 契约守护 | ✅ `<queries>` 由 `ManifestContractTest` 校验；识别与磁贴决策为纯 Kotlin + JVM 单测 |
| 电池白名单 | ✅ 直连请求页 → 白名单列表页降级 |
| 快捷磁贴 | ✅ Android 13+ `requestAddTileService`，含 `NoSuchMethodError` 降级 |
| 缺口 | ❌ 联想/moto、努比亚/红魔、中兴、黑鲨、华硕 ROG、索尼未入表（走 GENERIC）；❌ 全部入口表**未经真机实测**（`v3.1.0-rc.1` 预发布的原因）；❌ 被强杀后无自检/自愈提示；❌ 各 ROM 系统应用广告规则零覆盖 |

## 3. 厂商全景与优先级

### P0 — 国内 TOP 5（强杀最激进，`v3.1.0` 真机验收的硬依赖）

| 厂商 | ROM | 现有入口 | 待办重点 |
| --- | --- | --- | --- |
| 小米 / Redmi / POCO | MIUI 14 / HyperOS 1-2 | ✅ 3 候选 | HyperOS 入口复核；「最近任务下拉锁定」引导；省电策略设「无限制」 |
| 华为 | EMUI 13/14、HarmonyOS 4 | ✅ 3 候选 | 鸿蒙 4 电池管家入口复核；HarmonyOS NEXT 见第 3.4 节 |
| 荣耀 | MagicOS 8/9/10 | ✅ 2 候选 | 分家后独立入口实测；补「应用启动管理」手动路径 |
| OPPO + realme | ColorOS 13/14/15 | ✅ 3 候选（含 oplus 新包名） | realme UI 包名差异实测；「锁定后台」手动引导 |
| vivo + iQOO | OriginOS 3/4 | ✅ 2 候选 | 后台高耗电提醒关闭引导；行为簿白名单入口复核 |

### P1 — 已有代码、待真机实测补全

| 厂商 | ROM | 现有入口 | 待办重点 |
| --- | --- | --- | --- |
| 三星 | One UI 5/6/7（国行 + 国际版） | ✅ 2 候选 | 国行/国际版包名差异（`sm_cn` vs `lool`）；「不允许休眠」引导 |
| 一加 | OxygenOS / ColorOS 国内版 | ✅ 1 候选 | ColorOS 化后链式启动入口复核；与 OPPO 表的差异 |
| 魅族 | Flyme 10/11 | ✅ 2 候选 | `EXTRA_NAME` 传参约定实测；Flyme 11 新权限中心 |

### P2 — 长尾（当前走 GENERIC 兜底）

| 厂商/子品牌 | ROM | 建议 |
| --- | --- | --- |
| 联想 / moto | my ui（近原生） | 补 alias `motorola`/`lenovo`；入口≈原生，重点验证电池白名单 |
| 努比亚 / 红魔 | RedMagic OS | 补 alias `nubia`/`redmagic`，自启动页组件调研 |
| 中兴 / Axon | MiFavor | 补 alias `zte`，低优先 |
| 黑鲨 | JOYUI | 补 alias `blackshark`（独立于小米 ROM，入口单独调研） |
| 华硕 / ROG | ZenUI | 补 alias `asus`/`rog`，近原生 |
| 索尼 Xperia | 近原生 | 补 alias `sony`，走通用链 |
| Google Pixel / Nothing / CMF | 原生 | 保持 GENERIC，模拟器即可覆盖 |

### 3.4 明确不适配（避免无效 Issue 跟进）

- **华为 HarmonyOS NEXT（纯血鸿蒙）**：不运行 APK，需 ArkTS 原生重写——单独立项决策，不在本计划内；
- **iOS**：非本项目平台；
- 已停产小众品牌（360、酷派等）：仅走 GENERIC，不投入真机。

## 4. 每家厂商的统一工作项（清单模板）

对 P0/P1 每家、P2 按序执行：

1. **识别**：`VendorKeepAlive.ALIASES` 补 manufacturer/brand 变体（纯数据，JVM 单测断言优先级顺序）；
2. **入口表**：`candidates()` 补候选链（自启动 → 后台/电池 → 锁定后台提示），同步 `<queries>`——先补失败测试再补表（契约先行）；
3. **手动路径文案**：入口全失效时的降级提示补每家具体路径（中英双语）；
4. **强杀对抗验证**：息屏 8h / 后台 30min /「一键清理」后无障碍是否存活；`JobScheduler` 12h 任务是否被清、`AdskipApp.onCreate` 兜底重注册是否生效；并验证**通用「服务被杀自检」**（对照系统启用列表与进程内状态，不一致即提示重开）；
5. **磁贴验证**：`requestAddTileService` 在该 ROM 定制通知栏中的真实表现；
6. **广告识别样本**：该 ROM 系统应用（应用市场/浏览器/天气/负一屏）开屏广告的文本与 ViewID 样本，经 [ROADMAP.md](ROADMAP.md) `3.2.0` 快照工具采集，规则入库按 `3.3.0` 口径；
7. **回归记录**：按「厂商 × ROM 版本 × 结果」记入本页第 6 节的验收矩阵。

## 5. 分阶段计划（R0 → R2）

| 阶段 | 时间 | 内容 | 出口条件 | 挂靠版本 |
| --- | --- | --- | --- | --- |
| **R0 打样** | 第 1~2 周 | P0 五家入口表静态复核 + 真机各 1 台实测；「服务被杀自检」通用功能；模拟器补跑原生系（GENERIC） | P0 入口跳转 5/5；自检上线；`v3.1.0-rc.1` 真机验收清单全绿 | **解锁 `v3.1.0` 正式版**（[ROADMAP.md](ROADMAP.md) 既有约束） |
| **R1 扩表** | 第 3~6 周 | P1 三家真机实测补表；P2 别名 + 入口调研（云真机）；各 ROM 系统应用广告样本采集 | P0+P1 共 8 家实测通过；P2 别名入表且单测覆盖 | 随 `3.2.0` / `3.3.0` 增量携带 |
| **R2 长尾与生态** | 第 7~12 周 | P2 全部入表；Issue 模板加「厂商/ROM 版本/入口是否跳对/是否被杀」字段；厂商支持矩阵对用户公示 | 众测回收 ≥3 家入口修正；公示页上线 | 并入 **M4（`4.0.0`「ROM 指引」）** 收口 |

> 顺序规则（[../README.md](../README.md) 维护规则）：改本计划 → 同步 [ROADMAP.md](ROADMAP.md) 阶段意图 → 最后记 `CHANGELOG.md`；公示页届时登记文档地图。

## 6. 测试矩阵与验收口径

| 层级 | 手段 | 覆盖对象 |
| --- | --- | --- |
| L0 纯 JVM | `testDebugUnitTest`：识别优先级、候选链顺序、`<queries>` 契约 | 全部厂商（表驱动） |
| L1 模拟器 | AOSP API 33/34/35 | Pixel / moto / Nothing / 索尼等近原生系 |
| L2 真机 | 每家 ≥1 台，最新稳定版 + 上一版 | P0 五家必持，P1 尽量持有 |
| L3 云真机 | Testin / WeTest / 华为远程真机（AOSP 镜像无法验证 OEM 入口） | P2 长尾机型 |
| L4 众测 | Issue 模板 + `LogRing` 导出（设置页「导出分享」） | 全长尾 |

**单台验收清单**（每家同口径，横向可比）：安装 → 无障碍开启 → 模拟开屏测试通过 → 保活入口跳转落页正确 → 息屏/清理存活 → 磁贴添加与启停 → 12h Job 恢复 → 无崩溃（`logcat -b crash` 为空）。

## 7. 风险与对策

| 风险 | 对策 |
| --- | --- |
| 入口表随 ROM 升级失效（社区经验值本质） | 多级降级 + `PackageManager` 复核（已有）；众测回收修正；手动路径文案兜底 |
| HarmonyOS NEXT / 新品牌不兼容 | 第 3.4 节明示边界，独立决策是否立项 |
| 无真机阻塞发版 | 维持 rc→正式节奏：真机验收是 `v3.1.0` 出口条件，不降级 |
| 逐家适配线性膨胀 | 通用能力（被杀自检、降级链、快照工具）优先于逐家特化——一次投入全厂商受益 |