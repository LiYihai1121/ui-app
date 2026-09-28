package com.ldp.adskip.device

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.ldp.adskip.R
import com.ldp.adskip.service.SkipAdService

/**
 * 下拉通知栏快捷磁贴：一眼看服务状态，一次点击完成「关闭」或「去开启」。
 *
 * 交互契约（纯逻辑见 [QuickTileLogic]，可 JVM 单测）：
 * - 服务运行中 → 点击即关闭（经 [SkipAdService.requestShutdown] 走 `disableSelf()`，与用户在设置里关开关等价）；
 * - 服务未运行 → 点击拉起系统无障碍设置。
 *
 * 实现要点：
 * - 状态以 [SkipAdService.isEnabled]（系统已启用列表）为准，而非进程内快照——
 *   磁贴可能在应用进程刚被拉起、Service 尚未连接时就被点击；
 * - 关闭是异步的（`disableSelf()` → `onDestroy`），故先乐观置为「已停止」再延时复查；
 * - Android 14 起 `startActivityAndCollapse(Intent)` 已废弃，改用 `PendingIntent` 重载。
 */
class SkipTileService : TileService() {

    private val resyncHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        render()
    }

    override fun onClick() {
        super.onClick()
        when (QuickTileLogic.decide(SkipAdService.isEnabled(this))) {
            QuickTileAction.DISABLE_SERVICE -> requestDisable()
            QuickTileAction.OPEN_ACCESSIBILITY_SETTINGS -> openAccessibilitySettings()
        }
    }

    override fun onDestroy() {
        resyncHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestDisable() {
        if (!SkipAdService.requestShutdown(this)) {
            // 服务未在运行态（可能刚被系统回收），退化为引导用户手动处理
            openAccessibilitySettings()
            return
        }
        Toast.makeText(this, R.string.tile_disabled, Toast.LENGTH_SHORT).show()
        render(enabledOverride = false)
        resyncHandler.removeCallbacksAndMessages(null)
        resyncHandler.postDelayed({ render() }, RESYNC_DELAY_MS)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    REQ_OPEN_SETTINGS,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** @param enabledOverride 乐观值；`null` 表示以系统真实状态渲染 */
    private fun render(enabledOverride: Boolean? = null) {
        val tile = qsTile ?: return
        val model = QuickTileLogic.model(enabledOverride ?: SkipAdService.isEnabled(this))
        tile.state = if (model.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (model.enabled) R.string.tile_state_on else R.string.tile_state_off,
            )
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_skip)
        tile.updateTile()
    }

    private companion object {
        /** 关闭请求发出后复查系统状态的延迟（`disableSelf()` 是异步的）。 */
        const val RESYNC_DELAY_MS = 600L
        const val REQ_OPEN_SETTINGS = 0
    }
}
