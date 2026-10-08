package com.ldp.adskip.device

import android.content.Context
import android.provider.Settings
import com.ldp.adskip.service.FloatingToggleService
import com.ldp.adskip.service.PointPickService

/**
 * 悬浮窗快捷开关的 UI 门面。
 *
 * `ui/` 被禁止 import `service/`（架构边界契约），而悬浮开关的启停入口在
 * 设置页；`device/` 允许依赖 `service/`（先例：[com.ldp.adskip.device.SkipTileService]
 * 调用 SkipAdService），故把门面放在这里，UI 只面向本对象。
 */
object OverlayToggle {

    /** 是否已授予「显示在其他应用上层」权限 */
    fun hasPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun start(context: Context) = FloatingToggleService.start(context)

    fun stop(context: Context) = FloatingToggleService.stop(context)

    /** 悬浮开关当前是否在显示（服务存活即显示） */
    fun isActive(): Boolean = FloatingToggleService.running

    /** 进入取点模式（在目标广告界面上手动标注「跳过」位置） */
    fun startPointPick(context: Context) = PointPickService.start(context)

    /** 取点模式是否在显示 */
    fun isPointPickActive(): Boolean = PointPickService.running
}
