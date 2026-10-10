---
name: android-compose-design
description: Guide Compose UI work in this repo's Android client toward polished, mass-appeal design - theme tokens, typography, spacing, motion and shared components. Triggers on UI/screen/theme/motion/component tasks; not for engine, network, service or server-side changes.
metadata:
  audience: android-ui-contributors
  workflow: feature-branch
---

## 规范唯一事实源（此处不复述规范）

| 关注点 | 事实源 |
| --- | --- |
| 客户端分层职责与 `ui/` 层边界（禁止 import `service/` 等） | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 2.1 节 |
| 目录结构契约（新增文件落点） | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 2.2 节 |
| 主题 token 与设计系统实现 | `client/app/src/main/java/com/qingqi/adskip/ui/theme/`（`Color.kt` / `Type.kt` / `Spacing.kt` / `Theme.kt`） |
| 可复用组件 | `ui/components/`（`Common.kt`、`Feedback.kt`、`StatusOrb.kt`） |
| UI 契约测试 | `UiContractTest` / `ColorSchemeContractTest` / `ScreenHeaderContractTest` / `UiStateContractTest` |

> 本文件**刻意不复制**色板值、字号阶梯、间距数值：token 以 `ui/theme/` 代码为准，复制进文档会漂移。

## 开工前自检

1. 读 `ui/theme/` 现有 token——颜色、字体、间距**一律走 theme**，禁止在 Composable 内硬编码色值/字号/dp 魔数；
2. 图标只用 `material-icons-core` 集合（依赖选择理由见 `client/app/build.gradle.kts` 注释，勿擅自引入 extended 图标库）；
3. 新屏幕优先复用 `ui/components/` 既有组件与 `ScreenHeader` 模式（由 `ScreenHeaderContractTest` 固化）；
4. 注意 targetSdk 刻意留在 35 的决策（`build-logic` 约定插件注释）：Android 16 强制 edge-to-edge 等行为变更与 UI 改动耦合，升级前先在 PR 说明归因。

## 提交前自检

1. `cd client && ./gradlew testDebugUnitTest --tests "*UiContract*" --tests "*ColorScheme*" --tests "*ScreenHeader*" --tests "*UiState*"` 全绿；
2. UI 状态类（UiState/UiEffect）变更同步对应契约测试——契约先行：先改测试再改实现；
3. 动效使用 Material 3 内建 motion 体系，不引第三方动画库；大众向克制原则：动效服务于状态反馈，不做纯装饰动画；
4. 深色模式与动态字体（系统字体缩放）下目视可用，不得出现固定高度截断文本。
