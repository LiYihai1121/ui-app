package com.qingqi.adskip.device

/**
 * 快捷磁贴的纯逻辑模型 —— **零 Android 依赖**，可在纯 JVM 单测中断言。
 *
 * 为什么要有这层：`TileService` 的点击行为涉及 `AccessibilityService.disableSelf()` 与
 * 系统设置跳转（无法在 JVM 中执行），但「服务已开启时点磁贴应关闭、未开启时应引导开启」
 * 这条交互契约必须可测，故抽为纯函数；[SkipTileService] 只负责把它落到 Android API 上。
 */
enum class QuickTileAction {
    /** 服务未运行：拉起系统无障碍设置，引导用户开启。 */
    OPEN_ACCESSIBILITY_SETTINGS,

    /** 服务运行中：请求服务自行关闭（等价于用户去设置里关掉开关）。 */
    DISABLE_SERVICE,
}

/** 磁贴的完整呈现模型：[enabled] 为 true 时磁贴高亮。 */
data class QuickTileModel(val enabled: Boolean, val action: QuickTileAction)

/**
 * 磁贴「添加请求」的结果分类。
 *
 * 框架侧返回码由 [KeepAliveNavigator.toTileAddResult] 归一化到这里，
 * 便于 UI 层只依赖语义枚举而不直接耦合 [android.app.StatusBarManager]。
 */
enum class TileAddResult {
    /** 已加入快捷设置。 */
    ADDED,

    /** 此前已加入。 */
    ALREADY_ADDED,

    /** 应用不在前台，系统拒绝弹出添加请求。 */
    NOT_FOREGROUND,

    /** 其他失败（无状态栏服务、组件不匹配等）。 */
    FAILED,
}

object QuickTileLogic {

    /** 磁贴点击后的动作决策：运行中 → 关闭；未运行 → 去开启。 */
    fun decide(serviceEnabled: Boolean): QuickTileAction = if (serviceEnabled) {
        QuickTileAction.DISABLE_SERVICE
    } else {
        QuickTileAction.OPEN_ACCESSIBILITY_SETTINGS
    }

    /** 磁贴呈现模型（状态 + 点击动作保持同源推导，避免两处判空分叉）。 */
    fun model(serviceEnabled: Boolean): QuickTileModel =
        QuickTileModel(enabled = serviceEnabled, action = decide(serviceEnabled))
}
