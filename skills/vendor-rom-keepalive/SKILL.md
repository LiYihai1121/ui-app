---
name: vendor-rom-keepalive
description: Guide for vendor ROM keep-alive and system-navigation work in the device layer - vendor entry tables, degrading permission-navigation flows, `<queries>` visibility, quick tile. Triggers on VendorKeepAlive/KeepAliveNavigator/PermissionCenter/tile/ROM-path tasks; not for engine, UI theme or network changes.
metadata:
  audience: android-device-contributors
  workflow: feature-branch
---

## 规范唯一事实源（此处不复述规范）

| 关注点 | 事实源 |
| --- | --- |
| 厂商入口表与降级跳转实现 | `client/app/src/main/java/com/qingqi/adskip/device/`（`VendorKeepAlive.kt`、`KeepAliveNavigator.kt`、`PermissionCenter.kt`、`BatteryExemption.kt`、`AccessibilityStatus.kt`） |
| 厂商标签与 UI 映射 | `ui/VendorLabels.kt` |
| 快捷磁贴 | `device/SkipTileService.kt`、`device/QuickTileLogic.kt` |
| `<queries>` 可见性声明与 manifest 契约 | `AndroidManifest.xml`；`ManifestContractTest` |
| 目录落点 | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 2.2 节 |

> 本文件**刻意不复制**厂商清单与 ROM 设置页路径字典：以 `VendorKeepAlive.kt` 入口表为准，文档里的路径会随 ROM 版本静默失效。

## 新增/修改厂商条目的自检

1. `VendorKeepAlive` 入口表新增厂商 → 同步 `VendorDetectionContractTest` 夹具（双端/双表一致性）与 `VendorKeepAliveTest` 用例；
2. 涉及包可见性的目标 Activity → 更新 `AndroidManifest.xml` 的 `<queries>` 声明，`ManifestContractTest` 必须覆盖；
3. `KeepAliveNavigator` 遵循**逐级降级**：精确设置页 → 厂商应用信息页 → 系统设置首页；每级失败都要有下一级，不得以异常终止引导；
4. ROM 设置页路径变更须在测试用例中注明实测的 ROM 与 Android 版本（如 MIUI 14 / Android 13），未实测的路径标注 TODO 而非猜测值。

## 硬护栏

1. **只引导，不代管**：权限页只能跳转引导用户手动开启；不得伪造/缓存权限状态——无障碍真实状态以 `AccessibilityStatus` 查询为准（契约由 `AccessibilityStatusContractTest` 固化）；
2. 磁贴状态由 `QuickTileLogic` 纯函数决定，不在 `SkipTileService` 里散落判断——新状态先补 `QuickTileLogicTest`；
3. 厂商文案进 `VendorLabels.kt`，不在屏幕内写死厂商名。

## 提交前自检

- `cd client && ./gradlew testDebugUnitTest --tests "*Vendor*" --tests "*ManifestContract*" --tests "*QuickTile*" --tests "*AccessibilityStatus*"` 全绿；
- 门禁命令全集见 [AGENTS.md](../../AGENTS.md)「验证与合并」。
